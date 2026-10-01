package com.gms.dao;

import com.gms.dto.CheckInLogItem;
import com.gms.entity.CheckIn;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý truy vấn dữ liệu Check-in và gói tập phục vụ nghiệp vụ quầy Lễ tân (UC-19).
 */
public class CheckInDAO {

    /**
     * Dữ liệu sơ bộ về Customer tra cứu được.
     */
    public static class CustomerLookupInfo {
        public int customerId;
        public int personId;
        public String customerCode;
        public String checkInPhoto;
        public String customerType;
        public String customerStatus;
        public String fullName;
        public String phone;
        public String avatar;
    }

    /**
     * Thông tin Subcription kèm gói tập của Customer.
     */
    public static class CustomerSubscriptionInfo {
        public int subscriptionId;
        public int orderId;
        public int customerId;
        public String subscriptionType;
        public Integer ptEmployeeId;
        public LocalDate startDate;
        public LocalDate endDate;
        public Integer generatedSessionCount;
        public String status;
        public String packageName;
        public String packageType;
    }

    // ===== Ghi nhận lượt Check-in =====

    public int insertCheckIn(Connection conn, CheckIn checkIn) throws SQLException {
        String sql = "INSERT INTO CheckIns (SubscriptionId, VerifiedByEmployeeId, CheckedInAt, PhotoVerified) "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, checkIn.getSubscriptionId());
            stmt.setInt(2, checkIn.getVerifiedByEmployeeId());

            LocalDateTime checkedInAt = checkIn.getCheckedInAt() != null
                    ? checkIn.getCheckedInAt()
                    : LocalDateTime.now();
            stmt.setTimestamp(3, Timestamp.valueOf(checkedInAt));
            stmt.setBoolean(4, checkIn.isPhotoVerified());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    checkIn.setCheckInId(generatedId);
                    return generatedId;
                }
            }
        }
        throw new SQLException("Thêm CheckIn thất bại, không nhận được CheckInId");
    }

    public int insertCheckIn(CheckIn checkIn) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return insertCheckIn(conn, checkIn);
        }
    }

    /**
     * Đánh dấu vé ngày đã được sử dụng sau khi check-in thành công.
     */
    public void markSubscriptionUsed(Connection conn, int subscriptionId) throws SQLException {
        String sql = "UPDATE Subscriptions SET Status = 'Used' WHERE SubscriptionId = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);
            stmt.executeUpdate();
        }
    }

    public void markSubscriptionUsed(int subscriptionId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            markSubscriptionUsed(conn, subscriptionId);
        }
    }

    // ===== Tra cứu thông tin hồ sơ cho Check-in =====

    public CustomerLookupInfo findCustomerByKeyword(String keyword) throws SQLException {
        String sql = "SELECT cust.CustomerId, cust.PersonId, cust.CustomerCode, cust.CheckInPhoto, "
                + "cust.CustomerType, cust.Status AS CustomerStatus, p.FullName, p.Phone, p.Avatar "
                + "FROM Customers cust "
                + "JOIN Persons p ON cust.PersonId = p.PersonId "
                + "WHERE (UPPER(cust.CustomerCode) = UPPER(?) OR p.Phone = ?) "
                + "  AND cust.DeletedAt IS NULL";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, keyword.trim());
            stmt.setString(2, keyword.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    CustomerLookupInfo info = new CustomerLookupInfo();
                    info.customerId = rs.getInt("CustomerId");
                    info.personId = rs.getInt("PersonId");
                    info.customerCode = rs.getString("CustomerCode");
                    info.checkInPhoto = rs.getString("CheckInPhoto");
                    info.customerType = rs.getString("CustomerType");
                    info.customerStatus = rs.getString("CustomerStatus");
                    info.fullName = rs.getString("FullName");
                    info.phone = rs.getString("Phone");
                    info.avatar = rs.getString("Avatar");
                    return info;
                }
            }
        }
        return null;
    }

    public boolean hasPendingDeletionRequest(int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM CustomerDeletionRequests WHERE CustomerId = ? AND Status = 'Pending'";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public List<CustomerSubscriptionInfo> findSubscriptionsByCustomerId(int customerId) throws SQLException {
        String sql = "SELECT s.SubscriptionId, s.OrderId, s.CustomerId, s.SubscriptionType, s.PtEmployeeId, "
                + "s.StartDate, s.EndDate, s.GeneratedSessionCount, s.Status, "
                + "pkg.PackageName, pkg.PackageType "
                + "FROM Subscriptions s "
                + "JOIN Orders o ON s.OrderId = o.OrderId "
                + "JOIN Packages pkg ON o.PackageId = pkg.PackageId "
                + "WHERE s.CustomerId = ? "
                + "ORDER BY CASE WHEN s.Status = 'Active' THEN 1 WHEN s.Status = 'Frozen' THEN 2 ELSE 3 END, s.EndDate DESC";

        List<CustomerSubscriptionInfo> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CustomerSubscriptionInfo sub = new CustomerSubscriptionInfo();
                    sub.subscriptionId = rs.getInt("SubscriptionId");
                    sub.orderId = rs.getInt("OrderId");
                    sub.customerId = rs.getInt("CustomerId");
                    sub.subscriptionType = rs.getString("SubscriptionType");
                    int ptEmpId = rs.getInt("PtEmployeeId");
                    sub.ptEmployeeId = rs.wasNull() ? null : ptEmpId;

                    Date start = rs.getDate("StartDate");
                    sub.startDate = start != null ? start.toLocalDate() : null;

                    Date end = rs.getDate("EndDate");
                    sub.endDate = end != null ? end.toLocalDate() : null;

                    int sessionCount = rs.getInt("GeneratedSessionCount");
                    sub.generatedSessionCount = rs.wasNull() ? null : sessionCount;

                    sub.status = rs.getString("Status");
                    sub.packageName = rs.getString("PackageName");
                    sub.packageType = rs.getString("PackageType");

                    list.add(sub);
                }
            }
        }
        return list;
    }

    public LocalDate getPlannedFreezeEndDate(int subscriptionId) throws SQLException {
        String sql = "SELECT TOP 1 PlannedEndDate FROM FreezeRecords "
                + "WHERE SubscriptionId = ? AND ActualEndDate IS NULL "
                + "ORDER BY CreatedAt DESC";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Date date = rs.getDate("PlannedEndDate");
                    return date != null ? date.toLocalDate() : null;
                }
            }
        }
        return null;
    }

    public boolean isDayPassUsedToday(int subscriptionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM CheckIns "
                + "WHERE SubscriptionId = ? AND CAST(CheckedInAt AS DATE) = CAST(SYSDATETIME() AS DATE)";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public boolean updateCheckInPhoto(int customerId, String photoPath) throws SQLException {
        String sql = "UPDATE Customers SET CheckInPhoto = ? WHERE CustomerId = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, photoPath);
            stmt.setInt(2, customerId);
            return stmt.executeUpdate() > 0;
        }
    }

    // ===== Lịch sử Check-in ca này / hôm nay =====

    public List<CheckInLogItem> findTodayCheckIns(int limit) throws SQLException {
        String sql = "SELECT TOP (?) c.CheckInId, c.CheckedInAt, p.FullName, cust.CustomerCode, pkg.PackageName, c.PhotoVerified "
                + "FROM CheckIns c "
                + "JOIN Subscriptions s ON c.SubscriptionId = s.SubscriptionId "
                + "JOIN Customers cust ON s.CustomerId = cust.CustomerId "
                + "JOIN Persons p ON cust.PersonId = p.PersonId "
                + "JOIN Orders o ON s.OrderId = o.OrderId "
                + "JOIN Packages pkg ON o.PackageId = pkg.PackageId "
                + "WHERE CAST(c.CheckedInAt AS DATE) = CAST(SYSDATETIME() AS DATE) "
                + "ORDER BY c.CheckedInAt DESC";

        List<CheckInLogItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit > 0 ? limit : 20);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int checkInId = rs.getInt("CheckInId");
                    Timestamp ts = rs.getTimestamp("CheckedInAt");
                    LocalDateTime checkedInAt = ts != null ? ts.toLocalDateTime() : null;
                    String fullName = rs.getString("FullName");
                    String customerCode = rs.getString("CustomerCode");
                    String packageName = rs.getString("PackageName");
                    boolean photoVerified = rs.getBoolean("PhotoVerified");

                    list.add(new CheckInLogItem(checkInId, fullName, customerCode, checkedInAt, packageName, photoVerified));
                }
            }
        }
        return list;
    }
}
