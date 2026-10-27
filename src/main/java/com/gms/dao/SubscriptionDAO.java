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

    // ===================== KHÁCH TỰ CHỌN NGÀY BẮT ĐẦU =====================

    /** Tên hành động trong AuditLogs; cũng dùng để đếm số lần khách đã đổi ngày (tối đa 1). */
    public static final String ACTION_CHANGE_START = "Đổi ngày bắt đầu gói";

    private static final String SELECT_ONE = "SELECT SubscriptionId, OrderId, CustomerId, SubscriptionType, "
            + "StartDate, EndDate, Status FROM Subscriptions ";

    /** Đọc gói (không khóa) để dựng giao diện chọn ngày. */
    public Subscription findById(Connection conn, int subscriptionId) throws SQLException {
        return findOne(conn, SELECT_ONE + "WHERE SubscriptionId = ?", subscriptionId);
    }

    /** Đọc gói theo đơn (trang kết quả thanh toán chỉ biết mã đơn). */
    public Subscription findByOrderId(Connection conn, int orderId) throws SQLException {
        return findOne(conn, SELECT_ONE + "WHERE OrderId = ?", orderId);
    }

    /** Đọc và khóa dòng gói trước khi đổi ngày. Khóa khách phải lấy trước (khách → gói). */
    public Subscription findForUpdate(Connection conn, int subscriptionId) throws SQLException {
        return findOne(conn, SELECT_ONE.replace("FROM Subscriptions", "FROM Subscriptions WITH (UPDLOCK, ROWLOCK)")
                + "WHERE SubscriptionId = ?", subscriptionId);
    }

    private Subscription findOne(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Subscription subscription = new Subscription(rs.getInt("SubscriptionId"));
                subscription.setOrderId(rs.getInt("OrderId"));
                subscription.setCustomerId(rs.getInt("CustomerId"));
                subscription.setSubscriptionType(Subscription.Type.fromDbValue(rs.getString("SubscriptionType")));
                subscription.setStartDate(rs.getDate("StartDate").toLocalDate());
                subscription.setEndDate(rs.getDate("EndDate").toLocalDate());
                subscription.setStatus(Subscription.Status.fromDbValue(rs.getString("Status")));
                return subscription;
            }
        }
    }

    /** Ngày hết hạn của gói Membership khác còn Active/Frozen, để gói này không chồng lên (DBR-52). */
    public LocalDate findLastMembershipEndExcluding(Connection conn, int customerId, LocalDate today,
            int excludeSubscriptionId) throws SQLException {
        String sql = "SELECT MAX(EndDate) FROM Subscriptions "
                + "WHERE CustomerId = ? AND SubscriptionType = 'Membership' "
                + "AND Status IN ('Active','Frozen') AND EndDate >= ? AND SubscriptionId <> ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setDate(2, Date.valueOf(today));
            statement.setInt(3, excludeSubscriptionId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                Date end = rs.getDate(1);
                return end == null ? null : end.toLocalDate();
            }
        }
    }

    /** Có gói Membership khác nối tiếp sau gói này không (nếu có thì không được dời ngày, tránh chồng lịch). */
    public boolean existsLaterMembership(Connection conn, int customerId, int subscriptionId, LocalDate startDate)
            throws SQLException {
        String sql = "SELECT 1 FROM Subscriptions WHERE CustomerId = ? AND SubscriptionType = 'Membership' "
                + "AND Status IN ('Active','Frozen') AND SubscriptionId <> ? AND StartDate > ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setInt(2, subscriptionId);
            statement.setDate(3, Date.valueOf(startDate));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Gói đã có lượt check-in thì coi như đã dùng, không được đổi ngày bắt đầu. */
    public boolean hasCheckIn(Connection conn, int subscriptionId) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT 1 FROM CheckIns WHERE SubscriptionId = ?")) {
            statement.setInt(1, subscriptionId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Số lần khách đã đổi ngày của gói này, đếm từ AuditLogs nên không cần thêm cột. */
    public int countStartDateChanges(Connection conn, int subscriptionId) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT COUNT(*) FROM AuditLogs WHERE EntityName = 'Subscriptions' AND EntityId = ? AND Action = ?")) {
            statement.setInt(1, subscriptionId);
            statement.setString(2, ACTION_CHANGE_START);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Ghi ngày bắt đầu và hết hạn mới. Điều kiện nằm trong WHERE: gói Membership còn Active, vẫn đang giữ
     * ngày bắt đầu cũ, chưa có check-in. Trả 0 dòng nghĩa là dữ liệu đã đổi, Service rollback.
     */
    public int updateStartDate(Connection conn, int subscriptionId, LocalDate oldStart, LocalDate newStart,
            LocalDate newEnd) throws SQLException {
        String sql = "UPDATE Subscriptions SET StartDate = ?, EndDate = ? "
                + "WHERE SubscriptionId = ? AND SubscriptionType = 'Membership' AND Status = 'Active' "
                + "AND StartDate = ? "
                + "AND NOT EXISTS (SELECT 1 FROM CheckIns WHERE SubscriptionId = ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(newStart));
            statement.setDate(2, Date.valueOf(newEnd));
            statement.setInt(3, subscriptionId);
            statement.setDate(4, Date.valueOf(oldStart));
            statement.setInt(5, subscriptionId);
            return statement.executeUpdate();
        }
    }
}
