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
        return findFirstOpenShift("ShiftId <> ?", exceptShiftId);// ShiftId <> ? để tránh tìm chính ca hiện tại đang mở (tương đương dầu !=)
    }

    private Shift findFirstOpenShift(String extraCondition, Object param) {
        String sql = "SELECT TOP 1 " + SHIFT_COLUMNS + " FROM Shifts "
                + "WHERE Status = 'Open' AND " + extraCondition;
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
                //bỏ qua n dòng và lấy n dòng tiếp theo (phân trang SQL Server 2012+)

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
        // Tiền xử lý và chuẩn hóa các tham số lọc từ giao diện trước khi truyền vào SQL:
        // - keyword: cắt khoảng trắng và bọc %...% để tìm kiếm LIKE, rỗng thì để null
        // - dbMethod: chuyển về đúng giá trị 'Cash', 'Transfer' trong DB hoặc null nếu chọn Tất cả
        // - dbStatus: chuẩn hóa trạng thái về 'Paid', 'Pending', 'Cancelled' hoặc null nếu chọn Tất cả
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
                }//mục tiêu là hiển thị cảnh báo thì k cần phải lấy hết các trường dữ liệu, chỉ cần OrderId, OrderCode, CustomerName là đủ
            }
        }
        return list;
    }

    // ===== 4. Hủy đơn hàng tại quầy =====

    /**
     * Hủy 1 đơn tại quầy trong ca (CBR-11 đơn Paid, CBR-78 đơn Pending) — mọi bước trong transaction của Service.
     * Input: orderId, lý do (5–500), now, accountId = tài khoản Lễ tân bấm hủy. Output: 1 = đã hủy, 0 = đơn không còn hủy được.
     *
     * B1: đọc VoucherId + CreatedAt của đơn
     * B2: Orders → Cancelled / CancelledByReceptionist (chỉ khi đang Pending hoặc Paid)
     * B3: hoàn 1 lượt voucher (BR-44)
     * B4: Subscriptions của đơn → Cancelled (DBR-40)
     * B5: khung của các buổi CÒN Scheduled → Free (DBR-36). Làm TRƯỚC B6 và chỉ lấy buổi Scheduled: khung của buổi
     *     đã dời / đã hủy trước đó có thể đang Booked cho khách khác — bản cũ trả cả những khung đó về Free.
     * B6: buổi Scheduled → Cancelled / ByCustomer (DBR-37 không có loại "lễ tân hủy"; khách đổi ý, tiền trả lại tại quầy),
     *     CancelledAt = now, CancelledByAccountId = lễ tân (CK_Sessions_CustomerCancel), CommissionAmount = 0 → PT không
     *     được tính hoa hồng cho buổi đã hoàn tiền. Bản cũ ghi 'ByReceptionist' + thiếu CancelledAt → vi phạm
     *     CK_Sessions_Cancel → mọi lần hủy đơn gói PT đã thu tiền đều lỗi "Hệ thống đang bận".
     * B7: đơn gói PT còn đang GIỮ LỊCH (Pending): khung do lần giữ tạo ra → xóa, khung PT đã công bố → Free
     *     (giống nút "Hủy giữ chỗ" — PtSaleDAO.releaseHold; DBR-36). Bản cũ bỏ quên → khung treo "Đang giữ" mãi.
     */
    public int cancelCounterOrder(Connection conn, int orderId, String reason, LocalDateTime now, int accountId)
            throws SQLException {
        // B1
        Integer voucherId = null;
        Timestamp orderCreatedAt = null;
        String readOrderSql = "SELECT VoucherId, CreatedAt FROM Orders WHERE OrderId = ?";
        try (PreparedStatement readStmt = conn.prepareStatement(readOrderSql)) {
            readStmt.setInt(1, orderId);
            try (ResultSet rs = readStmt.executeQuery()) {
                if (rs.next()) {
                    int vId = rs.getInt("VoucherId");
                    if (!rs.wasNull()) {
                        voucherId = vId;
                    }
                    orderCreatedAt = rs.getTimestamp("CreatedAt");
                }
            }
        }//lấy

        // B2 set status = Cancelled, CancelType = CancelledByReceptionist, CancelledAt = now, CancelReason = reason ở bảng Orders
        String updateOrderSql = """
                UPDATE Orders
                SET Status = 'Cancelled', CancelType = 'CancelledByReceptionist',
                    CancelledAt = ?, CancelReason = ?
                WHERE OrderId = ? AND Status IN ('Pending', 'Paid')
                """;
        int rowsUpdated;
        try (PreparedStatement orderStmt = conn.prepareStatement(updateOrderSql)) {
            orderStmt.setTimestamp(1, Timestamp.valueOf(now.withNano(0)));
            orderStmt.setString(2, reason);
            orderStmt.setInt(3, orderId);
            rowsUpdated = orderStmt.executeUpdate();
        }
        if (rowsUpdated == 0) {
            return 0;
        }

        // B3 trả lại lượt voucher (UsedCount - 1) nếu đơn có VoucherId (BR-44)
        if (voucherId != null) {
            try (PreparedStatement vStmt = conn.prepareStatement(
                    "UPDATE Vouchers SET UsedCount = UsedCount - 1 WHERE VoucherId = ? AND UsedCount > 0")) {
                vStmt.setInt(1, voucherId);
                vStmt.executeUpdate();
            }
        }

        // B4 hủy Subscriptions của đơn (Status = Cancelled)
        String cancelSubSql = "UPDATE Subscriptions SET Status = 'Cancelled' WHERE OrderId = ?";
        try (PreparedStatement subStmt = conn.prepareStatement(cancelSubSql)) {
            subStmt.setInt(1, orderId);
            subStmt.executeUpdate();
        }

        // B5 giải phóng các khung của buổi còn Scheduled → Free
        //Time slot là khung giờ của buổi PT
        String freeSlotSql = """
                UPDATE TimeSlots
                SET Status = 'Free'
                WHERE Status = 'Booked' AND TimeSlotId IN (
                    SELECT s.TimeSlotId FROM Sessions s
                    JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                    WHERE sub.OrderId = ? AND s.Status = 'Scheduled'
                )
                """;
        try (PreparedStatement slotStmt = conn.prepareStatement(freeSlotSql)) {
            slotStmt.setInt(1, orderId);
            slotStmt.executeUpdate();
        }

        // B6
        //Session là buổi PT đc tạo khi thanh toán thành công, 1 time slot chỉ gắn với 1 session theo thời điểm
        String cancelSessionsSql = """
                UPDATE Sessions
                SET Status = 'Cancelled', CancelType = 'ByCustomer', CancelReason = ?, CancelledAt = ?,
                    CancelledByAccountId = ?, RefundAgreed = 0, CommissionAmount = 0
                WHERE SubscriptionId IN (SELECT SubscriptionId FROM Subscriptions WHERE OrderId = ?)
                  AND Status = 'Scheduled'
                """;
        try (PreparedStatement sesStmt = conn.prepareStatement(cancelSessionsSql)) {
            sesStmt.setString(1, reason);
            sesStmt.setTimestamp(2, Timestamp.valueOf(now.withNano(0)));
            sesStmt.setInt(3, accountId);
            sesStmt.setInt(4, orderId);
            sesStmt.executeUpdate();
        }

        // B7: Giải phóng lịch giữ chỗ nếu đây là đơn gói PT ở trạng thái Pending
        // 7.1: Xóa các khung giờ TẠO MỚI riêng cho đơn này (CreatedAt >= thời điểm tạo đơn)
        // để tránh để lại lịch trống rác ngoài ý muốn của PT sau khi hủy đơn.
        if (orderCreatedAt != null) {
            String deleteHoldSlotsSql =
                    "DELETE FROM TimeSlots WHERE HeldByOrderId = ? AND Status = 'Held' AND CreatedAt >= ?";
            try (PreparedStatement statement = conn.prepareStatement(deleteHoldSlotsSql)) {
                statement.setInt(1, orderId);
                statement.setTimestamp(2, orderCreatedAt);
                statement.executeUpdate();
            }
        }
        String freeHoldSlotsSql = "UPDATE TimeSlots SET Status = 'Free', HeldByOrderId = NULL, HoldExpiresAt = NULL "
                + "WHERE HeldByOrderId = ? AND Status = 'Held'";
        try (PreparedStatement statement = conn.prepareStatement(freeHoldSlotsSql)) {
            statement.setInt(1, orderId);
            statement.executeUpdate();
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
                JOIN Employees eOpen ON eOpen.EmployeeId = s.OpenedByEmployeeId
                JOIN Persons pOpen ON pOpen.PersonId = eOpen.PersonId
                JOIN Employees eClose ON eClose.EmployeeId = s.ClosedByEmployeeId
                JOIN Persons pClose ON pClose.PersonId = eClose.PersonId
                WHERE s.Status = 'Closed'
                  AND (? IS NULL OR s.WorkDate >= ?)
                  AND (? IS NULL OR s.WorkDate <= ?)
                ORDER BY s.WorkDate DESC, s.ShiftId DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
                //Coalesce: nếu null thì trả về 0, tránh lỗi NullPointerException khi tính tổng tiền chuyển khoản

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

