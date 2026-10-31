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
     * Ghi một lượt check-in mới.
     */
    public int insertCheckIn(CheckIn checkIn) throws SQLException {
        String sql = "INSERT INTO CheckIns (SubscriptionId, VerifiedByEmployeeId, CheckedInAt, PhotoVerified) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, checkIn.getSubscriptionId());
            stmt.setInt(2, checkIn.getVerifiedByEmployeeId());
            stmt.setTimestamp(3, Timestamp.valueOf(checkIn.getCheckedInAt())); // Chuyển đổi LocalDateTime sang java.sql.Timestamp
            stmt.setBoolean(4, checkIn.isPhotoVerified());

            return stmt.executeUpdate();
        }
    }

    /**
     * Đánh dấu vé ngày đã được sử dụng sau khi check-in thành công.
     */
    public void markSubscriptionUsed(int subscriptionId) throws SQLException {
        String sql = "UPDATE Subscriptions SET Status = 'Used' WHERE SubscriptionId = ? AND SubscriptionType = 'DayPass'";
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);
            stmt.executeUpdate();
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
                + "WHERE (UPPER(cust.CustomerCode) = UPPER(?) OR p.Phone = ?)"
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
        //count đếm số dòng, luôn trả về 1 dòng hoặc 0 dòng, nên dùng rs.getInt(1) > 0 để kiểm tra có tồn tại hay không
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                    //lấy dữ liệu từ cột đầu tiên (COUNT(*)) và kiểm tra xem có lớn hơn 0 hay không,
                    // nếu có thì có yêu cầu xóa đang chờ duyệt
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
                + "ORDER BY CASE "
                + "  WHEN s.SubscriptionType IN ('Membership', 'DayPass') AND s.Status = 'Active' "
                + "       AND (s.StartDate IS NULL OR s.StartDate <= CAST(SYSDATETIME() AS DATE)) THEN 1 "
                + "  WHEN s.SubscriptionType IN ('Membership', 'DayPass') AND s.Status = 'Frozen' THEN 2 "
                + "  WHEN s.SubscriptionType IN ('Membership', 'DayPass') AND s.Status = 'Active' "
                + "       AND s.StartDate > CAST(SYSDATETIME() AS DATE) THEN 3 "
                + "  WHEN s.SubscriptionType = 'DayPass' AND s.Status = 'Used' THEN 4 "
                + "  WHEN s.SubscriptionType IN ('Membership', 'DayPass') AND s.Status = 'Expired' THEN 5 "
                + "  WHEN s.SubscriptionType NOT IN ('Membership', 'DayPass') AND s.Status = 'Active' THEN 6 "
                + "  ELSE 7 END, s.EndDate DESC";
        //Sắp xếp theo thứ tự: Active trước, Frozen sau, các trạng thái khác cuối cùng.
        // Trong mỗi nhóm, sắp xếp theo ngày kết thúc muộn nhất

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
                    //không phải gói tập PT thì PtEmployeeId sẽ là null,
                    // nên cần kiểm tra rs.wasNull() để tránh gán giá trị mặc định 0.

                    Date start = rs.getDate("StartDate");
                    sub.startDate = start != null ? start.toLocalDate() : null;
                    //kiểm tra null trước khi chuyển đổi sang LocalDate để tránh NullPointerException
                    //Dùng localdate dễ hơn cho việc so sánh ngày trong Java 8+.

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
        //lấy 1
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
        //Kiểm tra xem đã có lượt check-in nào trong ngày hôm nay chưa, nếu có thì vé ngày đã được dùng
        //Cast: cắt bỏ phần giờ
        //Sysdatetime(): lấy ngày giờ hiện tại
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
                + "WHERE CAST(c.CheckedInAt AS DATE) = CAST(SYSDATETIME() AS DATE) "//CAST: cắt bỏ phần giờ, chỉ lấy lượt check-in trong ngày hôm nay
                + "ORDER BY c.CheckedInAt DESC"; //xếp theo thời gian check-in mới nhất trước, để hiển thị trên cùng

        List<CheckInLogItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit > 0 ? limit : 20);//ngăn ngừa truyền top 0 hoặc âm, mặc định 20 nếu limit <= 0

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
        //COLLATE Latin1_General_100_CI_AI LIKE không phân biệt chữ hoa/chữ thường,
        // không phân biệt dấu tiếng Việt
        //ESCAPE '\\' cho phép sử dụng ký tự thoát trong pattern, ví dụ: nếu keyword chứa ký tự '%' hoặc '_',
        // chúng sẽ được coi là ký tự bình thường thay vì ký tự đại diện trong LIKE.
        //Nếu không có thì hệ thống sẽ gặp 3 lỗi sau:
        //Sập trang (Crash web): Người dùng chỉ cần gõ một dấu ngoặc vuông [ vào ô tìm kiếm là SQL Server báo lỗi cú pháp -> Web bị văng lỗi 500 ngay lập tức.
        //Tìm sai người: Người dùng tìm mã có dấu gạch dưới (vd: KH_01) thì SQL sẽ hiểu _ là ký tự bất kỳ -> Trả về lung tung cả KHA01, KHB01... (lẫn lộn hồ sơ khách).
        //Treo máy chủ (Lag database): Người dùng gõ một dấu % -> SQL sẽ load toàn bộ hàng chục nghìn khách hàng ra ngoài -> Gây đơ và nghẽn hệ thống.

        String pattern = isPhoneSuffix ? ("%" + keyword.trim()) : SqlUtil.toContainsPattern(keyword);
        //Nếu là SĐT: Thêm % vào đằng trước -> "%5678" (số điện thoại có đuôi là 5678).
        //Nếu là Họ tên: SqlUtil.toContainsPattern(keyword) sẽ tạo ra "%tên%" (tên chứa từ khóa đó) và tự động xử lý an toàn các ký tự đặc biệt.

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
        int offset = Math.max(0, (pageNumber - 1) * pageSize); //số người bỏ qua, hàm Math.max đảm bảo không âm
        boolean isPhoneSuffix = keyword != null && keyword.trim().matches("\\d{4}");
        String sql = "SELECT cust.CustomerId, cust.CustomerCode, p.FullName, p.Phone, "
                + "cust.Status AS CustomerStatus, cust.CheckInPhoto "
                + "FROM Customers cust "
                + "JOIN Persons p ON cust.PersonId = p.PersonId "
                + "WHERE cust.DeletedAt IS NULL AND "
                + (isPhoneSuffix ? "p.Phone LIKE ?" : "p.FullName COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '\\'")
                + " ORDER BY p.FullName, cust.CustomerId "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        //khi ở trang 1, offset = 0 -> bỏ qua 0 dòng, lấy từ 1 đến 10
        //khi ở trang 2, offset = 10 -> bỏ qua 10 dòng, lấy từ 11 đến 20

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
