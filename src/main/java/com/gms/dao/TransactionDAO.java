package com.gms.dao;

import com.gms.dto.ManagerOrderDetailView;
import com.gms.dto.ManagerTransactionItem;
import com.gms.dto.TransactionAttemptView;
import com.gms.entity.Transaction;
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
 * Xử lý truy vấn dữ liệu bảng Transactions và các báo cáo giao dịch toàn hệ thống (Màn #41 - UC-26).
 *
 * <p>Quy tắc nghiệp vụ:</p>
 * <ul>
 *   <li>Mỗi đơn hàng có thể có nhiều Transaction nhưng tối đa 1 Pending và 1 Success (DBR-42).</li>
 *   <li>Trường hợp tiền về muộn khi đơn đã hủy (CBR-72): NeedsRefund = 1 để Manager hoàn tiền ngoài hệ thống (BR-47).</li>
 *   <li>Thanh toán tại quầy thành công phải ghi nhận Lễ tân thu tiền (ReceivedByEmployeeId, CBR-77).</li>
 * </ul>
 *
 * @author Le Cong Minh
 */
public class TransactionDAO {

    /**
     * Đếm số lượng giao dịch thành công cần hoàn tiền ngoài hệ thống mà chưa được hoàn (CBR-72, BR-47).
     *
     * <p>Dùng để hiển thị số đếm (badge counter) cạnh checkbox "Giao dịch cần hoàn tiền" trên thanh lọc.</p>
     *
     * @return số giao dịch đang chờ Manager hoàn tiền
     * @throws SQLException khi truy vấn lỗi
     */
    public int countPendingRefunds() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT t.TransactionId) "
                + "FROM Transactions t "
                + "JOIN Orders o ON o.OrderId = t.OrderId "
                + "WHERE t.NeedsRefund = 1 AND t.Status = 'Success' AND t.RefundedAt IS NULL";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Đếm tổng số đơn hàng khớp với bộ lọc của Manager (phục vụ phân trang Tab 1).
     *
     * @param search          từ khóa tìm kiếm (Mã đơn hoặc Tên khách)
     * @param method          phương thức thanh toán (Cash, Transfer, OnlineGateway, hoặc all)
     * @param status          trạng thái đơn (Paid, Pending, Cancelled, hoặc all)
     * @param needsRefundOnly true nếu chỉ lọc các giao dịch cần hoàn tiền
     * @param fromDate        ngày bắt đầu tạo đơn
     * @param toDate          ngày kết thúc tạo đơn
     * @return tổng số đơn hàng thỏa mãn
     * @throws SQLException khi truy vấn lỗi
     */
    public int countManagerTransactions(String search, String method, String status,
                                        Boolean needsRefundOnly, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT o.OrderId) FROM Orders o "
                + "JOIN Customers c ON c.CustomerId = o.HolderCustomerId "
                + "JOIN Persons p ON p.PersonId = c.PersonId "
                + "LEFT JOIN Transactions t ON t.OrderId = o.OrderId AND t.TransactionId = ("
                + "    SELECT MAX(t2.TransactionId) FROM Transactions t2 WHERE t2.OrderId = o.OrderId"
                + ") "
                + "LEFT JOIN Transactions tRefund ON tRefund.OrderId = o.OrderId AND tRefund.NeedsRefund = 1 AND tRefund.Status = 'Success' "
                + "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        appendFilterConditions(sql, params, search, method, status, needsRefundOnly, fromDate, toDate);

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
     * Lấy danh sách đơn hàng & giao dịch có phân trang phục vụ Tab 1 của Manager (SRS 7.4.2).
     *
     * @param search          từ khóa tìm kiếm
     * @param method          phương thức thanh toán
     * @param status          trạng thái đơn
     * @param needsRefundOnly chỉ lấy đơn cần hoàn tiền
     * @param fromDate        từ ngày
     * @param toDate          đến ngày
     * @param offset          vị trí bắt đầu
     * @param pageSize        số bản ghi mỗi trang
     * @return danh sách ManagerTransactionItem
     * @throws SQLException khi truy vấn lỗi
     */
    public List<ManagerTransactionItem> findManagerTransactionsPage(String search, String method, String status,
                                                                   Boolean needsRefundOnly, LocalDate fromDate,
                                                                   LocalDate toDate, int offset, int pageSize)
            throws SQLException {
        StringBuilder sql = new StringBuilder(buildSelectManagerTransactionsSql());
        List<Object> params = new ArrayList<>();

        appendFilterConditions(sql, params, search, method, status, needsRefundOnly, fromDate, toDate);

        sql.append(" ORDER BY o.CreatedAt DESC, o.OrderId DESC ");
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ");
        params.add(offset);
        params.add(pageSize);

        List<ManagerTransactionItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToManagerTransactionItem(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy toàn bộ danh sách đơn hàng & giao dịch khớp bộ lọc để xuất file Excel (SRS 7.4.2).
     *
     * @param search          từ khóa tìm kiếm
     * @param method          phương thức thanh toán
     * @param status          trạng thái đơn
     * @param needsRefundOnly chỉ lấy đơn cần hoàn tiền
     * @param fromDate        từ ngày
     * @param toDate          đến ngày
     * @return danh sách toàn bộ các dòng để xuất file
     * @throws SQLException khi truy vấn lỗi
     */
    public List<ManagerTransactionItem> findAllManagerTransactionsForExport(String search, String method, String status,
                                                                           Boolean needsRefundOnly, LocalDate fromDate,
                                                                           LocalDate toDate)
            throws SQLException {
        StringBuilder sql = new StringBuilder(buildSelectManagerTransactionsSql());
        List<Object> params = new ArrayList<>();

        appendFilterConditions(sql, params, search, method, status, needsRefundOnly, fromDate, toDate);

        sql.append(" ORDER BY o.CreatedAt DESC, o.OrderId DESC ");

        List<ManagerTransactionItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToManagerTransactionItem(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy thông tin chi tiết đầy đủ của đơn hàng kèm các lần thử thanh toán cho modal chi tiết (UC-26).
     *
     * @param orderId mã đơn hàng
     * @return ManagerOrderDetailView hoặc null nếu không tìm thấy
     * @throws SQLException khi truy vấn lỗi
     */
    public ManagerOrderDetailView findOrderDetailForManager(int orderId) throws SQLException {
        String sql = "SELECT "
                + "    o.OrderId, o.OrderCode, o.Channel, o.CreatedAt, o.PaymentDueAt, o.PaidAt, o.Status, "
                + "    o.CancelType, o.CancelledAt, o.CancelReason, o.ShiftId, "
                + "    pCreator.FullName AS CreatedByEmployeeName, "
                + "    pHolder.FullName AS CustomerName, pHolder.Phone AS CustomerPhone, pHolder.Email AS CustomerEmail, "
                + "    pPartner.FullName AS PartnerName, "
                + "    o.PackageNameSnapshot, o.PackageDescriptionSnapshot, o.PackageBenefitsSnapshot, "
                + "    o.UnitPriceSnapshot, o.DiscountAmount, v.VoucherCode, "
                + "    o.VatRateSnapshot, o.VatAmount "
                + "FROM Orders o "
                + "JOIN Customers cHolder ON cHolder.CustomerId = o.HolderCustomerId "
                + "JOIN Persons pHolder ON pHolder.PersonId = cHolder.PersonId "
                + "LEFT JOIN Customers cPartner ON cPartner.CustomerId = o.PartnerCustomerId "
                + "LEFT JOIN Persons pPartner ON pPartner.PersonId = cPartner.PersonId "
                + "LEFT JOIN Employees eCreator ON eCreator.EmployeeId = o.CreatedByEmployeeId "
                + "LEFT JOIN Persons pCreator ON pCreator.PersonId = eCreator.PersonId "
                + "LEFT JOIN Vouchers v ON v.VoucherId = o.VoucherId "
                + "WHERE o.OrderId = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long unitPrice = rs.getLong("UnitPriceSnapshot");
                    long discountAmount = rs.getLong("DiscountAmount");
                    long totalPayable = unitPrice - discountAmount;
                    long vatAmount = rs.getLong("VatAmount");
                    long preTaxAmount = totalPayable - vatAmount;

                    List<TransactionAttemptView> attempts = findAttemptsByOrderId(conn, orderId);

                    return new ManagerOrderDetailView(
                            rs.getInt("OrderId"),
                            rs.getString("OrderCode"),
                            rs.getString("Channel"),
                            readNullableDateTime(rs, "CreatedAt"),
                            readNullableDateTime(rs, "PaymentDueAt"),
                            readNullableDateTime(rs, "PaidAt"),
                            rs.getString("Status"),
                            rs.getString("CancelType"),
                            readNullableDateTime(rs, "CancelledAt"),
                            rs.getString("CancelReason"),
                            rs.getString("CreatedByEmployeeName"),
                            readNullableInt(rs, "ShiftId"),
                            rs.getString("CustomerName"),
                            rs.getString("CustomerPhone"),
                            rs.getString("CustomerEmail"),
                            rs.getString("PartnerName"),
                            rs.getString("PackageNameSnapshot"),
                            rs.getString("PackageDescriptionSnapshot"),
                            rs.getString("PackageBenefitsSnapshot"),
                            unitPrice,
                            discountAmount,
                            rs.getString("VoucherCode"),
                            rs.getInt("VatRateSnapshot"),
                            vatAmount,
                            totalPayable,
                            preTaxAmount,
                            attempts
                    );
                }
            }
        }
        return null;
    }

    /**
     * Lấy danh sách các lần thử thanh toán của một đơn hàng.
     *
     * @param orderId mã đơn hàng
     * @return danh sách TransactionAttemptView sắp xếp theo thời gian tăng dần
     * @throws SQLException khi truy vấn lỗi
     */
    public List<TransactionAttemptView> findAttemptsByOrderId(int orderId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findAttemptsByOrderId(conn, orderId);
        }
    }

    /**
     * Bản dùng chung Connection trong transaction để lấy các lần thử thanh toán.
     */
    public List<TransactionAttemptView> findAttemptsByOrderId(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT "
                + "    t.TransactionId, t.OrderId, t.Amount, t.PaymentMethod, t.GatewayReference, "
                + "    pReceiver.FullName AS ReceiverName, "
                + "    t.Status, t.FailureReason, t.NeedsRefund, t.RefundedAt, "
                + "    pRefunder.FullName AS RefundedByName, t.RefundNote, "
                + "    t.CreatedAt, t.ExpiresAt, t.CompletedAt "
                + "FROM Transactions t "
                + "LEFT JOIN Employees eReceiver ON eReceiver.EmployeeId = t.ReceivedByEmployeeId "
                + "LEFT JOIN Persons pReceiver ON pReceiver.PersonId = eReceiver.PersonId "
                + "LEFT JOIN Employees eRefunder ON eRefunder.EmployeeId = t.RefundedByEmployeeId "
                + "LEFT JOIN Persons pRefunder ON pRefunder.PersonId = eRefunder.PersonId "
                + "WHERE t.OrderId = ? "
                + "ORDER BY t.CreatedAt ASC, t.TransactionId ASC";

        List<TransactionAttemptView> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new TransactionAttemptView(
                            rs.getInt("TransactionId"),
                            rs.getInt("OrderId"),
                            rs.getLong("Amount"),
                            rs.getString("PaymentMethod"),
                            rs.getString("GatewayReference"),
                            rs.getString("ReceiverName"),
                            rs.getString("Status"),
                            rs.getString("FailureReason"),
                            rs.getBoolean("NeedsRefund"),
                            readNullableDateTime(rs, "RefundedAt"),
                            rs.getString("RefundedByName"),
                            rs.getString("RefundNote"),
                            readNullableDateTime(rs, "CreatedAt"),
                            readNullableDateTime(rs, "ExpiresAt"),
                            readNullableDateTime(rs, "CompletedAt")
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Đánh dấu hoàn tiền ngoài hệ thống cho một giao dịch theo BR-47 và CBR-72.
     *
     * @param conn               Connection tham gia transaction
     * @param transactionId      mã giao dịch cần đánh dấu hoàn
     * @param managerEmployeeId  mã nhân viên Manager thực hiện xác nhận
     * @param refundNote         ghi chú hoàn tiền
     * @return số dòng bị ảnh hưởng (1 nếu thành công, 0 nếu giao dịch không hợp lệ hoặc đã hoàn trước đó)
     * @throws SQLException khi truy vấn lỗi
     */
    public int markRefunded(Connection conn, int transactionId, int managerEmployeeId, String refundNote)
            throws SQLException {
        String sql = "UPDATE Transactions "
                + "SET RefundedAt = ?, RefundedByEmployeeId = ?, RefundNote = ? "
                + "WHERE TransactionId = ? AND NeedsRefund = 1 AND Status = 'Success' AND RefundedAt IS NULL";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now(DateTimeUtil.GYM_ZONE)));
            stmt.setInt(2, managerEmployeeId);
            stmt.setString(3, refundNote);
            stmt.setInt(4, transactionId);

            return stmt.executeUpdate();
        }
    }

    // ===== Các hàm phụ trợ =====

    private String buildSelectManagerTransactionsSql() {
        return "SELECT "
                + "    o.OrderId, o.OrderCode, o.Channel, o.CreatedAt, "
                + "    p.FullName AS CustomerName, p.Phone AS CustomerPhone, "
                + "    o.PackageNameSnapshot, v.VoucherCode, "
                + "    o.UnitPriceSnapshot, o.DiscountAmount, "
                + "    (o.UnitPriceSnapshot - o.DiscountAmount) AS TotalAmount, "
                + "    o.VatRateSnapshot, o.VatAmount, "
                + "    COALESCE(t.PaymentMethod, 'Cash') AS PaymentMethod, "
                + "    o.Status AS OrderStatus, o.CancelType, o.CancelReason, "
                + "    tRefund.TransactionId AS RefundableTransactionId, "
                + "    CASE WHEN tRefund.TransactionId IS NOT NULL THEN 1 ELSE 0 END AS NeedsRefund, "
                + "    tRefund.RefundedAt, "
                + "    pRefunder.FullName AS RefundedByName, "
                + "    tRefund.RefundNote "
                + "FROM Orders o "
                + "JOIN Customers c ON c.CustomerId = o.HolderCustomerId "
                + "JOIN Persons p ON p.PersonId = c.PersonId "
                + "LEFT JOIN Vouchers v ON v.VoucherId = o.VoucherId "
                + "LEFT JOIN Transactions t ON t.OrderId = o.OrderId AND t.TransactionId = ("
                + "    SELECT MAX(t2.TransactionId) FROM Transactions t2 WHERE t2.OrderId = o.OrderId"
                + ") "
                + "LEFT JOIN Transactions tRefund ON tRefund.OrderId = o.OrderId AND tRefund.NeedsRefund = 1 AND tRefund.Status = 'Success' "
                + "LEFT JOIN Employees eRefunder ON eRefunder.EmployeeId = tRefund.RefundedByEmployeeId "
                + "LEFT JOIN Persons pRefunder ON pRefunder.PersonId = eRefunder.PersonId "
                + "WHERE 1=1 ";
    }

    private void appendFilterConditions(StringBuilder sql, List<Object> params,
                                        String search, String method, String status,
                                        Boolean needsRefundOnly, LocalDate fromDate, LocalDate toDate) {
        // B1: Từ khóa tìm kiếm mã đơn hoặc tên khách
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (o.OrderCode LIKE ? OR p.FullName COLLATE Vietnamese_CI_AI LIKE ?) ");
            String searchPattern = "%" + search.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }

        // B2: Phương thức thanh toán
        if (method != null && !"all".equalsIgnoreCase(method)) {
            if ("cash".equalsIgnoreCase(method)) {
                sql.append(" AND COALESCE(t.PaymentMethod, 'Cash') = 'Cash' ");
            } else if ("transfer".equalsIgnoreCase(method)) {
                sql.append(" AND t.PaymentMethod = 'Transfer' ");
            } else if ("online".equalsIgnoreCase(method) || "onlinegateway".equalsIgnoreCase(method)) {
                sql.append(" AND t.PaymentMethod = 'OnlineGateway' ");
            }
        }

        // B3: Trạng thái đơn hàng
        if (status != null && !"all".equalsIgnoreCase(status)) {
            if ("paid".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Paid' ");
            } else if ("pending".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Pending' ");
            } else if ("cancelled".equalsIgnoreCase(status)) {
                sql.append(" AND o.Status = 'Cancelled' ");
            }
        }

        // B4: Lọc giao dịch cần hoàn tiền
        if (Boolean.TRUE.equals(needsRefundOnly)) {
            sql.append(" AND tRefund.NeedsRefund = 1 AND tRefund.RefundedAt IS NULL ");
        }

        // B5: Khoảng thời gian
        if (fromDate != null) {
            sql.append(" AND CAST(o.CreatedAt AS DATE) >= ? ");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND CAST(o.CreatedAt AS DATE) <= ? ");
            params.add(Date.valueOf(toDate));
        }
    }

    private ManagerTransactionItem mapResultSetToManagerTransactionItem(ResultSet rs) throws SQLException {
        int refId = rs.getInt("RefundableTransactionId");
        Integer refundableTransactionId = rs.wasNull() ? null : refId;

        return new ManagerTransactionItem(
                rs.getInt("OrderId"),
                rs.getString("OrderCode"),
                rs.getString("Channel"),
                readNullableDateTime(rs, "CreatedAt"),
                rs.getString("CustomerName"),
                rs.getString("CustomerPhone"),
                rs.getString("PackageNameSnapshot"),
                rs.getString("VoucherCode"),
                rs.getLong("UnitPriceSnapshot"),
                rs.getLong("DiscountAmount"),
                rs.getLong("TotalAmount"),
                rs.getInt("VatRateSnapshot"),
                rs.getLong("VatAmount"),
                rs.getString("PaymentMethod"),
                rs.getString("OrderStatus"),
                rs.getString("CancelType"),
                rs.getString("CancelReason"),
                refundableTransactionId,
                rs.getInt("NeedsRefund") == 1,
                readNullableDateTime(rs, "RefundedAt"),
                rs.getString("RefundedByName"),
                rs.getString("RefundNote")
        );
    }

    private LocalDateTime readNullableDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Integer readNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
