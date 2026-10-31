package com.gms.dao;

import com.gms.constant.Role;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Notification;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy vấn Notifications và NotificationRecipients.
 */
public class NotificationDAO {

    // ===== Tạo notification hệ thống =====

    public int insertSystemNotification(
            Connection connection,
            String title,
            String content
    ) throws SQLException {

        String sql = "INSERT INTO Notifications "
                + "(SentByEmployeeId, Title, Content, Audience, ScheduledAt, SentAt) "
                + "VALUES (NULL, ?, ?, ?, NULL, SYSDATETIME())";

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setString(1, title);
            statement.setString(2, content);
            statement.setString(
                    3,
                    Notification.Audience.INDIVIDUAL.getDbValue()
            );

            statement.executeUpdate();

            try (ResultSet resultSet =
                         statement.getGeneratedKeys()) {

                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }

        throw new SQLException(
                "Không nhận được NotificationId."
        );
    }

    public void insertRecipient(
            Connection connection,
            int notificationId,
            int accountId
    ) throws SQLException {

        String sql =
                "INSERT INTO NotificationRecipients "
                        + "(NotificationId, AccountId, ReadAt) "
                        + "VALUES (?, ?, NULL)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, notificationId);
            statement.setInt(2, accountId);

            statement.executeUpdate();
        }
    }

    // ===== Tìm người nhận =====

    public List<Integer> findActiveAccountIdsByRole(
            Connection connection,
            Role role
    ) throws SQLException {

        String sql;
        if (role == Role.CUSTOMER) {
            sql = "SELECT DISTINCT a.AccountId "
                    + "FROM Customers c "
                    + "JOIN Accounts a "
                    + "ON a.PersonId = c.PersonId "
                    + "WHERE c.Status <> 'Inactive' "
                    + "AND a.Role = ? "
                    + "AND a.Status = ?";
        } else {
            sql = "SELECT DISTINCT a.AccountId "
                    + "FROM Employees e "
                    + "JOIN Accounts a "
                    + "ON a.PersonId = e.PersonId "
                    + "WHERE e.Position = ? "
                    + "AND e.Status = ? "
                    + "AND a.Status = ?";
        }

        List<Integer> accountIds =
                new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            if (role == Role.CUSTOMER) {
                statement.setString(
                        1,
                        role.getDbValue()
                );
                statement.setString(
                        2,
                        Account.Status.ACTIVE.getDbValue()
                );
            } else {
                statement.setString(
                        1,
                        role.getDbValue()
                );
                statement.setString(
                        2,
                        Employee.Status.WORKING.getDbValue()
                );
                statement.setString(
                        3,
                        Account.Status.ACTIVE.getDbValue()
                );
            }

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    accountIds.add(
                            resultSet.getInt("AccountId")
                    );
                }
            }
        }

        return accountIds;
    }

    public Integer findActiveAccountIdByCustomerId(
            Connection connection,
            int customerId
    ) throws SQLException {

        String sql =
                "SELECT a.AccountId "
                        + "FROM Customers c "
                        + "JOIN Accounts a "
                        + "ON a.PersonId = c.PersonId "
                        + "WHERE c.CustomerId = ? "
                        + "AND c.Status <> 'Inactive' "
                        + "AND a.Status = ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, customerId);
            statement.setString(
                    2,
                    Account.Status.ACTIVE.getDbValue()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt("AccountId");
                }
            }
        }

        return null;
    }

    public Integer findActiveAccountIdByEmployeeId(
            Connection connection,
            int employeeId
    ) throws SQLException {

        String sql =
                "SELECT a.AccountId "
                        + "FROM Employees e "
                        + "JOIN Accounts a "
                        + "ON a.PersonId = e.PersonId "
                        + "WHERE e.EmployeeId = ? "
                        + "AND a.Status = ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, employeeId);

            statement.setString(
                    2,
                    Account.Status.ACTIVE.getDbValue()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt("AccountId");
                }
            }
        }

        return null;
    }

    // ===== Chuông thông báo =====

    public int countUnread(int accountId)
            throws SQLException {

        String sql =
                "SELECT COUNT(*) "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "WHERE nr.AccountId = ? "
                        + "AND nr.ReadAt IS NULL "
                        + "AND n.SentAt IS NOT NULL";

        try (Connection connection =
                     DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }

        return 0;
    }

    public List<NotificationBellRow> findLatestForAccount(
            int accountId,
            int limit
    ) throws SQLException {

        String sql =
                "SELECT "
                        + "n.NotificationId, "
                        + "n.SentByEmployeeId, "
                        + "n.Title, "
                        + "n.Content, "
                        + "n.Audience, "
                        + "n.ScheduledAt, "
                        + "n.SentAt, "
                        + "n.CreatedAt, "
                        + "nr.ReadAt, "
                        + "p.FullName AS SenderName, "
                        + "e.EmployeeCode AS SenderCode, "
                        + "e.Position AS SenderPosition "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "LEFT JOIN Employees e "
                        + "ON e.EmployeeId = n.SentByEmployeeId "
                        + "LEFT JOIN Persons p "
                        + "ON p.PersonId = e.PersonId "
                        + "WHERE nr.AccountId = ? "
                        + "AND n.SentAt IS NOT NULL "
                        + "ORDER BY n.SentAt DESC, "
                        + "n.NotificationId DESC";

        List<NotificationBellRow> rows =
                new ArrayList<>();

        try (Connection connection =
                     DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);
            statement.setMaxRows(limit);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    rows.add(
                            mapBellRow(resultSet)
                    );
                }
            }
        }

        return rows;
    }

    // ===== Đã đọc =====

    public boolean markRead(
            int accountId,
            int notificationId
    ) throws SQLException {

        String sql =
                "UPDATE nr "
                        + "SET nr.ReadAt = "
                        + "COALESCE(nr.ReadAt, SYSDATETIME()) "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "WHERE nr.AccountId = ? "
                        + "AND nr.NotificationId = ? "
                        + "AND n.SentAt IS NOT NULL";

        try (Connection connection =
                     DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);
            statement.setInt(2, notificationId);

            return statement.executeUpdate() == 1;
        }
    }

    public int markAllRead(int accountId)
            throws SQLException {

        String sql =
                "UPDATE nr "
                        + "SET nr.ReadAt = SYSDATETIME() "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "WHERE nr.AccountId = ? "
                        + "AND nr.ReadAt IS NULL "
                        + "AND n.SentAt IS NOT NULL";

        try (Connection connection =
                     DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);

            return statement.executeUpdate();
        }
    }

    // ===== Mapping =====

    private NotificationBellRow mapBellRow(
            ResultSet resultSet
    ) throws SQLException {

        Notification notification =
                new Notification();

        notification.setNotificationId(
                resultSet.getInt("NotificationId")
        );

        int sentByEmployeeId =
                resultSet.getInt("SentByEmployeeId");

        notification.setSentByEmployeeId(
                resultSet.wasNull()
                        ? null
                        : sentByEmployeeId
        );

        notification.setTitle(
                resultSet.getString("Title")
        );

        notification.setContent(
                resultSet.getString("Content")
        );

        notification.setAudience(
                Notification.Audience.fromDbValue(
                        resultSet.getString("Audience")
                )
        );

        notification.setScheduledAt(
                toLocalDateTime(
                        resultSet.getTimestamp("ScheduledAt")
                )
        );

        notification.setSentAt(
                toLocalDateTime(
                        resultSet.getTimestamp("SentAt")
                )
        );

        notification.setCreatedAt(
                toLocalDateTime(
                        resultSet.getTimestamp("CreatedAt")
                )
        );

        LocalDateTime readAt =
                toLocalDateTime(
                        resultSet.getTimestamp("ReadAt")
                );

        String senderName =
                resultSet.getString("SenderName");

        String senderCode =
                resultSet.getString("SenderCode");

        String senderPosition =
                resultSet.getString("SenderPosition");

        return new NotificationBellRow(
                notification,
                readAt,
                senderName,
                senderCode,
                senderPosition
        );
    }

    private LocalDateTime toLocalDateTime(
            Timestamp timestamp
    ) {

        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }

    public static class NotificationBellRow {

        private final Notification notification;
        private final LocalDateTime readAt;
        private final String senderName;
        private final String senderCode;
        private final String senderPosition;

        public NotificationBellRow(
                Notification notification,
                LocalDateTime readAt
        ) {
            this(notification, readAt, null, null, null);
        }

        public NotificationBellRow(
                Notification notification,
                LocalDateTime readAt,
                String senderName,
                String senderCode,
                String senderPosition
        ) {
            this.notification = notification;
            this.readAt = readAt;
            this.senderName = senderName;
            this.senderCode = senderCode;
            this.senderPosition = senderPosition;
        }

        public Notification getNotification() {
            return notification;
        }

        public LocalDateTime getReadAt() {
            return readAt;
        }

        public boolean isRead() {
            return readAt != null;
        }

        public String getSenderName() {
            return senderName;
        }

        public String getSenderCode() {
            return senderCode;
        }

        public String getSenderPosition() {
            return senderPosition;
        }
    }
}
