package com.gms.dao;

import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * Nút "Nhắc khách" ở PT Dashboard: tìm tài khoản của khách + ghi 1 thông báo cá nhân (Notifications).
 * Khách xem lời nhắc ở chuông thông báo (Gói của tôi, Lịch tập).
 */
public class PtRemindDAO {

    /** Tiêu đề cố định của lời nhắc → dùng để biết hôm nay PT đã nhắc khách chưa. */
    public static final String REMIND_TITLE = "PT nhắc bạn trước buổi tập";

    /** Tài khoản đang hoạt động của khách (null = khách chưa có tài khoản, không gửi được — GBR-15). */
    public Integer findActiveAccountId(int customerId) throws SQLException {
        String sql = """
                SELECT a.AccountId
                FROM Customers c
                JOIN Accounts a ON a.PersonId = c.PersonId
                WHERE c.CustomerId = ? AND a.Status = 'Active'
                """;
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("AccountId") : null;
            }
        }
    }

    /** Các khách (CustomerId) mà PT này đã nhắc trong hôm nay. */
    public Set<Integer> findRemindedToday(int ptEmployeeId) throws SQLException {
        String sql = """
                SELECT DISTINCT c.CustomerId
                FROM Notifications n
                JOIN NotificationRecipients r ON r.NotificationId = n.NotificationId
                JOIN Accounts a               ON a.AccountId = r.AccountId
                JOIN Customers c              ON c.PersonId = a.PersonId
                WHERE n.SentByEmployeeId = ? AND n.Title = ?
                  AND n.SentAt >= CAST(CAST(SYSDATETIME() AS DATE) AS DATETIME2)
                """;
        Set<Integer> customerIds = new HashSet<>();
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptEmployeeId);
            statement.setString(2, REMIND_TITLE);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    customerIds.add(rs.getInt("CustomerId"));
                }
            }
        }
        return customerIds;
    }

    /** Gửi ngay 1 thông báo cá nhân từ PT tới 1 tài khoản: 2 câu INSERT trong 1 transaction. */
    public void insertReminder(int ptEmployeeId, int accountId, String content) throws SQLException {
        String notificationSql = """
                INSERT INTO Notifications (SentByEmployeeId, Title, Content, Audience, ScheduledAt, SentAt)
                VALUES (?, ?, ?, 'Individual', NULL, SYSDATETIME())
                """;
        String recipientSql = "INSERT INTO NotificationRecipients (NotificationId, AccountId, ReadAt) VALUES (?, ?, NULL)";

        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement notification = connection.prepareStatement(notificationSql, Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement recipient = connection.prepareStatement(recipientSql)) {
                // B1: thông báo
                notification.setInt(1, ptEmployeeId);
                notification.setString(2, REMIND_TITLE);
                notification.setString(3, content);
                notification.executeUpdate();
                int notificationId;
                try (ResultSet keys = notification.getGeneratedKeys()) {
                    keys.next();
                    notificationId = keys.getInt(1);
                }

                // B2: người nhận, rồi commit cả hai
                recipient.setInt(1, notificationId);
                recipient.setInt(2, accountId);
                recipient.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();   // lỗi 1 câu → không để lại thông báo không có người nhận
                throw e;
            }
        }
    }
}
