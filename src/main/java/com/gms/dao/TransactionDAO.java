package com.gms.dao;

import com.gms.entity.Transaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/** SEQ-3: tạo một lần thử thanh toán; TransactionId dùng làm vnp_TxnRef duy nhất. */
public class TransactionDAO {

    /** Đơn đã được Service khóa; đọc giao dịch Pending để từ chối bấm thanh toán trùng. */
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
     * Lưu Transaction Pending, số tiền/hạn từ Service, dùng chung transaction của đơn.
     * @param conn transaction PaymentService quản lý
     * @param payment dữ liệu lần thử mới, không có bank reference trước khi nhận IPN
     * @return ID tự sinh, chính là merchant txnRef gửi VNPay; không thay schema
     */
    public int insertPending(Connection conn, Transaction payment) throws SQLException {
        String sql = "INSERT INTO Transactions (OrderId, Amount, PaymentMethod, Status, CreatedAt, ExpiresAt) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, payment.getOrderId());
            statement.setLong(2, payment.getAmount());
            statement.setString(3, Transaction.PaymentMethod.ONLINE_GATEWAY.getDbValue());
            statement.setString(4, Transaction.Status.PENDING.getDbValue());
            statement.setTimestamp(5, Timestamp.valueOf(payment.getCreatedAt()));
            statement.setTimestamp(6, Timestamp.valueOf(payment.getExpiresAt()));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Không tạo được transaction Pending");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Thiếu TransactionId sau INSERT");
    }
    /** Đọc transaction theo txnRef = TransactionId từ SEQ-3; khi lock=true giữ khóa tới commit. */
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

    /** Kiểm index hiện tại: có Success khác thì chưa được ghi Success thứ hai, kể cả NeedsRefund. */
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

    /** Ghi kết quả callback có điều kiện; Expired có thể nhận Success muộn, không bỏ sót tiền đã về. */
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

}
