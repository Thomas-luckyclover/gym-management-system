package com.gms.dao;

import com.gms.dto.PaymentCustomerView;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Truy vấn SEQ-1; mọi ghi dữ liệu dùng Connection do PaymentService mở.
 */
public class PaymentDAO {

    /**
     * Đọc hồ sơ theo tài khoản, giữ khóa tới cuối transaction để hai lần mua
     * đồng thời của cùng khách phải lần lượt kiểm tra đơn cũ.
     *
     * @param conn transaction tạo đơn
     * @param accountId mã tài khoản từ session
     * @return hồ sơ còn hiệu lực; null nếu tài khoản/hồ sơ không hợp lệ
     */
    public PaymentCustomerView findCustomerForPurchase(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT c.CustomerId, CASE WHEN c.Status = 'Frozen' THEN 1 ELSE 0 END AS IsFrozen "
                // Khóa hồ sơ khách trong transaction để các yêu cầu cùng xử lý khách này phải chờ nhau.
                // Khóa được giải phóng khi commit hoặc rollback.
                + "FROM Customers c WITH (UPDLOCK, HOLDLOCK) "
                + "JOIN Accounts a ON a.PersonId = c.PersonId "
                + "WHERE a.AccountId = ? AND a.Status = 'Active' AND c.Status <> 'Inactive'";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new PaymentCustomerView(resultSet.getInt("CustomerId"), resultSet.getBoolean("IsFrozen"));
                }
            }
        }
        return null;
    }

    /**
     * Trả đúng một lượt voucher của đơn vừa hủy; Service kiểm rowsAffected.
     *
     * @param conn transaction hủy đơn cũ và tạo đơn mới
     * @param voucherId voucher trên đơn cũ, không lấy từ request
     * @return số voucher cập nhật; 0 nghĩa là dữ liệu không nhất quán
     */
    public int returnVoucherUse(Connection conn, int voucherId) throws SQLException {
        String sql = "UPDATE Vouchers SET UsedCount = UsedCount - 1 WHERE VoucherId = ? AND UsedCount > 0";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, voucherId);
            return statement.executeUpdate();
        }
    }

    /**
     * CBR-75: còn transaction Pending thì không cho thêm/gỡ/thay voucher.
     */
    public boolean hasPendingTransaction(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT TOP (1) TransactionId FROM Transactions "
                + "WHERE OrderId = ? AND Status = 'Pending'";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Khóa hồ sơ khách trước khi khóa đơn và giao dịch. Giữ cùng thứ tự khóa
     * với tạo đơn, voucher và mở thanh toán.
     *
     * @param conn kết nối trong transaction của Service
     * @param customerId chủ đơn lấy từ database
     */
    public void lockCustomer(Connection conn, int customerId)
            throws SQLException {

        String sql = "SELECT CustomerId "
                + "FROM Customers WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE CustomerId = ?";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, customerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Không tìm thấy chủ đơn");
                }
            }
        }
    }

    /**
     * Chỉ chuyển hồ sơ New/Expired thành Active. Không tự mở Frozen hoặc khôi
     * phục Inactive.
     *
     * @return số hồ sơ thay đổi; 0 có thể là khách đã Active
     */
    public int activateNewOrExpired(Connection conn, int customerId)
            throws SQLException {

        String sql = "UPDATE Customers "
                + "SET Status = 'Active' "
                + "WHERE CustomerId = ? AND Status IN ('New', 'Expired')";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            return statement.executeUpdate();
        }
    }

    /**
     * Tìm tài khoản của chủ đơn để gửi thông báo trong ứng dụng.
     *
     * @return AccountId còn Active hoặc null nếu không có
     */
    public Integer findAccountForCustomer(Connection conn, int customerId)
            throws SQLException {

        String sql = "SELECT a.AccountId "
                + "FROM Accounts a "
                + "JOIN Customers c ON c.PersonId = a.PersonId "
                + "WHERE c.CustomerId = ? AND a.Status = 'Active'";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, customerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("AccountId");
                }
            }
        }

        return null;
    }
}
