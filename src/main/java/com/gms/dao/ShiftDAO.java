package com.gms.dao;

import com.gms.dto.ShiftMetrics;
import com.gms.dto.ShiftTransactionItem;
import com.gms.entity.Shift;
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
 * Data Access Object quản lý dữ liệu Ca làm việc của Lễ tân (Bảng Shifts)
 * và các giao dịch, thống kê doanh thu liên quan trong ca (Màn #39 - UC-25).
 *
 * <p>Quy tắc kỹ thuật tuân thủ:</p>
 * <ul>
 *   <li>CBR-104: Mỗi ngày mở cửa có đúng 2 ca (Morning, Afternoon) tự động sinh.</li>
 *   <li>CBR-105: Mọi hóa đơn quầy lưu ShiftId của ca đang mở.</li>
 *   <li>DBR-55: ExpectedCash = Tổng tiền các giao dịch tiền mặt thành công (Cash & Success) của các đơn chưa hủy.</li>
 *   <li>CBR-78: Shift không thể chốt nếu còn đơn Pending.</li>
 *   <li>BR-46: Ghi nhận CountedCash và VarianceReason khi chốt ca.</li>
 * </ul>
 *
 * @author Antigravity
 */
public class ShiftDAO {

    // Danh sách các cột của bảng Shifts dùng cho câu SELECT
    private static final String SHIFT_COLUMNS = "ShiftId, WorkDate, ShiftType, OpenedByEmployeeId, "
            + "ClosedByEmployeeId, Status, OpenedAt, ClosedAt, ExpectedCash, CountedCash, VarianceReason";

    // ===== 1. Khởi tạo & Tìm kiếm Ca làm việc =====

    /**
     * Đảm bảo ngày làm việc luôn có đủ 2 ca: Morning và Afternoon (CBR-104).
     * Nếu chưa tồn tại trong cơ sở dữ liệu, hàm sẽ tự động chèn mới với trạng thái NotOpened.
     *
     * @param conn Connection tham gia transaction
     * @param date ngày làm việc cần kiểm tra
     * @throws SQLException khi truy vấn lỗi
     */
    public void ensureShiftsForDate(Connection conn, LocalDate date) throws SQLException {
        String selectSql = "SELECT ShiftType FROM Shifts WHERE WorkDate = ?";
        boolean hasMorning = false;
        boolean hasAfternoon = false;

        // B1: Kiểm tra xem các ca nào đã có
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

        // B2: Chèn ca sáng nếu thiếu
        String insertSql = "INSERT INTO Shifts (WorkDate, ShiftType, Status) VALUES (?, ?, 'NotOpened')";
        if (!hasMorning) {
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setDate(1, Date.valueOf(date));
                insertStmt.setString(2, "Morning");
                insertStmt.executeUpdate();
            }
        }

        // B3: Chèn ca chiều nếu thiếu
        if (!hasAfternoon) {
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setDate(1, Date.valueOf(date));
                insertStmt.setString(2, "Afternoon");
                insertStmt.executeUpdate();
            }
        }
    }

    /**
     * Phiên bản tự mở kết nối riêng lẻ của ensureShiftsForDate.
     */
    public void ensureShiftsForDate(LocalDate date) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            ensureShiftsForDate(conn, date);
        }
    }

    /**
     * Lấy danh sách tất cả các ca trong một ngày cụ thể (thường gồm 2 ca: Sáng và Chiều).
     *
     * @param date ngày cần lấy ca
     * @return danh sách các ca được sắp xếp theo ca Sáng trước, ca Chiều sau
     * @throws SQLException khi truy vấn lỗi
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
     *
     * @param shiftId mã ca
     * @return đối tượng Shift hoặc null nếu không tìm thấy
     * @throws SQLException khi truy vấn lỗi
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
     * Tìm ca đang Open hiện tại của ngày hôm nay, hoặc nếu không có ca nào Open thì tìm ca phù hợp nhất.
     * (Trước 13:00 ưu tiên ca Sáng, từ 13:00 trở đi ưu tiên ca Chiều).
     *
     * @param today ngày hiện tại
     * @return đối tượng Shift phù hợp nhất
     * @throws SQLException khi truy vấn lỗi
     */
    public Shift findCurrentOrActiveShift(LocalDate today) throws SQLException {
        List<Shift> shifts = findTodayShifts(today);
        if (shifts.isEmpty()) {
            return null;
        }

        // B1: Nếu có ca đang Open thì đó chính là ca hiện tại
        for (Shift s : shifts) {
            if (s.isOpen()) {
                return s;
            }
        }

        // B2: Nếu không có ca nào Open, xét theo giờ hiện tại
        LocalDateTime now = LocalDateTime.now(DateTimeUtil.GYM_ZONE);
        boolean isAfternoonTime = now.getHour() >= 13;

        for (Shift s : shifts) {
            if (isAfternoonTime && s.getShiftType() == Shift.ShiftType.AFTERNOON) {
                return s;
            } else if (!isAfternoonTime && s.getShiftType() == Shift.ShiftType.MORNING) {
                return s;
            }
        }

        // B3: Fallback lấy phần tử đầu tiên
        return shifts.get(0);
    }

    /**
     * Lấy họ tên đầy đủ của nhân viên theo EmployeeId.
     *
     * @param employeeId mã nhân viên
     * @return họ tên nhân viên hoặc null nếu không tìm thấy
     * @throws SQLException khi truy vấn lỗi
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

    // ===== 2. Thao tác Mở ca & Chốt ca =====

    /**
     * Mở ca làm việc (Chuyển Status từ NotOpened sang Open).
     *
     * @param conn Connection tham gia transaction
     * @param shiftId mã ca cần mở
     * @param employeeId mã nhân viên thực hiện mở ca
     * @param openedAt thời điểm mở ca
     * @return số dòng bị ảnh hưởng (1 nếu thành công, 0 nếu ca đã bị người khác mở trước)
     * @throws SQLException khi truy vấn lỗi
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
     * Chốt ca làm việc và đối soát tiền mặt (Chuyển Status từ Open sang Closed).
     *
     * @param conn Connection tham gia transaction
     * @param shiftId mã ca cần chốt
     * @param employeeId mã nhân viên thực hiện chốt ca
     * @param closedAt thời điểm chốt ca
     * @param expectedCash số tiền mặt kỳ vọng tính từ hệ thống
     * @param countedCash số tiền mặt thực tế đếm được trong két
     * @param varianceReason lý do chênh lệch nếu có
     * @return số dòng bị ảnh hưởng (1 nếu thành công, 0 nếu ca đã chốt trước)
     * @throws SQLException khi truy vấn lỗi
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
     * Tính toán 4 chỉ số KPI trong ca: Tiền mặt, Chuyển khoản, Tổng thu và Đơn chờ xử lý (DBR-55).
     *
     * @param shiftId mã ca cần tính toán
     * @return đối tượng ShiftMetrics chứa các số liệu
     * @throws SQLException khi truy vấn lỗi
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
                    long totalRevenue = cashTotal + bankTotal;
                    int totalValidCount = cashCount + bankCount;
                    int pendingCount = rs.getInt("PendingCount");

                    return new ShiftMetrics(cashTotal, cashCount, bankTotal, bankCount,
                            totalRevenue, totalValidCount, pendingCount);
                }
            }
        }
        return new ShiftMetrics(0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Đếm tổng số giao dịch trong ca theo bộ lọc để phục vụ phân trang.
     */
    public int countTransactions(int shiftId, String search, String method, String status) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT o.OrderId)
                FROM Orders o
                JOIN Customers c ON c.CustomerId = o.HolderCustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                LEFT JOIN Transactions t ON t.OrderId = o.OrderId AND t.TransactionId = (
                    SELECT MAX(t2.TransactionId) FROM Transactions t2 WHERE t2.OrderId = o.OrderId
                )
                WHERE o.ShiftId = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(shiftId);

        // B1: Bộ lọc từ khóa tìm kiếm (Mã đơn hoặc Tên khách không dấu)
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (o.OrderCode LIKE ? OR p.FullName COLLATE Vietnamese_CI_AI LIKE ?) ");
            String searchPattern = "%" + search.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }

        // B2: Bộ lọc phương thức thanh toán
        if (method != null && !"all".equalsIgnoreCase(method)) {
            sql.append(" AND COALESCE(t.PaymentMethod, 'Cash') = ? ");
            params.add("cash".equalsIgnoreCase(method) ? "Cash" : "Transfer");
        }

        // B3: Bộ lọc trạng thái đơn
        if (status != null && !"all".equalsIgnoreCase(status)) {
            if ("paid".equalsIgnoreCase(status) || "ok".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Paid' ");
            } else if ("pending".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Pending' ");
            } else if ("cancelled".equalsIgnoreCase(status) || "void".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Cancelled' ");
            }
        }

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
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
        StringBuilder sql = new StringBuilder("""
                SELECT 
                    o.OrderId,
                    o.OrderCode,
                    p.FullName AS CustomerName,
                    p.Phone AS CustomerPhone,
                    o.PackageNameSnapshot,
                    o.CreatedAt,
                    COALESCE(t.PaymentMethod, 'Cash') AS PaymentMethod,
                    (o.UnitPriceSnapshot - o.DiscountAmount) AS Amount,
                    o.Status AS OrderStatus,
                    o.CancelType,
                    o.CancelReason,
                    o.CancelledAt,
                    o.HolderCustomerId
                FROM Orders o
                JOIN Customers c ON c.CustomerId = o.HolderCustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                LEFT JOIN Transactions t ON t.OrderId = o.OrderId AND t.TransactionId = (
                    SELECT MAX(t2.TransactionId) FROM Transactions t2 WHERE t2.OrderId = o.OrderId
                )
                WHERE o.ShiftId = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(shiftId);

        // B1: Bộ lọc từ khóa tìm kiếm
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (o.OrderCode LIKE ? OR p.FullName COLLATE Vietnamese_CI_AI LIKE ?) ");
            String searchPattern = "%" + search.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }

        // B2: Bộ lọc phương thức thanh toán
        if (method != null && !"all".equalsIgnoreCase(method)) {
            sql.append(" AND COALESCE(t.PaymentMethod, 'Cash') = ? ");
            params.add("cash".equalsIgnoreCase(method) ? "Cash" : "Transfer");
        }

        // B3: Bộ lọc trạng thái đơn
        if (status != null && !"all".equalsIgnoreCase(status)) {
            if ("paid".equalsIgnoreCase(status) || "ok".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Paid' ");
            } else if ("pending".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Pending' ");
            } else if ("cancelled".equalsIgnoreCase(status) || "void".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Cancelled' ");
            }
        }

        // B4: Sắp xếp đơn mới nhất lên đầu và phân trang
        sql.append(" ORDER BY o.CreatedAt DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(pageSize);

        List<ShiftTransactionItem> items = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int orderId = rs.getInt("OrderId");
                    String orderCode = rs.getString("OrderCode");
                    String customerName = rs.getString("CustomerName");
                    String customerPhone = rs.getString("CustomerPhone");
                    String packageName = rs.getString("PackageNameSnapshot");

                    Timestamp createdAtTs = rs.getTimestamp("CreatedAt");
                    LocalDateTime createdAt = createdAtTs != null ? createdAtTs.toLocalDateTime() : null;

                    String paymentMethod = rs.getString("PaymentMethod");
                    long amount = rs.getLong("Amount");
                    String orderStatus = rs.getString("OrderStatus");
                    String cancelType = rs.getString("CancelType");
                    String cancelReason = rs.getString("CancelReason");

                    Timestamp cancelledAtTs = rs.getTimestamp("CancelledAt");
                    LocalDateTime cancelledAt = cancelledAtTs != null ? cancelledAtTs.toLocalDateTime() : null;

                    // canCancel và canConfirmTransfer tạm gán sơ bộ theo trạng thái, Service sẽ hoàn thiện với SelfServiceGuard
                    boolean isPending = "Pending".equalsIgnoreCase(orderStatus);
                    boolean isCancelled = "Cancelled".equalsIgnoreCase(orderStatus);
                    boolean canCancel = !isCancelled;
                    boolean canConfirmTransfer = isPending && "Transfer".equalsIgnoreCase(paymentMethod);

                    items.add(new ShiftTransactionItem(orderId, orderCode, customerName, customerPhone,
                            packageName, createdAt, paymentMethod, amount, orderStatus, cancelType,
                            cancelReason, cancelledAt, canCancel, canConfirmTransfer));
                }
            }
        }
        return items;
    }

    /**
     * Lấy danh sách các đơn hàng đang ở trạng thái Pending trong ca để hiển thị Alert Banner và kiểm tra chốt ca.
     */
    public List<ShiftTransactionItem> findPendingOrdersInShift(int shiftId) throws SQLException {
        String sql = """
                SELECT 
                    o.OrderId,
                    o.OrderCode,
                    p.FullName AS CustomerName,
                    p.Phone AS CustomerPhone,
                    o.PackageNameSnapshot,
                    o.CreatedAt,
                    COALESCE(t.PaymentMethod, 'Transfer') AS PaymentMethod,
                    (o.UnitPriceSnapshot - o.DiscountAmount) AS Amount,
                    o.Status AS OrderStatus,
                    o.CancelType,
                    o.CancelReason,
                    o.CancelledAt,
                    o.HolderCustomerId
                FROM Orders o
                JOIN Customers c ON c.CustomerId = o.HolderCustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                LEFT JOIN Transactions t ON t.OrderId = o.OrderId
                WHERE o.ShiftId = ? AND o.Status = 'Pending'
                ORDER BY o.CreatedAt ASC
                """;

        List<ShiftTransactionItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, shiftId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int orderId = rs.getInt("OrderId");
                    String orderCode = rs.getString("OrderCode");
                    String customerName = rs.getString("CustomerName");
                    String customerPhone = rs.getString("CustomerPhone");
                    String packageName = rs.getString("PackageNameSnapshot");

                    Timestamp createdAtTs = rs.getTimestamp("CreatedAt");
                    LocalDateTime createdAt = createdAtTs != null ? createdAtTs.toLocalDateTime() : null;

                    String paymentMethod = rs.getString("PaymentMethod");
                    long amount = rs.getLong("Amount");

                    list.add(new ShiftTransactionItem(orderId, orderCode, customerName, customerPhone,
                            packageName, createdAt, paymentMethod, amount, "Pending", null, null,
                            null, true, true));
                }
            }
        }
        return list;
    }

    // ===== 4. Xác nhận Chuyển khoản & Hủy đơn hàng tại quầy =====

    /**
     * Lễ tân xác nhận đã nhận tiền chuyển khoản cho đơn hàng tại quầy (CBR-77).
     *
     * @param conn Connection tham gia transaction
     * @param orderId mã đơn hàng
     * @param employeeId nhân viên Lễ tân xác nhận
     * @param completedAt thời điểm xác nhận
     * @return số dòng bị ảnh hưởng của Orders
     * @throws SQLException khi truy vấn lỗi
     */
    public int confirmBankTransfer(Connection conn, int orderId, int employeeId, LocalDateTime completedAt) throws SQLException {
        // B1: Cập nhật giao dịch chuyển khoản sang Success
        String updateTxSql = """
                UPDATE Transactions 
                SET Status = 'Success', ReceivedByEmployeeId = ?, CompletedAt = ? 
                WHERE OrderId = ? AND Status = 'Pending'
                """;
        int txUpdated;
        try (PreparedStatement txStmt = conn.prepareStatement(updateTxSql)) {
            txStmt.setInt(1, employeeId);
            txStmt.setTimestamp(2, Timestamp.valueOf(completedAt));
            txStmt.setInt(3, orderId);
            txUpdated = txStmt.executeUpdate();
        }

        // B1.1: Nếu đơn chưa có bản ghi Transaction nào thì tạo mới giao dịch Success
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

        // B2: Cập nhật Order sang Paid
        String updateOrderSql = "UPDATE Orders SET Status = 'Paid', PaidAt = ? WHERE OrderId = ? AND Status = 'Pending'";
        try (PreparedStatement orderStmt = conn.prepareStatement(updateOrderSql)) {
            orderStmt.setTimestamp(1, Timestamp.valueOf(completedAt));
            orderStmt.setInt(2, orderId);
            return orderStmt.executeUpdate();
        }
    }

    /**
     * Hủy đơn hàng tại quầy trong ca mở (CBR-11) và hoàn lại các quyền lợi liên quan.
     *
     * @param conn Connection tham gia transaction
     * @param orderId mã đơn hàng cần hủy
     * @param reason lý do hủy (tối thiểu 5 ký tự)
     * @param now thời điểm hủy
     * @return số dòng Orders bị hủy
     * @throws SQLException khi truy vấn lỗi
     */
    public int cancelCounterOrder(Connection conn, int orderId, String reason, LocalDateTime now) throws SQLException {
        // B1: Đọc thông tin VoucherId của đơn để hoàn voucher (BR-44)
        Integer voucherId = null;
        String readOrderSql = "SELECT VoucherId FROM Orders WHERE OrderId = ?";
        try (PreparedStatement readStmt = conn.prepareStatement(readOrderSql)) {
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

        // B2: Cập nhật Order sang Cancelled với CancelType = CancelledByReceptionist
        String updateOrderSql = """
                UPDATE Orders 
                SET Status = 'Cancelled', CancelType = 'CancelledByReceptionist', 
                    CancelledAt = ?, CancelReason = ? 
                WHERE OrderId = ? AND Status IN ('Pending', 'Paid')
                """;
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

        // B3: Hoàn lại 1 lượt sử dụng voucher nếu có (BR-44)
        if (voucherId != null) {
            String returnVoucherSql = "UPDATE Vouchers SET UsedCount = UsedCount - 1 WHERE VoucherId = ? AND UsedCount > 0";
            try (PreparedStatement vStmt = conn.prepareStatement(returnVoucherSql)) {
                vStmt.setInt(1, voucherId);
                vStmt.executeUpdate();
            }
        }

        // B4: Chuyển Subscription liên quan sang Cancelled nếu có (DBR-40)
        String cancelSubSql = "UPDATE Subscriptions SET Status = 'Cancelled' WHERE OrderId = ?";
        try (PreparedStatement subStmt = conn.prepareStatement(cancelSubSql)) {
            subStmt.setInt(1, orderId);
            subStmt.executeUpdate();
        }

        // B5: Chuyển các buổi PT (Sessions) chưa tập sang Cancelled
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

        // B6: Giải phóng các khung giờ (TimeSlots) về Free nếu có (DBR-36)
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

    // ===== Hàm hỗ trợ map ResultSet =====

    private Shift mapResultSetToShift(ResultSet rs) throws SQLException {
        int shiftId = rs.getInt("ShiftId");
        Date workDateSql = rs.getDate("WorkDate");
        LocalDate workDate = workDateSql != null ? workDateSql.toLocalDate() : null;

        Shift.ShiftType shiftType = Shift.ShiftType.fromDbValue(rs.getString("ShiftType"));

        int openedBy = rs.getInt("OpenedByEmployeeId");
        Integer openedByEmployeeId = rs.wasNull() ? null : openedBy;

        int closedBy = rs.getInt("ClosedByEmployeeId");
        Integer closedByEmployeeId = rs.wasNull() ? null : closedBy;

        Shift.Status status = Shift.Status.fromDbValue(rs.getString("Status"));

        Timestamp openedAtTs = rs.getTimestamp("OpenedAt");
        LocalDateTime openedAt = openedAtTs != null ? openedAtTs.toLocalDateTime() : null;

        Timestamp closedAtTs = rs.getTimestamp("ClosedAt");
        LocalDateTime closedAt = closedAtTs != null ? closedAtTs.toLocalDateTime() : null;

        long expCash = rs.getLong("ExpectedCash");
        Long expectedCash = rs.wasNull() ? null : expCash;

        long cntCash = rs.getLong("CountedCash");
        Long countedCash = rs.wasNull() ? null : cntCash;

        String varianceReason = rs.getString("VarianceReason");

        return new Shift(shiftId, workDate, shiftType, openedByEmployeeId, closedByEmployeeId,
                status, openedAt, closedAt, expectedCash, countedCash, varianceReason);
    }
}
