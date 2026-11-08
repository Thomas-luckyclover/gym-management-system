package com.gms.dao;

import com.gms.constant.AppConstants;
import com.gms.dto.ClosedShiftHistoryItem;
import com.gms.dto.ShiftMetrics;
import com.gms.dto.ShiftTransactionItem;
import com.gms.entity.Shift;
import com.gms.service.ShiftService;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy vấn dữ liệu Ca làm việc Lễ tân (bảng Shifts) và giao dịch trong ca (Màn #39 - UC-25).
 */
public class ShiftDAO {

    private static final String SHIFT_COLUMNS = "ShiftId, WorkDate, ShiftType, OpenedByEmployeeId, "
            + "ClosedByEmployeeId, Status, OpenedAt, ClosedAt, ExpectedCash, CountedCash, VarianceReason";

    // Đoạn FROM...WHERE dùng chung cho đếm và lấy danh sách giao dịch trong ca
    private static final String TRANSACTION_FROM_SQL = """
            FROM Orders o
            JOIN Customers c ON c.CustomerId = o.HolderCustomerId
            JOIN Persons p ON p.PersonId = c.PersonId
            LEFT JOIN Transactions t ON t.OrderId = o.OrderId AND t.TransactionId = (
                SELECT MAX(t2.TransactionId) FROM Transactions t2 WHERE t2.OrderId = o.OrderId
            )
            WHERE o.ShiftId = ?
              AND (? IS NULL OR o.OrderCode LIKE ? OR p.FullName COLLATE Vietnamese_CI_AI LIKE ?)
              AND (? IS NULL OR COALESCE(t.PaymentMethod, CASE WHEN o.Status = 'Pending' THEN 'Transfer' ELSE 'Cash' END) = ?)
              AND (? IS NULL OR o.Status = ?)
            """;

    // ===== 1. Khởi tạo & Tìm kiếm Ca làm việc =====

    /**
     * Đảm bảo ngày mở cửa luôn có đủ 2 ca Morning và Afternoon (CBR-104).
     */
    public void ensureShiftsForDate(LocalDate date) throws SQLException {
        // CBR-104: Nếu hôm nay phòng tập đóng cửa nghỉ cả ngày thì thoát luôn, không tạo ca
        if (isGymClosedOnDate(date)) {
            return;
        }

        String selectSql = "SELECT ShiftType FROM Shifts WHERE WorkDate = ?";
        boolean hasMorning = false;
        boolean hasAfternoon = false;

        try (Connection conn = DBContext.getConnection()) {
            // B1: Kiểm tra trong DB ngày hôm nay đã có ca Sáng / ca Chiều chưa (tránh INSERT trùng khi F5 trang)
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setDate(1, Date.valueOf(date));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("ShiftType");
                        if ("Morning".equalsIgnoreCase(type)) {
                            hasMorning = true;
                        } else if ("Afternoon".equalsIgnoreCase(type)) {
                            hasAfternoon = true;
                        }
                    }
                }
            }

            // B2: Chèn bù ca Sáng / ca Chiều nếu chưa có (trạng thái NotOpened)
            if (!hasMorning) {
                insertDefaultShift(conn, date, "Morning");
            }
            if (!hasAfternoon) {
                insertDefaultShift(conn, date, "Afternoon");
            }
        }
    }

    private void insertDefaultShift(Connection conn, LocalDate date, String shiftType) throws SQLException {
        String sql = "INSERT INTO Shifts (WorkDate, ShiftType, Status) VALUES (?, ?, 'NotOpened')";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(date));
            stmt.setString(2, shiftType);
            stmt.executeUpdate();
        }
    }

    /**
     * Lấy 2 ca trong ngày, xếp ca Sáng trước ca Chiều.
     */
    public List<Shift> findTodayShifts(LocalDate date) throws SQLException {
        String sql = "SELECT " + SHIFT_COLUMNS + " FROM Shifts "
                + "WHERE WorkDate = ? "
                + "ORDER BY CASE WHEN ShiftType = 'Morning' THEN 1 ELSE 2 END";

        List<Shift> shifts = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    shifts.add(mapResultSetToShift(rs));
                }
            }
        }
        return shifts;
    }

    /**
     * Tìm thông tin ca theo mã ShiftId.
     */
    public Shift findById(int shiftId) throws SQLException {
        String sql = "SELECT " + SHIFT_COLUMNS + " FROM Shifts WHERE ShiftId = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, shiftId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToShift(rs);
                }
            }
        }
        return null;
    }

    /**
     * Kiểm tra phòng tập có đóng cửa nghỉ cả ngày theo OperatingHourExceptions không (CBR-104).
     */
    public boolean isGymClosedOnDate(LocalDate date) throws SQLException {
        String sql = "SELECT 1 FROM OperatingHourExceptions WHERE ApplyDate = ? AND IsClosedAllDay = 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Tìm ca làm việc của ngày trước bị bỏ quên chưa chốt sổ (Status = 'Open').
     */
    public Shift findUnclosedPastShift(LocalDate today) throws SQLException {
        return findFirstOpenShift("WorkDate < ?", Date.valueOf(today));
    }

    /**
     * Tìm bất kỳ ca nào khác đang ở trạng thái Open trên toàn hệ thống (trừ exceptShiftId).
     */
    public Shift findOtherOpenShift(int exceptShiftId) throws SQLException {
        return findFirstOpenShift("ShiftId <> ?", exceptShiftId);
    }

    private Shift findFirstOpenShift(String extraCondition, Object param) {
        String sql = "SELECT TOP 1 " + SHIFT_COLUMNS + " FROM Shifts "
                + "WHERE Status = 'Open' AND " + extraCondition
                + " ORDER BY WorkDate ASC, CASE WHEN ShiftType = 'Morning' THEN 1 ELSE 2 END";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                return null;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, param);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToShift(rs);
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * @deprecated Business logic chọn ca đã được chuyển sang {@link ShiftService#findCurrentOrActiveShift(LocalDate)}.
     * Giữ lại phương thức này để các module khác (CounterSaleService, PtSaleService) tiếp tục hoạt động mà không cần sửa file ngoài ca làm việc.
     */
    public Shift findCurrentOrActiveShift(LocalDate today) throws SQLException {
        return new ShiftService(this, null, null, null, null).findCurrentOrActiveShift(today);
    }

    /**
     * Lấy họ tên nhân viên theo EmployeeId.
     */
    public String findEmployeeFullName(Integer employeeId) throws SQLException {
        if (employeeId == null || employeeId <= 0) {
            return null;
        }
        String sql = "SELECT p.FullName FROM Employees e "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                + "WHERE e.EmployeeId = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("FullName");
                }
            }
        }
        return null;
    }

    /**
     * Lấy CustomerId đứng tên đơn hàng (để chặn Lễ tân tự hủy đơn của chính mình - CBR-115).
     */
    public Integer findOrderHolderCustomerId(int orderId) throws SQLException {
        String sql = "SELECT HolderCustomerId FROM Orders WHERE OrderId = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("HolderCustomerId");
                }
            }
        }
        return null;
    }

    /**
     * Tìm ca làm việc gắn với đơn bán tại quầy (CBR-11, CBR-77).
     */
    public Shift findShiftOfCounterOrder(int orderId) throws SQLException {
        String sql = "SELECT ShiftId FROM Orders WHERE OrderId = ? AND Channel = 'Counter'";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int shiftId = rs.getInt("ShiftId");
                    if (!rs.wasNull()) {
                        return findById(shiftId);
                    }
                }
            }
        }
        return null;
    }

    // ===== 2. Thao tác Mở ca & Chốt ca =====

    /**
     * Mở ca làm việc (NotOpened -> Open).
     */
    public int openShift(Connection conn, int shiftId, int employeeId, LocalDateTime openedAt) throws SQLException {
        String sql = "UPDATE Shifts "
                + "SET Status = 'Open', OpenedByEmployeeId = ?, OpenedAt = ? "
                + "WHERE ShiftId = ? AND Status = 'NotOpened'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            stmt.setTimestamp(2, Timestamp.valueOf(openedAt));
            stmt.setInt(3, shiftId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Chốt ca làm việc và lưu số liệu đối soát tiền mặt (Open -> Closed).
     */
    public int closeShift(Connection conn, int shiftId, int employeeId, LocalDateTime closedAt,
                          long expectedCash, long countedCash, String varianceReason) throws SQLException {
        String sql = "UPDATE Shifts "
                + "SET Status = 'Closed', ClosedByEmployeeId = ?, ClosedAt = ?, "
                + "    ExpectedCash = ?, CountedCash = ?, VarianceReason = ? "
                + "WHERE ShiftId = ? AND Status = 'Open'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            stmt.setTimestamp(2, Timestamp.valueOf(closedAt));
            stmt.setLong(3, expectedCash);
            stmt.setLong(4, countedCash);
            stmt.setString(5, varianceReason);
            stmt.setInt(6, shiftId);
            return stmt.executeUpdate();
        }
    }

    // ===== 3. Thống kê KPI & Danh sách giao dịch =====

    /**
     * Tính 4 thẻ KPI của ca làm việc trong 1 lần truy vấn (DBR-55).
     */
    public ShiftMetrics calculateShiftMetrics(int shiftId) throws SQLException {
        String sql = """
                SELECT 
                    COALESCE(SUM(CASE WHEN t.PaymentMethod = 'Cash' AND t.Status = 'Success' AND o.Status = 'Paid' THEN t.Amount ELSE 0 END), 0) AS CashTotal,
                    COUNT(DISTINCT CASE WHEN t.PaymentMethod = 'Cash' AND t.Status = 'Success' AND o.Status = 'Paid' THEN o.OrderId END) AS CashCount,
                    COALESCE(SUM(CASE WHEN t.PaymentMethod = 'Transfer' AND t.Status = 'Success' AND o.Status = 'Paid' THEN t.Amount ELSE 0 END), 0) AS BankTotal,
                    COUNT(DISTINCT CASE WHEN t.PaymentMethod = 'Transfer' AND t.Status = 'Success' AND o.Status = 'Paid' THEN o.OrderId END) AS BankCount,
                    COUNT(DISTINCT CASE WHEN o.Status = 'Pending' THEN o.OrderId END) AS PendingCount
                FROM Orders o
                LEFT JOIN Transactions t ON t.OrderId = o.OrderId
                WHERE o.ShiftId = ?
                """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, shiftId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long cashTotal = rs.getLong("CashTotal");
                    int cashCount = rs.getInt("CashCount");
                    long bankTotal = rs.getLong("BankTotal");
                    int bankCount = rs.getInt("BankCount");
                    int pendingCount = rs.getInt("PendingCount");

                    return new ShiftMetrics(cashTotal, cashCount, bankTotal, bankCount,
                            cashTotal + bankTotal, cashCount + bankCount, pendingCount);
                }
            }
        }
        return new ShiftMetrics(0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Đếm tổng số giao dịch trong ca theo bộ lọc để phân trang.
     */
    public int countTransactions(int shiftId, String search, String method, String status) throws SQLException {
        String sql = "SELECT COUNT(o.OrderId) " + TRANSACTION_FROM_SQL;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setTransactionFilterParams(stmt, shiftId, search, method, status);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Lấy danh sách giao dịch trong ca có phân trang và áp bộ lọc tìm kiếm.
     */
    public List<ShiftTransactionItem> findTransactionsPage(int shiftId, String search, String method,
                                                          String status, int offset, int pageSize) throws SQLException {
        String sql = """
                SELECT 
                    o.OrderId,
                    o.OrderCode,
                    p.FullName AS CustomerName,
                    p.Phone AS CustomerPhone,
                    o.PackageNameSnapshot,
                    o.CreatedAt,
                    COALESCE(t.PaymentMethod, CASE WHEN o.Status = 'Pending' THEN 'Transfer' ELSE 'Cash' END) AS PaymentMethod,
                    (o.UnitPriceSnapshot - o.DiscountAmount) AS Amount,
                    o.Status AS OrderStatus,
                    o.CancelType,
                    o.CancelReason,
                    o.CancelledAt
                """
                + TRANSACTION_FROM_SQL
                + " ORDER BY o.CreatedAt DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<ShiftTransactionItem> items = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setTransactionFilterParams(stmt, shiftId, search, method, status);
            stmt.setInt(9, offset);
            stmt.setInt(10, pageSize);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp createdAtTs = rs.getTimestamp("CreatedAt");
                    Timestamp cancelledAtTs = rs.getTimestamp("CancelledAt");

                    items.add(new ShiftTransactionItem(
                            rs.getInt("OrderId"),
                            rs.getString("OrderCode"),
                            rs.getString("CustomerName"),
                            rs.getString("CustomerPhone"),
                            rs.getString("PackageNameSnapshot"),
                            createdAtTs != null ? createdAtTs.toLocalDateTime() : null,
                            rs.getString("PaymentMethod"),
                            rs.getLong("Amount"),
                            rs.getString("OrderStatus"),
                            rs.getString("CancelType"),
                            rs.getString("CancelReason"),
                            cancelledAtTs != null ? cancelledAtTs.toLocalDateTime() : null,
                            false,
                            false));
                }
            }
        }
        return items;
    }

    private void setTransactionFilterParams(PreparedStatement stmt, int shiftId,
                                            String search, String method, String status) throws SQLException {
        String keyword = (search != null && !search.trim().isEmpty()) ? "%" + search.trim() + "%" : null;
        String dbMethod = (method == null || "all".equalsIgnoreCase(method))
                ? null
                : ("cash".equalsIgnoreCase(method) ? "Cash" : "Transfer");
        String dbStatus = null;
        if ("paid".equalsIgnoreCase(status)) {
            dbStatus = "Paid";
        } else if ("pending".equalsIgnoreCase(status)) {
            dbStatus = "Pending";
        } else if ("cancelled".equalsIgnoreCase(status)) {
            dbStatus = "Cancelled";
        }

        stmt.setInt(1, shiftId);
        stmt.setString(2, keyword);
        stmt.setString(3, keyword);
        stmt.setString(4, keyword);
        stmt.setString(5, dbMethod);
        stmt.setString(6, dbMethod);
        stmt.setString(7, dbStatus);
        stmt.setString(8, dbStatus);
    }

    /**
     * Lấy danh sách đơn đang Pending trong ca để hiện cảnh báo và chặn chốt ca (CBR-78).
     */
    public List<ShiftTransactionItem> findPendingOrdersInShift(int shiftId) throws SQLException {
        String sql = """
                SELECT o.OrderId, o.OrderCode, p.FullName AS CustomerName
                FROM Orders o
                JOIN Customers c ON c.CustomerId = o.HolderCustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                WHERE o.ShiftId = ? AND o.Status = 'Pending'
                ORDER BY o.CreatedAt ASC
                """;

        List<ShiftTransactionItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, shiftId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new ShiftTransactionItem(
                            rs.getInt("OrderId"),
                            rs.getString("OrderCode"),
                            rs.getString("CustomerName"),
                            null, null, null, "Transfer", 0L, "Pending",
                            null, null, null, true, true));
                }
            }
        }
        return list;
    }

    // ===== 4. Xác nhận Chuyển khoản & Hủy đơn hàng tại quầy =====

    /**
     * Lễ tân xác nhận đã nhận tiền chuyển khoản cho đơn hàng tại quầy (CBR-77).
     */
    public int confirmBankTransfer(Connection conn, int orderId, int employeeId, LocalDateTime completedAt) throws SQLException {
        // B1: Cập nhật giao dịch chuyển khoản sang Success
        String updateTxSql = "UPDATE Transactions "
                + "SET Status = 'Success', ReceivedByEmployeeId = ?, CompletedAt = ? "
                + "WHERE OrderId = ? AND Status = 'Pending'";
        int txUpdated;
        try (PreparedStatement txStmt = conn.prepareStatement(updateTxSql)) {
            txStmt.setInt(1, employeeId);
            txStmt.setTimestamp(2, Timestamp.valueOf(completedAt));
            txStmt.setInt(3, orderId);
            txUpdated = txStmt.executeUpdate();
        }

        // B1.1: Nếu đơn chưa có dòng Transaction (VD đơn giữ chỗ PT) thì tạo mới giao dịch Success
        if (txUpdated == 0) {
            String insertTxSql = """
                    INSERT INTO Transactions (OrderId, Amount, PaymentMethod, ReceivedByEmployeeId, Status, CreatedAt, CompletedAt)
                    SELECT OrderId, (UnitPriceSnapshot - DiscountAmount), 'Transfer', ?, 'Success', ?, ?
                    FROM Orders WHERE OrderId = ?
                    """;
            try (PreparedStatement insertTxStmt = conn.prepareStatement(insertTxSql)) {
                insertTxStmt.setInt(1, employeeId);
                insertTxStmt.setTimestamp(2, Timestamp.valueOf(completedAt));
                insertTxStmt.setTimestamp(3, Timestamp.valueOf(completedAt));
                insertTxStmt.setInt(4, orderId);
                insertTxStmt.executeUpdate();
            }
        }

        // B2: Chuyển trạng thái đơn hàng sang Paid
        String updateOrderSql = "UPDATE Orders SET Status = 'Paid', PaidAt = ? WHERE OrderId = ? AND Status = 'Pending'";
        try (PreparedStatement orderStmt = conn.prepareStatement(updateOrderSql)) {
            orderStmt.setTimestamp(1, Timestamp.valueOf(completedAt));
            orderStmt.setInt(2, orderId);
            return orderStmt.executeUpdate();
        }
    }

    /**
     * Hủy đơn hàng tại quầy trong ca mở (CBR-11) và thu hồi các quyền lợi liên quan.
     */
    public int cancelCounterOrder(Connection conn, int orderId, String reason, LocalDateTime now) throws SQLException {
        // B1: Đọc VoucherId của đơn để hoàn lại lượt dùng nếu có (BR-44)
        Integer voucherId = null;
        try (PreparedStatement readStmt = conn.prepareStatement("SELECT VoucherId FROM Orders WHERE OrderId = ?")) {
            readStmt.setInt(1, orderId);
            try (ResultSet rs = readStmt.executeQuery()) {
                if (rs.next()) {
                    int vId = rs.getInt("VoucherId");
                    if (!rs.wasNull()) {
                        voucherId = vId;
                    }
                }
            }
        }

        // B2: Đổi trạng thái Order sang Cancelled
        String updateOrderSql = "UPDATE Orders "
                + "SET Status = 'Cancelled', CancelType = 'CancelledByReceptionist', CancelledAt = ?, CancelReason = ? "
                + "WHERE OrderId = ? AND Status IN ('Pending', 'Paid')";
        int rowsUpdated;
        try (PreparedStatement orderStmt = conn.prepareStatement(updateOrderSql)) {
            orderStmt.setTimestamp(1, Timestamp.valueOf(now));
            orderStmt.setString(2, reason);
            orderStmt.setInt(3, orderId);
            rowsUpdated = orderStmt.executeUpdate();
        }
        if (rowsUpdated == 0) {
            return 0;
        }

        // B3: Hoàn lại 1 lượt sử dụng voucher nếu đơn có áp mã (BR-44)
        if (voucherId != null) {
            try (PreparedStatement vStmt = conn.prepareStatement(
                    "UPDATE Vouchers SET UsedCount = UsedCount - 1 WHERE VoucherId = ? AND UsedCount > 0")) {
                vStmt.setInt(1, voucherId);
                vStmt.executeUpdate();
            }
        }

        // B4: Hủy gói tập Subscriptions vừa sinh từ đơn (DBR-40)
        try (PreparedStatement subStmt = conn.prepareStatement(
                "UPDATE Subscriptions SET Status = 'Cancelled' WHERE OrderId = ?")) {
            subStmt.setInt(1, orderId);
            subStmt.executeUpdate();
        }

        // B5: Hủy các buổi tập PT chưa diễn ra
        String cancelSessionsSql = """
                UPDATE Sessions 
                SET Status = 'Cancelled', CancelType = 'ByReceptionist', CancelReason = ?
                WHERE SubscriptionId IN (SELECT SubscriptionId FROM Subscriptions WHERE OrderId = ?)
                  AND Status = 'Scheduled'
                """;
        try (PreparedStatement sesStmt = conn.prepareStatement(cancelSessionsSql)) {
            sesStmt.setString(1, reason);
            sesStmt.setInt(2, orderId);
            sesStmt.executeUpdate();
        }

        // B6: Giải phóng khung giờ PT về Free (DBR-36)
        String freeSlotSql = """
                UPDATE TimeSlots 
                SET Status = 'Free' 
                WHERE TimeSlotId IN (
                    SELECT s.TimeSlotId FROM Sessions s
                    JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                    WHERE sub.OrderId = ?
                ) AND Status = 'Booked'
                """;
        try (PreparedStatement slotStmt = conn.prepareStatement(freeSlotSql)) {
            slotStmt.setInt(1, orderId);
            slotStmt.executeUpdate();
        }

        return rowsUpdated;
    }

    // ===== 5. Lịch sử chốt ca cho màn Quản lý (Màn #41 - UC-26) =====

    /**
     * Đếm tổng số ca đã chốt theo khoảng ngày lọc.
     */
    public int countClosedShiftsHistory(LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT COUNT(s.ShiftId)
                FROM Shifts s
                WHERE s.Status = 'Closed'
                  AND (? IS NULL OR s.WorkDate >= ?)
                  AND (? IS NULL OR s.WorkDate <= ?)
                """;

        Date fromSql = fromDate != null ? Date.valueOf(fromDate) : null;
        Date toSql = toDate != null ? Date.valueOf(toDate) : null;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, fromSql);
            stmt.setDate(2, fromSql);
            stmt.setDate(3, toSql);
            stmt.setDate(4, toSql);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Lấy danh sách ca đã chốt có phân trang cho Tab Lịch sử chốt ca của Quản lý.
     */
    public List<ClosedShiftHistoryItem> findClosedShiftsHistoryPage(LocalDate fromDate, LocalDate toDate,
                                                                   int offset, int pageSize) throws SQLException {
        String sql = """
                SELECT 
                    s.ShiftId, s.WorkDate, s.ShiftType,
                    pOpen.FullName AS OpenedByName,
                    pClose.FullName AS ClosedByName,
                    s.OpenedAt, s.ClosedAt,
                    s.ExpectedCash, s.CountedCash, s.VarianceReason,
                    COALESCE((
                        SELECT SUM(t.Amount)
                        FROM Orders o
                        JOIN Transactions t ON t.OrderId = o.OrderId
                        WHERE o.ShiftId = s.ShiftId 
                          AND t.PaymentMethod = 'Transfer' 
                          AND t.Status = 'Success'
                          AND o.Status <> 'Cancelled'
                    ), 0) AS TotalBankTransfer,
                    (
                        SELECT COUNT(o.OrderId)
                        FROM Orders o
                        WHERE o.ShiftId = s.ShiftId
                    ) AS TotalInvoiceCount
                FROM Shifts s
                LEFT JOIN Employees eOpen ON eOpen.EmployeeId = s.OpenedByEmployeeId
                LEFT JOIN Persons pOpen ON pOpen.PersonId = eOpen.PersonId
                LEFT JOIN Employees eClose ON eClose.EmployeeId = s.ClosedByEmployeeId
                LEFT JOIN Persons pClose ON pClose.PersonId = eClose.PersonId
                WHERE s.Status = 'Closed'
                  AND (? IS NULL OR s.WorkDate >= ?)
                  AND (? IS NULL OR s.WorkDate <= ?)
                ORDER BY s.WorkDate DESC, s.ShiftId DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;

        Date fromSql = fromDate != null ? Date.valueOf(fromDate) : null;
        Date toSql = toDate != null ? Date.valueOf(toDate) : null;

        List<ClosedShiftHistoryItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, fromSql);
            stmt.setDate(2, fromSql);
            stmt.setDate(3, toSql);
            stmt.setDate(4, toSql);
            stmt.setInt(5, offset);
            stmt.setInt(6, pageSize);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long expectedCash = rs.getLong("ExpectedCash");
                    long countedCash = rs.getLong("CountedCash");
                    Date workDateSql = rs.getDate("WorkDate");
                    Timestamp openedTs = rs.getTimestamp("OpenedAt");
                    Timestamp closedTs = rs.getTimestamp("ClosedAt");

                    list.add(new ClosedShiftHistoryItem(
                            rs.getInt("ShiftId"),
                            workDateSql != null ? workDateSql.toLocalDate() : null,
                            rs.getString("ShiftType"),
                            rs.getString("OpenedByName"),
                            rs.getString("ClosedByName"),
                            openedTs != null ? openedTs.toLocalDateTime() : null,
                            closedTs != null ? closedTs.toLocalDateTime() : null,
                            expectedCash,
                            countedCash,
                            countedCash - expectedCash,
                            rs.getString("VarianceReason"),
                            rs.getLong("TotalBankTransfer"),
                            rs.getInt("TotalInvoiceCount")
                    ));
                }
            }
        }
        return list;
    }

    // ===== Hàm hỗ trợ map ResultSet =====

    private Shift mapResultSetToShift(ResultSet rs) throws SQLException {
        Date workDateSql = rs.getDate("WorkDate");
        int openedBy = rs.getInt("OpenedByEmployeeId");
        Integer openedByEmployeeId = rs.wasNull() ? null : openedBy;
        int closedBy = rs.getInt("ClosedByEmployeeId");
        Integer closedByEmployeeId = rs.wasNull() ? null : closedBy;

        Timestamp openedAtTs = rs.getTimestamp("OpenedAt");
        Timestamp closedAtTs = rs.getTimestamp("ClosedAt");

        long expCash = rs.getLong("ExpectedCash");
        Long expectedCash = rs.wasNull() ? null : expCash;
        long cntCash = rs.getLong("CountedCash");
        Long countedCash = rs.wasNull() ? null : cntCash;

        return new Shift(
                rs.getInt("ShiftId"),
                workDateSql != null ? workDateSql.toLocalDate() : null,
                Shift.ShiftType.fromDbValue(rs.getString("ShiftType")),
                openedByEmployeeId,
                closedByEmployeeId,
                Shift.Status.fromDbValue(rs.getString("Status")),
                openedAtTs != null ? openedAtTs.toLocalDateTime() : null,
                closedAtTs != null ? closedAtTs.toLocalDateTime() : null,
                expectedCash,
                countedCash,
                rs.getString("VarianceReason")
        );
    }
}
