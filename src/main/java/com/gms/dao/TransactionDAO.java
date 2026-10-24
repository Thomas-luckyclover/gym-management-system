package com.gms.dao;

import com.gms.entity.Transaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * Đọc và lưu các lần thử thanh toán. TransactionId là ID nội bộ.
 * GatewayReference lưu merchant txnRef gửi sang VNPay.
 */
public class TransactionDAO {

    /**
     * Kiểm tra đơn đã có giao dịch đang chờ kết quả thanh toán hay chưa.
     * Service dùng kết quả để chặn tạo thêm giao dịch Pending cho cùng đơn. Hàm
     * chỉ đọc dữ liệu, không đổi trạng thái hoặc xử lý hết hạn giao dịch.
     *
     * @param conn kết nối do Service quản lý, không đóng trong hàm này
     * @param orderId ID đơn cần kiểm tra
     * @return true nếu có giao dịch Pending của đơn; false nếu không có
     * @throws SQLException nếu truy vấn database thất bại
     */
    public boolean hasPending(Connection conn, int orderId) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT TOP (1) TransactionId FROM Transactions WHERE OrderId = ? AND Status = 'Pending'")) {
            statement.setInt(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Lấy số lần thử thanh toán online tiếp theo của một đơn. PaymentService
     * phải khóa đơn trước khi gọi.
     *
     * @param conn kết nối trong transaction của Service
     * @param orderId đơn cần thanh toán
     * @return số lần thử tiếp theo, bắt đầu từ 1
     */
    public int nextAttempt(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT COUNT(*) + 1 AS NextAttempt "
                + "FROM Transactions "
                + "WHERE OrderId = ? AND PaymentMethod = 'OnlineGateway'";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, orderId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("NextAttempt");
                }
            }
        }

        throw new SQLException("Không xác định được lần thử thanh toán");
    }

    /**
     * Lưu Transaction Pending, số tiền/hạn từ Service, dùng chung transaction
     * của đơn.
     *
     * @param conn transaction PaymentService quản lý
     * @param payment dữ liệu lần thử mới, không có bank reference trước khi
     * nhận IPN
     * @return ID tự sinh, chính là merchant txnRef gửi VNPay; không thay schema
     */
    /**
     * Lưu một lần thử thanh toán online Pending. Service quản lý
     * commit/rollback, DAO không tự commit.
     *
     * @param conn kết nối trong transaction của Service
     * @param payment giao dịch Service đã chuẩn bị
     * @return TransactionId do database tự sinh
     */
    public int insertPending(Connection conn, Transaction payment)
            throws SQLException {

        String sql = "INSERT INTO Transactions "
                + "(OrderId, Amount, PaymentMethod, Status, "
                + "CreatedAt, ExpiresAt, GatewayReference) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            // B1: lưu đơn, số tiền và trạng thái.
            statement.setInt(1, payment.getOrderId());
            statement.setLong(2, payment.getAmount());
            statement.setString(
                    3, Transaction.PaymentMethod.ONLINE_GATEWAY.getDbValue());
            statement.setString(
                    4, Transaction.Status.PENDING.getDbValue());

            // B2: lưu thời điểm tạo, hạn link và merchant txnRef.
            statement.setTimestamp(
                    5, Timestamp.valueOf(payment.getCreatedAt()));
            statement.setTimestamp(
                    6, Timestamp.valueOf(payment.getExpiresAt()));
            statement.setString(7, payment.getGatewayReference());

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Không tạo được giao dịch thanh toán");
            }

            // B3: lấy ID nội bộ do SQL Server tự sinh.
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }

        throw new SQLException("Không lấy được TransactionId sau khi tạo");
    }

    /**
     * Đọc transaction theo txnRef = TransactionId từ SEQ-3; khi lock=true giữ
     * khóa tới commit.
     */
    public Transaction findById(Connection conn, int transactionId, boolean lock) throws SQLException {
        String sql = "SELECT TransactionId, OrderId, Amount, PaymentMethod, Status, GatewayReference, "
                + "NeedsRefund, CreatedAt, ExpiresAt, CompletedAt FROM Transactions "
                + (lock ? "WITH (UPDLOCK, HOLDLOCK) " : "") + "WHERE TransactionId = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Transaction payment = new Transaction(resultSet.getInt("TransactionId"));
                payment.setOrderId(resultSet.getInt("OrderId"));
                payment.setAmount(resultSet.getLong("Amount"));
                payment.setPaymentMethod(Transaction.PaymentMethod.fromDbValue(resultSet.getString("PaymentMethod")));
                payment.setStatus(Transaction.Status.fromDbValue(resultSet.getString("Status")));
                payment.setGatewayReference(resultSet.getString("GatewayReference"));
                payment.setNeedsRefund(resultSet.getBoolean("NeedsRefund"));
                payment.setCreatedAt(resultSet.getTimestamp("CreatedAt").toLocalDateTime());
                Timestamp expires = resultSet.getTimestamp("ExpiresAt");
                payment.setExpiresAt(expires == null ? null : expires.toLocalDateTime());
                Timestamp completed = resultSet.getTimestamp("CompletedAt");
                payment.setCompletedAt(completed == null ? null : completed.toLocalDateTime());
                return payment;
            }
        }
    }

    /**
     * Kiểm index hiện tại: có Success khác thì chưa được ghi Success thứ hai,
     * kể cả NeedsRefund.
     */
    public boolean hasOtherSuccess(Connection conn, int orderId, int excludedId) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT TOP (1) TransactionId FROM Transactions WHERE OrderId = ? AND TransactionId <> ? AND Status = 'Success'")) {
            statement.setInt(1, orderId);
            statement.setInt(2, excludedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Ghi kết quả callback có điều kiện; Expired có thể nhận Success muộn,
     * không bỏ sót tiền đã về.
     */
    public int recordResult(Connection conn, int transactionId, Transaction.Status status, String reference,
            boolean needsRefund, String failureReason, java.time.LocalDateTime now) throws SQLException {
        String sql = "UPDATE Transactions SET Status = ?, GatewayReference = ?, NeedsRefund = ?, "
                + "FailureReason = ?, CompletedAt = ? WHERE TransactionId = ? AND Status IN ('Pending', 'Expired')";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, status.getDbValue());
            statement.setString(2, reference);
            statement.setBoolean(3, needsRefund);
            statement.setString(4, failureReason);
            if (status == Transaction.Status.EXPIRED) {
                statement.setNull(5, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(5, Timestamp.valueOf(now));
            }
            statement.setInt(6, transactionId);
            return statement.executeUpdate();
        }
    }

    /**
     * Tìm lần thanh toán theo merchant txnRef đã gửi sang VNPay.
     *
     * @param conn kết nối của Service
     * @param txnRef giá trị vnp_TxnRef nhận từ IPN
     * @return giao dịch tìm được hoặc null
     */
    public Transaction findByGatewayReference(
            Connection conn, String txnRef) throws SQLException {

        String sql = "SELECT TransactionId "
                + "FROM Transactions "
                + "WHERE GatewayReference = ? "
                + "AND PaymentMethod = 'OnlineGateway'";

        Integer transactionId = null;

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, txnRef);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    transactionId = resultSet.getInt("TransactionId");
                }
            }
        }

        if (transactionId == null) {
            return null;
        }

        return findById(conn, transactionId, false);
    }
}
