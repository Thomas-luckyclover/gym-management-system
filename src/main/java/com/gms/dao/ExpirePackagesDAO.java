package com.gms.dao;

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
 * JOB-02 — hết hạn gói tập và gói PT (SRS 16.2, CBR-03, CBR-55, DBR-36, DBR-37). Chỉ chạy SQL; ExpirePackagesService gọi.
 *
 * Bảng: Subscriptions (R/U), Sessions (U), TimeSlots (U), Customers (U).
 */
public class ExpirePackagesDAO {

    /** Gói cần xét (đã khóa): id, khách, loại gói, ngày hết hạn. */
    public record ExpiringPackage(int subscriptionId, int customerId, String subscriptionType, LocalDate endDate) {
    }

    // B1 (Service): Id các gói Membership / PTSingle / PTCouple còn Active mà EndDate < hôm nay (EndDate = ngày cuối còn dùng, GBR-03).
    public List<Integer> findExpiredIds(Connection connection, LocalDate today) throws SQLException {
        String sql = """
                SELECT SubscriptionId FROM Subscriptions
                WHERE SubscriptionType IN ('Membership', 'PTSingle', 'PTCouple')
                  AND Status = 'Active' AND EndDate < ?
                ORDER BY SubscriptionId
                """;
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(today));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("SubscriptionId"));
                }
            }
        }
        return ids;
    }

    // B2 (Service): KHÓA 1 gói và kiểm lại còn Active + đã qua EndDate. null = đã có người/lượt khác xử lý.
    public ExpiringPackage lockExpired(Connection connection, int subscriptionId, LocalDate today) throws SQLException {
        String sql = """
                SELECT SubscriptionId, CustomerId, SubscriptionType, EndDate
                FROM Subscriptions WITH (UPDLOCK, HOLDLOCK)
                WHERE SubscriptionId = ? AND Status = 'Active' AND EndDate < ?
                  AND SubscriptionType IN ('Membership', 'PTSingle', 'PTCouple')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, subscriptionId);
            statement.setDate(2, Date.valueOf(today));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ExpiringPackage(rs.getInt("SubscriptionId"), rs.getInt("CustomerId"),
                        rs.getString("SubscriptionType"), rs.getDate("EndDate").toLocalDate());
            }
        }
    }

    // B3/B4 (Service): Subscriptions Active → Expired.
    public boolean expire(Connection connection, int subscriptionId) throws SQLException {
        String sql = "UPDATE Subscriptions SET Status = 'Expired' WHERE SubscriptionId = ? AND Status = 'Active'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, subscriptionId);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * B4 (Service): gói PT — buổi còn Scheduled NẰM SAU ngày hết hạn → Cancelled / PackageExpired (CBR-55), khung → Free (DBR-36).
     * Buổi trong hạn (ngày tập ≤ EndDate) KHÔNG đụng: JOB-04 chạy ngay sau sẽ chốt theo check-in.
     * (Chạy JOB-02 trước JOB-04 — GBR-14 — nếu hủy cả buổi ngày cuối thì khách đã tập vẫn bị ghi hủy.)
     * Trả về: số buổi đã hủy.
     */
    public int cancelSessionsAfterEnd(Connection connection, int subscriptionId, LocalDate endDate, LocalDateTime now)
            throws SQLException {
        // Câu 1: khung của các buổi đó → Free (khung Booked của chính buổi này, chưa ai khác dùng)
        String freeSql = """
                UPDATE TimeSlots SET Status = 'Free'
                WHERE Status = 'Booked' AND SlotDate > ? AND TimeSlotId IN (
                    SELECT s.TimeSlotId FROM Sessions s
                    WHERE s.SubscriptionId = ? AND s.Status = 'Scheduled')
                """;
        try (PreparedStatement statement = connection.prepareStatement(freeSql)) {
            statement.setDate(1, Date.valueOf(endDate));
            statement.setInt(2, subscriptionId);
            statement.executeUpdate();
        }
        // Câu 2: buổi → Cancelled / PackageExpired (CK_Sessions_SystemCancel: không người hủy, không hoàn tiền)
        String cancelSql = """
                UPDATE Sessions
                SET Status = 'Cancelled', CancelType = 'PackageExpired', CancelledAt = ?,
                    CancelReason = N'Gói PT đã hết hạn'
                WHERE SubscriptionId = ? AND Status = 'Scheduled'
                  AND TimeSlotId IN (SELECT TimeSlotId FROM TimeSlots WHERE SlotDate > ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(cancelSql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now.withNano(0)));
            statement.setInt(2, subscriptionId);
            statement.setDate(3, Date.valueOf(endDate));
            return statement.executeUpdate();
        }
    }

    /**
     * B5 (Service): khách không còn gói thành viên Active/Frozen và không còn vé ngày dùng được → Customers.Status = Expired (CBR-03).
     * Gói PT không giữ khách Active (CBR-58). Hồ sơ Inactive / đã Expired thì thôi. Trả về: true = đã chuyển Expired.
     */
    public boolean expireCustomerIfNoAccess(Connection connection, int customerId, LocalDate today) throws SQLException {
        String sql = """
                UPDATE Customers SET Status = 'Expired'
                WHERE CustomerId = ? AND Status NOT IN ('Inactive', 'Expired')
                  AND NOT EXISTS (SELECT 1 FROM Subscriptions m
                                  WHERE m.CustomerId = Customers.CustomerId AND m.SubscriptionType = 'Membership'
                                    AND m.Status IN ('Active', 'Frozen'))
                  AND NOT EXISTS (SELECT 1 FROM Subscriptions d
                                  WHERE d.CustomerId = Customers.CustomerId AND d.SubscriptionType = 'DayPass'
                                    AND d.Status = 'Active' AND d.EndDate >= ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setDate(2, Date.valueOf(today));
            return statement.executeUpdate() == 1;
        }
    }
}
