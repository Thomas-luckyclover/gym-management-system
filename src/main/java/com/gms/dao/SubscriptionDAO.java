package com.gms.dao;

import com.gms.entity.Subscription;
import java.sql.*;
import java.time.LocalDate;

/** IPN cấp quyền Membership/DayPass cho đơn online đã Paid, mỗi đơn chỉ có một subscription. */
public class SubscriptionDAO {

    /** Gói mới bắt đầu sau gói Membership cuối còn Active/Frozen, gồm gói có ngày bắt đầu tương lai. */
    public LocalDate findLastMembershipEnd(Connection conn, int customerId, LocalDate today) throws SQLException {
        String sql = "SELECT MAX(EndDate) FROM Subscriptions WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE CustomerId = ? AND SubscriptionType = 'Membership' "
                + "AND Status IN ('Active','Frozen') AND EndDate >= ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setDate(2, Date.valueOf(today));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                Date end = resultSet.getDate(1);
                return end == null ? null : end.toLocalDate();
            }
        }
    }

    /** Nhận quyền lợi Service tính từ loại/thời hạn gói và ngày thanh toán, không viết luật trong DAO. */
    public int insertOnline(Connection conn, Subscription subscription) throws SQLException {
        String sql = "INSERT INTO Subscriptions (OrderId, CustomerId, SubscriptionType, StartDate, EndDate, "
                + "MaxFreezesSnapshot, Status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, subscription.getOrderId());
            statement.setInt(2, subscription.getCustomerId());
            statement.setString(3, subscription.getSubscriptionType().getDbValue());
            statement.setDate(4, Date.valueOf(subscription.getStartDate()));
            statement.setDate(5, Date.valueOf(subscription.getEndDate()));
            if (subscription.getMaxFreezesSnapshot() == null) {
                statement.setNull(6, Types.TINYINT);
            } else {
                statement.setInt(6, subscription.getMaxFreezesSnapshot());
            }
            statement.setString(7, Subscription.Status.ACTIVE.getDbValue());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Không tạo được SubscriptionId");
    }
}
