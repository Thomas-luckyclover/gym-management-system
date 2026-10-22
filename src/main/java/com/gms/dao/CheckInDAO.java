package com.gms.dao;

import com.gms.dto.CheckInLogItem;
import com.gms.dto.CustomerSearchItem;
import com.gms.entity.CheckIn;
import com.gms.util.DBContext;
import com.gms.util.SqlUtil;

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
 * Xử lý truy vấn dữ liệu Check-in và gói tập phục vụ nghiệp vụ quầy Lễ tân
 * (UC-19).
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

    /**
     * Ghi một lượt check-in mới dùng Connection có sẵn (dùng trong transaction).
     */
    public int insertCheckIn(Connection conn, CheckIn checkIn) throws SQLException {
        String sql = "INSERT INTO CheckIns (SubscriptionId, VerifiedByEmployeeId, CheckedInAt, PhotoVerified) "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, checkIn.getSubscriptionId());
            stmt.setInt(2, checkIn.getVerifiedByEmployeeId());

            LocalDateTime checkedInAt = checkIn.getCheckedInAt() != null
                    ? checkIn.getCheckedInAt()
                    : LocalDateTime.now();
            stmt.setTimestamp(3, Timestamp.valueOf(checkedInAt)); // Chuyển đổi LocalDateTime sang java.sql.Timestamp
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

    /**
     * Ghi một lượt check-in mới bằng Connection riêng.
     */
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

    /**
     * Đánh dấu vé ngày đã sử dụng, dùng Connection riêng.
     */
    public void markSubscriptionUsed(int subscriptionId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            markSubscriptionUsed(conn, subscriptionId);
        }
    }

    // ===== Tra cứu thông tin hồ sơ cho Check-in =====

    /**
     * Tra cứu Customer theo mã KH hoặc số điện thoại (Lễ tân dùng mã QR / nhập
     * SĐT).
     */
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
                    info.fullName = rs.getString("FullName").trim();
                    info.phone = rs.getString("Phone");
                    info.avatar = rs.getString("Avatar");
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * Kiểm tra Customer có yêu cầu xóa hồ sơ đang chờ duyệt không (chặn check-in
     * nếu đang chờ xóa).
     */
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

    /**
     * Lấy danh sách gói/quyền dùng của 1 Customer, xếp Active trước rồi đến Frozen,
     * các trạng thái khác cuối.
     */
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

    /**
     * Lấy ngày dự kiến kết thúc bảo lưu gần nhất (còn hiệu lực) của 1 Subscription,
     * phục vụ cảnh báo "đang bảo lưu đến ngày X".
     */
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

    /**
     * Kiểm tra vé ngày đã được dùng trong hôm nay chưa (chặn check-in trùng vé 1
     * ngày).
     */
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

    /**
     * Cập nhật ảnh xác minh check-in cho Customer (lưu đường dẫn ảnh mới nhất).
     */
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

    /**
     * Lấy danh sách các lượt check-in trong ngày hôm nay (phục vụ panel "Lễ tân"
     * theo dõi ca làm việc).
     */
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

                    list.add(new CheckInLogItem(checkInId, fullName, customerCode, checkedInAt, packageName,
                            photoVerified));
                }
            }
        }
        return list;
    }

    // ===== Tìm kiếm khách hàng theo phần tên (dùng cho Check-in) =====

    /**
     * Đếm tổng số khách hàng khớp từ khóa tìm kiếm theo tên hoặc 4 số cuối SĐT (phục vụ phân trang).
     *
     * @param keyword từ khóa tìm kiếm (tên hoặc 4 số cuối SĐT)
     * @return tổng số khách hàng khớp
     */
    public long countCustomersByKeyword(String keyword) throws SQLException {
        boolean isPhoneSuffix = keyword != null && keyword.trim().matches("\\d{4}");
        String sql = "SELECT COUNT(*) "
                + "FROM Customers cust "
                + "JOIN Persons p ON cust.PersonId = p.PersonId "
                + "WHERE cust.DeletedAt IS NULL AND "
                + (isPhoneSuffix ? "p.Phone LIKE ?" : "p.FullName COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '\\'");

        String pattern = isPhoneSuffix ? ("%" + keyword.trim()) : SqlUtil.toContainsPattern(keyword);

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pattern);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }

    /**
     * Tìm kiếm khách hàng theo một phần tên hoặc 4 số cuối SĐT có phân trang (OFFSET ? ROWS FETCH NEXT ? ROWS ONLY).
     *
     * @param keyword    từ khóa tìm kiếm (tên hoặc 4 số cuối SĐT)
     * @param pageNumber số trang hiện tại (bắt đầu từ 1)
     * @param pageSize   số lượng dòng mỗi trang
     * @return danh sách khách hàng của trang
     */
    public List<CustomerSearchItem> searchCustomersByKeyword(String keyword, int pageNumber, int pageSize)
            throws SQLException {
        int offset = Math.max(0, (pageNumber - 1) * pageSize);
        boolean isPhoneSuffix = keyword != null && keyword.trim().matches("\\d{4}");
        String sql = "SELECT cust.CustomerId, cust.CustomerCode, p.FullName, p.Phone, "
                + "cust.Status AS CustomerStatus, cust.CheckInPhoto "
                + "FROM Customers cust "
                + "JOIN Persons p ON cust.PersonId = p.PersonId "
                + "WHERE cust.DeletedAt IS NULL AND "
                + (isPhoneSuffix ? "p.Phone LIKE ?" : "p.FullName COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '\\'")
                + " ORDER BY p.FullName, cust.CustomerId "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<CustomerSearchItem> list = new ArrayList<>();
        String pattern = isPhoneSuffix ? ("%" + keyword.trim()) : SqlUtil.toContainsPattern(keyword);

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pattern);
            stmt.setInt(2, offset);
            stmt.setInt(3, pageSize);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CustomerSearchItem item = new CustomerSearchItem();
                    item.setCustomerId(rs.getInt("CustomerId"));
                    item.setCustomerCode(rs.getString("CustomerCode"));
                    item.setFullName(rs.getString("FullName"));
                    item.setPhone(rs.getString("Phone"));
                    item.setCustomerStatus(rs.getString("CustomerStatus"));
                    item.setCheckInPhoto(rs.getString("CheckInPhoto"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    /**
     * Tìm kiếm khách hàng theo một phần tên (mặc định trang 1, tối đa 100 khách).
     *
     * @param keyword từ khóa tìm kiếm
     * @return danh sách CustomerSearchItem khớp
     */
    public List<CustomerSearchItem> searchCustomersByKeyword(String keyword) throws SQLException {
        return searchCustomersByKeyword(keyword, 1, 100);
    }
}
