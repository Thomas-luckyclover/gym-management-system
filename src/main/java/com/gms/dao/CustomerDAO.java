package com.gms.dao;

import com.gms.dto.CustomerProfileRowView;
import com.gms.dto.DependentCustomerRow;
import com.gms.entity.Customer;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý truy vấn dữ liệu bảng Customers.
 */
public class CustomerDAO {

    // ===== Thêm mới =====

    /**
     * Thêm mới một Customer trong transaction dùng chung Connection.
     * @param conn Connection tham gia transaction
     * @param customer đối tượng Customer cần chèn
     * @return CustomerId tự sinh trong database
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int insert(Connection conn, Customer customer) throws SQLException {
        String sql = "INSERT INTO Customers (PersonId, CustomerCode, CheckInPhoto, CustomerType, "
                + "GuardianName, GuardianRelationship, GuardianPhone, GuardianPhone2, Status, JoinedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // B1: chuẩn bị PreparedStatement với cờ lấy generated keys
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, customer.getPersonId());
            stmt.setString(2, customer.getCustomerCode());
            stmt.setString(3, customer.getCheckInPhoto());
            stmt.setString(4, customer.getCustomerType());
            stmt.setString(5, customer.getGuardianName());
            stmt.setString(6, customer.getGuardianRelationship());
            stmt.setString(7, customer.getGuardianPhone());
            stmt.setString(8, customer.getGuardianPhone2());
            stmt.setString(9, customer.getStatus());

            Timestamp joinedAtTimestamp = customer.getJoinedAt() != null
                    ? Timestamp.valueOf(customer.getJoinedAt())
                    : new Timestamp(System.currentTimeMillis());
            stmt.setTimestamp(10, joinedAtTimestamp);

            // B2: thực thi câu lệnh SQL
            stmt.executeUpdate();

            // B3: đọc CustomerId tự sinh
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    customer.setCustomerId(generatedId);
                    return generatedId;
                }
            }
        }
        throw new SQLException("Thêm Customer thất bại, không nhận được CustomerId");
    }

    /**
     * Thêm mới một Customer dùng Connection riêng lẻ ngoài transaction.
     * @param customer đối tượng Customer cần chèn
     * @return CustomerId tự sinh trong database
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int insert(Customer customer) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return insert(conn, customer);
        }
    }

    // ===== Truy vấn tìm kiếm =====

    /**
     * Tìm Customer theo CustomerId.
     * @param customerId khóa chính của Customer
     * @return đối tượng Customer hoặc null nếu không tồn tại
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public Customer findById(int customerId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findById(conn, customerId, false);
        }
    }

    /**
     * Tìm Customer theo CustomerId trong transaction, có hỗ trợ khóa dòng (UPDLOCK, ROWLOCK).
     */
    public Customer findById(Connection conn, int customerId, boolean lockRow) throws SQLException {
        String lockHint = lockRow ? " WITH (UPDLOCK, ROWLOCK) " : " ";
        String sql = "SELECT CustomerId, PersonId, CustomerCode, CheckInPhoto, CustomerType, "
                + "GuardianName, GuardianRelationship, GuardianPhone, GuardianPhone2, Status, JoinedAt, DeletedAt "
                + "FROM Customers" + lockHint + "WHERE CustomerId = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCustomer(rs);
                }
            }
        }
        return null;
    }

    // ===== Nghiệp vụ Xóa hồ sơ (BF-07, BR-15, CBR-16, DBR-28) =====

    /**
     * Đếm số gói tập đang chạy của khách hàng (lấy cả người đứng tên và partner của gói đôi - Q1).
     * Gói đang chạy: Status IN ('Active', 'Frozen') và EndDate >= ngày hiện tại.
     *
     * @param conn       Connection tham gia transaction
     * @param customerId mã khách hàng
     * @return số lượng gói đang chạy
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int countActiveSubscriptions(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Subscriptions s "
                + "JOIN Orders o ON o.OrderId = s.OrderId "
                + "WHERE (s.CustomerId = ? OR o.PartnerCustomerId = ?) "
                + "AND s.Status IN ('Active', 'Frozen') "
                + "AND s.EndDate >= CAST(SYSDATETIME() AS DATE)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setInt(2, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Đếm số buổi PT chưa sử dụng (Sessions.Status = 'Scheduled') của khách hoặc partner (Q2).
     *
     * @param conn       Connection tham gia transaction
     * @param customerId mã khách hàng
     * @return số lượng buổi PT Scheduled
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int countScheduledSessions(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Sessions ss "
                + "JOIN Subscriptions s ON s.SubscriptionId = ss.SubscriptionId "
                + "JOIN Orders o ON o.OrderId = s.OrderId "
                + "WHERE (s.CustomerId = ? OR o.PartnerCustomerId = ?) "
                + "AND ss.Status = 'Scheduled'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setInt(2, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Xóa mềm hồ sơ khách hàng: đổi Status = 'Inactive', ghi nhận DeletedAt = SYSDATETIME().
     * Chỉ cập nhật khi hồ sơ chưa Inactive.
     *
     * @param conn       Connection tham gia transaction
     * @param customerId mã khách hàng
     * @return true nếu cập nhật thành công 1 dòng, false nếu khách hàng không tồn tại hoặc đã Inactive
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean softDeleteCustomer(Connection conn, int customerId) throws SQLException {
        String sql = "UPDATE Customers "
                + "SET Status = 'Inactive', DeletedAt = SYSDATETIME() "
                + "WHERE CustomerId = ? AND Status <> 'Inactive'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        }
    }

    /**
     * Lấy danh sách tóm tắt các gói dịch vụ đang hoạt động để hiển thị cảnh báo cho Quản lý (Màn #32).
     */
    public List<ActiveSubscriptionSummary> findActiveSubscriptionsByCustomer(Connection conn, int customerId) throws SQLException {
        List<ActiveSubscriptionSummary> list = new ArrayList<>();
        String sql = "SELECT s.SubscriptionId, COALESCE(o.PackageNameSnapshot, p.PackageName) AS PackageName, p.PackageType, s.StartDate, s.EndDate, s.Status, "
                + "CASE WHEN o.PartnerCustomerId = ? THEN 1 ELSE 0 END AS IsPartner "
                + "FROM Subscriptions s "
                + "JOIN Orders o ON o.OrderId = s.OrderId "
                + "JOIN Packages p ON p.PackageId = o.PackageId "
                + "WHERE (s.CustomerId = ? OR o.PartnerCustomerId = ?) "
                + "AND s.Status IN ('Active', 'Frozen') "
                + "AND s.EndDate >= CAST(SYSDATETIME() AS DATE) "
                + "ORDER BY s.EndDate ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setInt(2, customerId);
            stmt.setInt(3, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new ActiveSubscriptionSummary(
                            rs.getInt("SubscriptionId"),
                            rs.getString("PackageName"),
                            rs.getString("PackageType"),
                            rs.getDate("StartDate").toLocalDate(),
                            rs.getDate("EndDate").toLocalDate(),
                            rs.getString("Status"),
                            rs.getInt("IsPartner") == 1
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Lấy danh sách tóm tắt các buổi PT Scheduled để hiển thị cảnh báo cho Quản lý (Màn #32).
     */
    public List<ScheduledSessionSummary> findScheduledSessionsByCustomer(Connection conn, int customerId) throws SQLException {
        List<ScheduledSessionSummary> list = new ArrayList<>();
        String sql = "SELECT ss.SessionId, ts.SlotDate, ts.StartTime, pp.FullName AS PtName "
                + "FROM Sessions ss "
                + "JOIN Subscriptions s ON s.SubscriptionId = ss.SubscriptionId "
                + "JOIN Orders o ON o.OrderId = s.OrderId "
                + "JOIN TimeSlots ts ON ts.TimeSlotId = ss.TimeSlotId "
                + "JOIN Employees pe ON pe.EmployeeId = ss.PtEmployeeId "
                + "JOIN Persons pp ON pp.PersonId = pe.PersonId "
                + "WHERE (s.CustomerId = ? OR o.PartnerCustomerId = ?) "
                + "AND ss.Status = 'Scheduled' "
                + "ORDER BY ts.SlotDate ASC, ts.StartTime ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setInt(2, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new ScheduledSessionSummary(
                            rs.getInt("SessionId"),
                            rs.getDate("SlotDate").toLocalDate(),
                            rs.getTime("StartTime").toLocalTime(),
                            rs.getString("PtName")
                    ));
                }
            }
        }
        return list;
    }

    public static class ActiveSubscriptionSummary {
        private final int subscriptionId;
        private final String packageName;
        private final String packageType;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final String status;
        private final boolean partner;

        public ActiveSubscriptionSummary(int subscriptionId, String packageName, String packageType,
                                        LocalDate startDate, LocalDate endDate, String status, boolean partner) {
            this.subscriptionId = subscriptionId;
            this.packageName = packageName;
            this.packageType = packageType;
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = status;
            this.partner = partner;
        }

        public int getSubscriptionId() { return subscriptionId; }
        public String getPackageName() { return packageName; }
        public String getPackageType() { return packageType; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public String getStatus() { return status; }
        public boolean isPartner() { return partner; }
    }

    public static class ScheduledSessionSummary {
        private final int sessionId;
        private final LocalDate slotDate;
        private final java.time.LocalTime startTime;
        private final String ptName;

        public ScheduledSessionSummary(int sessionId, LocalDate slotDate, java.time.LocalTime startTime, String ptName) {
            this.sessionId = sessionId;
            this.slotDate = slotDate;
            this.startTime = startTime;
            this.ptName = ptName;
        }

        public int getSessionId() { return sessionId; }
        public LocalDate getSlotDate() { return slotDate; }
        public java.time.LocalTime getStartTime() { return startTime; }
        public String getPtName() { return ptName; }
    }

    /**
     * Tìm Customer theo PersonId.
     * @param personId ID của Person sở hữu hồ sơ khách
     * @return đối tượng Customer hoặc null nếu không tồn tại
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public Customer findByPersonId(int personId) throws SQLException {
        String sql = "SELECT CustomerId, PersonId, CustomerCode, CheckInPhoto, CustomerType, "
                + "GuardianName, GuardianRelationship, GuardianPhone, GuardianPhone2, Status, JoinedAt, DeletedAt "
                + "FROM Customers WHERE PersonId = ? "
                // ưu tiên hồ sơ còn hiệu lực, mới nhất (DBR-65)
                + "ORDER BY CASE WHEN Status = 'Inactive' THEN 1 ELSE 0 END, CustomerId DESC";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCustomer(rs);
                }
            }
        }
        return null;
    }

    // ===== Hồ sơ phụ thuộc (CBR-33) =====

    /**
     * Lấy các hồ sơ Dependent theo SĐT người giám hộ.
     * @param guardianPhone SĐT giám hộ chính hoặc giám hộ thứ 2
     * @return danh sách dòng dữ liệu thô, rỗng nếu không có hồ sơ nào
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public List<DependentCustomerRow> findDependentsByGuardianPhone(String guardianPhone) throws SQLException {
        // B1: dựng SQL Dependent kèm gói tập còn hiệu lực (CBR-33)
        List<DependentCustomerRow> rows = new ArrayList<>();

        String sql = "SELECT c.CustomerCode, p.FullName, p.DateOfBirth, s.PackageName, s.EndDate "
                + "FROM Customers c JOIN Persons p ON p.PersonId = c.PersonId "
                + "OUTER APPLY (SELECT TOP 1 o.PackageNameSnapshot AS PackageName, sub.EndDate "
                + "FROM Subscriptions sub JOIN Orders o ON o.OrderId = sub.OrderId "
                + "WHERE sub.CustomerId = c.CustomerId AND sub.SubscriptionType = 'Membership' "
                + "AND sub.Status IN ('Active', 'Frozen') AND sub.EndDate >= ? "
                + "ORDER BY sub.EndDate DESC) s "
                + "WHERE c.CustomerType = 'Dependent' AND c.Status <> 'Inactive' "
                + "AND (c.GuardianPhone = ? OR c.GuardianPhone2 = ?) "
                + "ORDER BY c.CustomerId";

        // B2: mở kết nối, gán ngày hôm nay và SĐT giám hộ
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(DateTimeUtil.today()));
            stmt.setString(2, guardianPhone);
            stmt.setString(3, guardianPhone);

            // B3: ánh xạ từng dòng sang DependentCustomerRow
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapResultSetToDependentRow(rs));
                }
            }
        }

        // B4: trả danh sách, rỗng nếu không có hồ sơ phụ thuộc
        return rows;
    }

    // ===== Sinh mã khách hàng tự động (DBR-09) =====

    /**
     * Sinh mã khách hàng tiếp theo theo định dạng KH-nnnn.
     * @param conn Connection tham gia transaction
     * @return mã khách hàng mới, ví dụ KH-1001
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public String generateNextCustomerCode(Connection conn) throws SQLException {
        String sql = "SELECT MAX(CustomerId) FROM Customers";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            int nextNumber = 1;
            if (rs.next()) {
                int maxId = rs.getInt(1);
                nextNumber = maxId + 1;
            }
            return String.format("KH-%04d", nextNumber);
        }
    }

    // ===== Hàm hỗ trợ ánh xạ =====

    private DependentCustomerRow mapResultSetToDependentRow(ResultSet rs) throws SQLException {
        LocalDate dateOfBirth = rs.getDate("DateOfBirth").toLocalDate();

        LocalDate packageEndDate = null;
        Date endDate = rs.getDate("EndDate");
        if (endDate != null) {
            packageEndDate = endDate.toLocalDate();
        }

        return new DependentCustomerRow(
                rs.getString("FullName"),
                rs.getString("CustomerCode"),
                dateOfBirth,
                rs.getString("PackageName"),
                packageEndDate);
    }

    private Customer mapResultSetToCustomer(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setCustomerId(rs.getInt("CustomerId"));
        customer.setPersonId(rs.getInt("PersonId"));
        customer.setCustomerCode(rs.getString("CustomerCode"));
        customer.setCheckInPhoto(rs.getString("CheckInPhoto"));
        customer.setCustomerType(rs.getString("CustomerType"));
        customer.setGuardianName(rs.getString("GuardianName"));
        customer.setGuardianRelationship(rs.getString("GuardianRelationship"));
        customer.setGuardianPhone(rs.getString("GuardianPhone"));
        customer.setGuardianPhone2(rs.getString("GuardianPhone2"));
        customer.setStatus(rs.getString("Status"));

        Timestamp joinedAt = rs.getTimestamp("JoinedAt");
        if (joinedAt != null) {
            customer.setJoinedAt(joinedAt.toLocalDateTime());
        }

        Timestamp deletedAt = rs.getTimestamp("DeletedAt");
        if (deletedAt != null) {
            customer.setDeletedAt(deletedAt.toLocalDateTime());
        }

        return customer;
    }

    /**
     * Tìm kiếm và phân trang danh sách hồ sơ khách hàng cho Lễ tân (#28) và Quản lý (#30).
     *
     * @param keyword      từ khóa tìm kiếm (theo tên, SĐT, email, mã KH), có thể null
     * @param statusFilter bộ lọc trạng thái (Active, Inactive, v.v.), có thể null hoặc ALL
     * @param pendingOnly  chỉ lấy các hồ sơ đang có yêu cầu xóa Pending (dành cho Quản lý)
     * @param isManager    true nếu là Quản lý (xem được Inactive); false nếu là Lễ tân (chỉ xem Active, CBR-04)
     * @param offset       vị trí dòng bắt đầu
     * @param limit        số lượng dòng tối đa
     * @return danh sách các dòng hồ sơ khách hàng
     * @throws SQLException khi truy vấn cơ sở dữ liệu gặp lỗi
     */
    public List<CustomerProfileRowView> findCustomerProfiles(
            String keyword,
            String statusFilter,
            Boolean pendingOnly,
            boolean isManager,
            int offset,
            int limit) throws SQLException {

        List<CustomerProfileRowView> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT c.CustomerId, c.CustomerCode, c.PersonId, p.FullName, p.Phone, p.Email,
                   c.CustomerType, c.Status, c.JoinedAt, c.DeletedAt,
                   latestSub.PackageNameSnapshot AS CurrentPackageName,
                   CASE WHEN (activeSubs.SubCount > 0 OR scheduledSessions.SessionCount > 0) THEN 1 ELSE 0 END AS HasActivePackage,
                   CASE WHEN cdr.CustomerDeletionRequestId IS NOT NULL THEN 1 ELSE 0 END AS HasPendingDeletion,
                   cdr.CustomerDeletionRequestId AS PendingRequestId
            FROM Customers c
            JOIN Persons p ON p.PersonId = c.PersonId
            LEFT JOIN CustomerDeletionRequests cdr ON cdr.CustomerId = c.CustomerId AND cdr.Status = 'Pending'
            OUTER APPLY (
                SELECT TOP 1 o.PackageNameSnapshot
                FROM Subscriptions s
                JOIN Orders o ON o.OrderId = s.OrderId
                WHERE (s.CustomerId = c.CustomerId OR o.PartnerCustomerId = c.CustomerId)
                  AND s.Status IN ('Active', 'Frozen')
                  AND s.EndDate >= CAST(GETDATE() AS DATE)
                ORDER BY s.EndDate DESC
            ) latestSub
            OUTER APPLY (
                SELECT COUNT(*) AS SubCount
                FROM Subscriptions s
                LEFT JOIN Orders o ON o.OrderId = s.OrderId
                WHERE (s.CustomerId = c.CustomerId OR o.PartnerCustomerId = c.CustomerId)
                  AND s.Status IN ('Active', 'Frozen')
                  AND s.EndDate >= CAST(GETDATE() AS DATE)
            ) activeSubs
            OUTER APPLY (
                SELECT COUNT(*) AS SessionCount
                FROM Sessions ss
                JOIN Subscriptions s ON s.SubscriptionId = ss.SubscriptionId
                LEFT JOIN Orders o ON o.OrderId = s.OrderId
                WHERE (s.CustomerId = c.CustomerId OR o.PartnerCustomerId = c.CustomerId)
                  AND ss.Status = 'Scheduled'
            ) scheduledSessions
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (!isManager) {
            sql.append(" AND c.Status <> 'Inactive' ");
        }

        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql.append(" AND c.Status = ? ");
            params.add(statusFilter.trim());
        }

        if (Boolean.TRUE.equals(pendingOnly)) {
            sql.append(" AND cdr.CustomerDeletionRequestId IS NOT NULL ");
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.FullName LIKE ? OR p.Phone LIKE ? OR p.Email LIKE ? OR c.CustomerCode LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }

        sql.append(" ORDER BY CASE WHEN cdr.CustomerDeletionRequestId IS NOT NULL THEN 0 ELSE 1 END, c.CustomerId DESC ");
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ");
        params.add(offset);
        params.add(limit);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp joinedAtTs = rs.getTimestamp("JoinedAt");
                    Timestamp deletedAtTs = rs.getTimestamp("DeletedAt");
                    int pendingReqIdVal = rs.getInt("PendingRequestId");
                    Integer pendingRequestId = rs.wasNull() ? null : pendingReqIdVal;

                    list.add(new CustomerProfileRowView(
                            rs.getInt("CustomerId"),
                            rs.getString("CustomerCode"),
                            rs.getInt("PersonId"),
                            rs.getString("FullName"),
                            rs.getString("Phone"),
                            rs.getString("Email"),
                            rs.getString("CustomerType"),
                            rs.getString("Status"),
                            rs.getString("CurrentPackageName"),
                            rs.getInt("HasActivePackage") == 1,
                            rs.getInt("HasPendingDeletion") == 1,
                            pendingRequestId,
                            joinedAtTs != null ? joinedAtTs.toLocalDateTime() : null,
                            deletedAtTs != null ? deletedAtTs.toLocalDateTime() : null
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Đếm tổng số lượng hồ sơ khách hàng thỏa mãn bộ lọc tìm kiếm.
     *
     * @param keyword      từ khóa tìm kiếm (theo tên, SĐT, email, mã KH)
     * @param statusFilter bộ lọc trạng thái (Active, Inactive, v.v.)
     * @param pendingOnly  chỉ lấy các hồ sơ đang có yêu cầu xóa Pending
     * @param isManager    true nếu là Quản lý; false nếu là Lễ tân (CBR-04)
     * @return số lượng hồ sơ thỏa mãn
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int countCustomerProfiles(
            String keyword,
            String statusFilter,
            Boolean pendingOnly,
            boolean isManager) throws SQLException {

        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM Customers c
            JOIN Persons p ON p.PersonId = c.PersonId
            LEFT JOIN CustomerDeletionRequests cdr ON cdr.CustomerId = c.CustomerId AND cdr.Status = 'Pending'
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (!isManager) {
            sql.append(" AND c.Status <> 'Inactive' ");
        }

        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql.append(" AND c.Status = ? ");
            params.add(statusFilter.trim());
        }

        if (Boolean.TRUE.equals(pendingOnly)) {
            sql.append(" AND cdr.CustomerDeletionRequestId IS NOT NULL ");
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.FullName LIKE ? OR p.Phone LIKE ? OR p.Email LIKE ? OR c.CustomerCode LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}