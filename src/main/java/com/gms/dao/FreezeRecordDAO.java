package com.gms.dao;

import com.gms.dto.CustomerMembershipRow;
import com.gms.dto.FreezeRecordItemView;
import com.gms.dto.FreezeSessionImpactView;
import com.gms.entity.FreezeRecord;
import com.gms.entity.Subscription;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object phụ trách toàn bộ truy vấn liên quan đến bảo lưu gói tập (FreezeRecords),
 * chuyển đổi trạng thái gói tập và các buổi tập PT chịu ảnh hưởng (UC-18, BF-09, JOB-01).
 *
 * <p>Tuân thủ nghiêm ngặt nguyên tắc:
 * <ul>
 *   <li>Chỉ chứa SQL thuần túy, không chứa nghiệp vụ hay ValidationUtil.</li>
 *   <li>Mọi từ khóa SQL viết hoa, xuống dòng rõ ràng, dùng PreparedStatement.</li>
 *   <li>Các thao tác trong transaction nhận {@link Connection} từ Service.</li>
 *   <li>Phương thức ánh xạ ResultSet đặt ở cuối class.</li>
 * </ul>
 * </p>
 */
public class FreezeRecordDAO {

    // ===== 1. Thao tác trên bảng FreezeRecords =====

    /**
     * Thêm một bản ghi bảo lưu mới vào FreezeRecords trong transaction.
     *
     * @param conn kết nối CSDL trong transaction
     * @param record bản ghi bảo lưu cần lưu
     * @return mã FreezeRecordId tự tăng vừa được sinh
     * @throws SQLException khi có lỗi truy vấn CSDL
     */
    public int insert(Connection conn, FreezeRecord record) throws SQLException {
        String sql = """
                INSERT INTO FreezeRecords (
                    SubscriptionId,
                    HandledByEmployeeId,
                    StartDate,
                    PlannedEndDate,
                    ActualEndDate,
                    Reason,
                    CreatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, record.getSubscriptionId());
            stmt.setInt(2, record.getHandledByEmployeeId());
            stmt.setDate(3, Date.valueOf(record.getStartDate()));
            stmt.setDate(4, Date.valueOf(record.getPlannedEndDate()));

            if (record.getActualEndDate() != null) {
                stmt.setDate(5, Date.valueOf(record.getActualEndDate()));
            } else {
                stmt.setNull(5, java.sql.Types.DATE);
            }

            stmt.setString(6, record.getReason());
            Timestamp created = record.getCreatedAt() != null
                    ? Timestamp.valueOf(record.getCreatedAt())
                    : new Timestamp(System.currentTimeMillis());
            stmt.setTimestamp(7, created);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    record.setFreezeRecordId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Thêm FreezeRecord thất bại, không nhận được FreezeRecordId.");
    }

    /**
     * Tra cứu một bản ghi bảo lưu theo ID trong transaction (có khóa UPDLOCK).
     */
    public FreezeRecord findById(Connection conn, int freezeRecordId) throws SQLException {
        String sql = """
                SELECT
                    FreezeRecordId,
                    SubscriptionId,
                    HandledByEmployeeId,
                    StartDate,
                    PlannedEndDate,
                    ActualEndDate,
                    Reason,
                    CreatedAt
                FROM FreezeRecords WITH (UPDLOCK, HOLDLOCK)
                WHERE FreezeRecordId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, freezeRecordId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToFreezeRecord(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tra cứu một bản ghi bảo lưu theo ID bằng kết nối riêng (chỉ đọc).
     */
    public FreezeRecord findById(int freezeRecordId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            String sql = """
                    SELECT
                        FreezeRecordId,
                        SubscriptionId,
                        HandledByEmployeeId,
                        StartDate,
                        PlannedEndDate,
                        ActualEndDate,
                        Reason,
                        CreatedAt
                    FROM FreezeRecords
                    WHERE FreezeRecordId = ?
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, freezeRecordId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToFreezeRecord(rs);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Tìm đợt bảo lưu đang mở (ActualEndDate IS NULL) của một Subscription trong transaction.
     */
    public FreezeRecord findOpenFreezeBySubscriptionId(Connection conn, int subscriptionId) throws SQLException {
        String sql = """
                SELECT
                    FreezeRecordId,
                    SubscriptionId,
                    HandledByEmployeeId,
                    StartDate,
                    PlannedEndDate,
                    ActualEndDate,
                    Reason,
                    CreatedAt
                FROM FreezeRecords WITH (UPDLOCK, HOLDLOCK)
                WHERE SubscriptionId = ?
                  AND ActualEndDate IS NULL
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToFreezeRecord(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tìm đợt bảo lưu đang mở của một Subscription bằng kết nối riêng.
     */
    public FreezeRecord findOpenFreezeBySubscriptionId(int subscriptionId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            String sql = """
                    SELECT
                        FreezeRecordId,
                        SubscriptionId,
                        HandledByEmployeeId,
                        StartDate,
                        PlannedEndDate,
                        ActualEndDate,
                        Reason,
                        CreatedAt
                    FROM FreezeRecords
                    WHERE SubscriptionId = ?
                      AND ActualEndDate IS NULL
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, subscriptionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToFreezeRecord(rs);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Cập nhật ngày kết thúc thực tế (ActualEndDate) cho một bản ghi bảo lưu khi hủy sớm hoặc hoàn tất.
     */
    public boolean updateActualEndDate(Connection conn, int freezeRecordId, LocalDate actualEndDate) throws SQLException {
        String sql = """
                UPDATE FreezeRecords
                SET ActualEndDate = ?
                WHERE FreezeRecordId = ?
                  AND ActualEndDate IS NULL
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(actualEndDate));
            stmt.setInt(2, freezeRecordId);
            return stmt.executeUpdate() == 1;
        }
    }

    /**
     * Tìm các đợt bảo lưu đã quá hạn dự kiến (PlannedEndDate < today) nhưng chưa được chốt (JOB-01).
     */
    public List<FreezeRecord> findExpiredOpenFreezes(LocalDate today) throws SQLException {
        String sql = """
                SELECT
                    FreezeRecordId,
                    SubscriptionId,
                    HandledByEmployeeId,
                    StartDate,
                    PlannedEndDate,
                    ActualEndDate,
                    Reason,
                    CreatedAt
                FROM FreezeRecords
                WHERE ActualEndDate IS NULL
                  AND PlannedEndDate < ?
                ORDER BY PlannedEndDate ASC
                """;

        List<FreezeRecord> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(today));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToFreezeRecord(rs));
                }
            }
        }
        return list;
    }

    // ===== 2. Thao tác trên Subscriptions & Customers =====

    /**
     * Đọc thông tin gói tập Membership theo SubscriptionId trong transaction (UPDLOCK).
     */
    public Subscription findMembershipSubscription(Connection conn, int subscriptionId) throws SQLException {
        String sql = """
                SELECT
                    SubscriptionId,
                    OrderId,
                    CustomerId,
                    SubscriptionType,
                    PtEmployeeId,
                    StartDate,
                    EndDate,
                    FixedStartTime,
                    GeneratedSessionCount,
                    SwapsUsed,
                    MaxSwapsSnapshot,
                    FreezeUsed,
                    MaxFreezesSnapshot,
                    Status
                FROM Subscriptions WITH (UPDLOCK, HOLDLOCK)
                WHERE SubscriptionId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSubscription(rs);
                }
            }
        }
        return null;
    }

    /**
     * Lấy gói Membership đang hoạt động (Active) hoặc đang bảo lưu (Frozen) của một khách hàng.
     */
    public Subscription findActiveOrFrozenMembershipByCustomerId(Connection conn, int customerId) throws SQLException {
        String sql = """
                SELECT TOP 1
                    s.SubscriptionId,
                    s.OrderId,
                    s.CustomerId,
                    s.SubscriptionType,
                    s.PtEmployeeId,
                    s.StartDate,
                    s.EndDate,
                    s.FixedStartTime,
                    s.GeneratedSessionCount,
                    s.SwapsUsed,
                    s.MaxSwapsSnapshot,
                    s.FreezeUsed,
                    s.MaxFreezesSnapshot,
                    s.Status
                FROM Subscriptions s WITH (UPDLOCK, HOLDLOCK)
                JOIN Orders o ON o.OrderId = s.OrderId
                JOIN Packages pkg ON pkg.PackageId = o.PackageId
                WHERE s.CustomerId = ?
                  AND s.SubscriptionType = 'Membership'
                  AND pkg.PackageType = 'Membership'
                  AND o.Status != 'Cancelled'
                  AND s.Status IN ('Active', 'Frozen')
                ORDER BY CASE WHEN s.Status = 'Frozen' THEN 1 WHEN s.Status = 'Active' THEN 2 ELSE 3 END,
                         s.EndDate DESC
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSubscription(rs);
                }
            }
        }
        return null;
    }

    /**
     * Lấy gói Membership đang hoạt động hoặc đang bảo lưu bằng kết nối riêng.
     */
    public Subscription findActiveOrFrozenMembershipByCustomerId(int customerId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findActiveOrFrozenMembershipByCustomerId(conn, customerId);
        }
    }

    /**
     * Cập nhật trạng thái gói tập và tăng số lần bảo lưu đã dùng (khi tạo bảo lưu).
     */
    public boolean updateSubscriptionStatusAndFreezeUsed(Connection conn, int subscriptionId,
                                                        String status, int newFreezeUsed) throws SQLException {
        String sql = """
                UPDATE Subscriptions
                SET Status = ?,
                    FreezeUsed = ?
                WHERE SubscriptionId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, newFreezeUsed);
            stmt.setInt(3, subscriptionId);
            return stmt.executeUpdate() == 1;
        }
    }

    /**
     * Cập nhật mở lại gói tập và cộng thêm số ngày bảo lưu vào EndDate (khi hủy sớm hoặc JOB-01 hoàn tất).
     */
    public boolean updateSubscriptionResume(Connection conn, int subscriptionId,
                                           String status, LocalDate newEndDate) throws SQLException {
        String sql = """
                UPDATE Subscriptions
                SET Status = ?,
                    EndDate = ?
                WHERE SubscriptionId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setDate(2, Date.valueOf(newEndDate));
            stmt.setInt(3, subscriptionId);
            return stmt.executeUpdate() == 1;
        }
    }

    /**
     * Cập nhật trạng thái hồ sơ khách hàng (Customers.Status = 'Frozen' hoặc 'Active') (BR-20, BR-25, BR-26).
     */
    public boolean updateCustomerStatus(Connection conn, int customerId, String status) throws SQLException {
        String sql = """
                UPDATE Customers
                SET Status = ?
                WHERE CustomerId = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, customerId);
            return stmt.executeUpdate() == 1;
        }
    }

    // ===== 3. Buổi tập PT chịu ảnh hưởng (CBR-62, CBR-67, CBR-68, DBR-36) =====

    /**
     * Tìm tất cả các buổi Scheduled của khách hàng trong khoảng thời gian [startDate, plannedEndDate]
     * để tính toán và cảnh báo các buổi sẽ bị chuyển sang Missed (MembershipFrozen).
     *
     * <p>Xử lý gói đôi (PTCouple) theo CBR-67: buổi chỉ bị Missed nếu partner cũng không có thẻ tập hợp lệ.</p>
     * <p>Sử dụng kết nối đang mở để an toàn trong transaction (tránh deadlock tự khóa khi bảng Subscriptions/Customers bị X-Lock).</p>
     */
    public List<FreezeSessionImpactView> findScheduledSessionsInPeriod(
            Connection conn, int customerId, LocalDate startDate, LocalDate plannedEndDate) throws SQLException {

        String sql = """
                SELECT
                    s.SessionId,
                    t.SlotDate,
                    t.StartTime,
                    t.EndTime,
                    ptP.FullName AS PtName,
                    sub.SubscriptionType,
                    o.PartnerCustomerId,
                    partnerP.FullName AS PartnerName,
                    CASE
                        WHEN sub.SubscriptionType IN ('PTSingle', 'SingleSession') THEN 1
                        WHEN sub.SubscriptionType = 'PTCouple' THEN
                            CASE WHEN EXISTS (
                                SELECT 1
                                FROM Subscriptions partnerSub
                                WHERE partnerSub.CustomerId = o.PartnerCustomerId
                                  AND partnerSub.SubscriptionType = 'Membership'
                                  AND partnerSub.Status = 'Active'
                                  AND partnerSub.StartDate <= t.SlotDate
                                  AND partnerSub.EndDate >= t.SlotDate
                                  AND NOT EXISTS (
                                      SELECT 1
                                      FROM FreezeRecords f
                                      WHERE f.SubscriptionId = partnerSub.SubscriptionId
                                        AND f.StartDate <= t.SlotDate
                                        AND t.SlotDate <= COALESCE(f.ActualEndDate, f.PlannedEndDate)
                                  )
                            ) THEN 0 ELSE 1 END
                        ELSE 1
                    END AS WillBeMissed
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o ON o.OrderId = sub.OrderId
                JOIN Employees ptEmp ON ptEmp.EmployeeId = s.PtEmployeeId
                JOIN Persons ptP ON ptP.PersonId = ptEmp.PersonId
                LEFT JOIN Customers partnerCust ON partnerCust.CustomerId = o.PartnerCustomerId
                LEFT JOIN Persons partnerP ON partnerP.PersonId = partnerCust.PersonId
                WHERE (sub.CustomerId = ? OR o.PartnerCustomerId = ?)
                  AND s.Status = 'Scheduled'
                  AND t.SlotDate >= ?
                  AND t.SlotDate <= ?
                ORDER BY t.SlotDate ASC, t.StartTime ASC
                """;

        List<FreezeSessionImpactView> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setInt(2, customerId);
            stmt.setDate(3, Date.valueOf(startDate));
            stmt.setDate(4, Date.valueOf(plannedEndDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FreezeSessionImpactView item = new FreezeSessionImpactView();
                    item.setSessionId(rs.getInt("SessionId"));
                    item.setSlotDate(rs.getDate("SlotDate").toLocalDate());
                    item.setStartTime(rs.getTime("StartTime").toLocalTime());
                    item.setEndTime(rs.getTime("EndTime").toLocalTime());
                    item.setPtName(rs.getString("PtName"));
                    item.setPackageType(rs.getString("SubscriptionType"));

                    String partner = rs.getString("PartnerName");
                    item.setPartnerName(partner != null ? partner.trim() : null);

                    boolean willMiss = rs.getInt("WillBeMissed") == 1;
                    item.setWillBeMissed(willMiss);

                    if (willMiss) {
                        item.setImpactExplanation("Chuyển sang Vắng (Bảo lưu gói tập) — trừ buổi, không hoàn tiền (CBR-62)");
                    } else {
                        item.setImpactExplanation("Vẫn giữ lịch tập — bạn tập vẫn còn thẻ tập hợp lệ (CBR-67)");
                    }

                    list.add(item);
                }
            }
        }
        return list;
    }

    public List<FreezeSessionImpactView> findScheduledSessionsInPeriod(
            int customerId, LocalDate startDate, LocalDate plannedEndDate) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findScheduledSessionsInPeriod(conn, customerId, startDate, plannedEndDate);
        }
    }

    /**
     * Chuyển các buổi PT sang trạng thái Missed (MembershipFrozen) và nhả slot về Free (CBR-62, CBR-68, DBR-36).
     */
    public void markSessionsMissedDueToFreeze(Connection conn, List<Integer> sessionIds) throws SQLException {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }

        String updateSessionSql = """
                UPDATE Sessions
                SET Status = 'Missed',
                    MissedReason = 'MembershipFrozen',
                    AttendanceMarkedAt = SYSDATETIME()
                WHERE SessionId = ?
                  AND Status = 'Scheduled'
                """;

        String freeSlotSql = """
                UPDATE t
                SET Status = 'Free'
                FROM TimeSlots t
                JOIN Sessions s ON s.TimeSlotId = t.TimeSlotId
                WHERE s.SessionId = ?
                """;

        try (PreparedStatement stmtSession = conn.prepareStatement(updateSessionSql);
             PreparedStatement stmtSlot = conn.prepareStatement(freeSlotSql)) {

            for (Integer sid : sessionIds) {
                stmtSession.setInt(1, sid);
                stmtSession.addBatch();

                stmtSlot.setInt(1, sid);
                stmtSlot.addBatch();
            }

            stmtSession.executeBatch();
            stmtSlot.executeBatch();
        }
    }

    // ===== 4. Lịch sử và danh sách bảo lưu hiển thị trên giao diện =====

    /**
     * Lấy danh sách các lượt bảo lưu mới nhất của hệ thống phục vụ hiển thị trên màn hình Lễ tân.
     */
    public List<FreezeRecordItemView> findRecentFreezes(int limit) throws SQLException {
        String sql = """
                SELECT TOP (?)
                    fr.FreezeRecordId,
                    fr.SubscriptionId,
                    c.CustomerId,
                    c.CustomerCode,
                    p.FullName AS CustomerName,
                    p.Phone AS CustomerPhone,
                    pkg.PackageName,
                    fr.StartDate,
                    fr.PlannedEndDate,
                    fr.ActualEndDate,
                    fr.Reason,
                    empP.FullName AS HandledByEmployeeName,
                    emp.EmployeeCode AS HandledByEmployeeCode,
                    fr.CreatedAt
                FROM FreezeRecords fr
                JOIN Subscriptions s ON s.SubscriptionId = fr.SubscriptionId
                JOIN Customers c ON c.CustomerId = s.CustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                JOIN Orders o ON o.OrderId = s.OrderId
                JOIN Packages pkg ON pkg.PackageId = o.PackageId
                JOIN Employees emp ON emp.EmployeeId = fr.HandledByEmployeeId
                JOIN Persons empP ON empP.PersonId = emp.PersonId
                ORDER BY fr.CreatedAt DESC
                """;

        List<FreezeRecordItemView> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit > 0 ? limit : 20);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToItemView(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy toàn bộ lịch sử các lần bảo lưu của một khách hàng cụ thể.
     */
    public List<FreezeRecordItemView> findFreezesByCustomerId(int customerId) throws SQLException {
        String sql = """
                SELECT
                    fr.FreezeRecordId,
                    fr.SubscriptionId,
                    c.CustomerId,
                    c.CustomerCode,
                    p.FullName AS CustomerName,
                    p.Phone AS CustomerPhone,
                    pkg.PackageName,
                    fr.StartDate,
                    fr.PlannedEndDate,
                    fr.ActualEndDate,
                    fr.Reason,
                    empP.FullName AS HandledByEmployeeName,
                    emp.EmployeeCode AS HandledByEmployeeCode,
                    fr.CreatedAt
                FROM FreezeRecords fr
                JOIN Subscriptions s ON s.SubscriptionId = fr.SubscriptionId
                JOIN Customers c ON c.CustomerId = s.CustomerId
                JOIN Persons p ON p.PersonId = c.PersonId
                JOIN Orders o ON o.OrderId = s.OrderId
                JOIN Packages pkg ON pkg.PackageId = o.PackageId
                JOIN Employees emp ON emp.EmployeeId = fr.HandledByEmployeeId
                JOIN Persons empP ON empP.PersonId = emp.PersonId
                WHERE c.CustomerId = ?
                ORDER BY fr.CreatedAt DESC
                """;

        List<FreezeRecordItemView> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToItemView(rs));
                }
            }
        }
        return list;
    }

    // ===== Search Helper =====

    private static class SearchCondition {
        final String sqlCondition;
        final List<String> params;

        SearchCondition(String sqlCondition, List<String> params) {
            this.sqlCondition = sqlCondition;
            this.params = params;
        }
    }

    private SearchCondition buildSearchCondition(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String trimmed = keyword.trim();
        String upper = trimmed.toUpperCase();
        boolean isNumeric = trimmed.matches("\\d+");
        boolean startsWithKh = upper.startsWith("KH");

        List<String> params = new ArrayList<>();
        String namePattern = SqlUtil.toContainsPattern(trimmed);

        if (startsWithKh) {
            // Khách nhập mã hội viên (ví dụ KH-1001, KH-20,...) hoặc tên bắt đầu bằng Kh
            String sql = "(p.FullName COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '\\' OR c.CustomerCode LIKE ?)";
            params.add(namePattern);
            params.add("%" + trimmed + "%");
            return new SearchCondition(sql, params);
        } else if (isNumeric) {
            // Khách nhập số điện thoại hoặc đuôi số mã hội viên (ví dụ 0901234504, 2038,...)
            String sql = "(p.FullName COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '\\' OR c.CustomerCode LIKE ? OR p.Phone LIKE ?)";
            params.add(namePattern);
            params.add("%" + trimmed + "%");
            params.add("%" + trimmed + "%");
            return new SearchCondition(sql, params);
        } else {
            // Khách nhập chữ cái/tên thông thường (ví dụ 'h', 'an', 'long', 'hồng',...)
            // TUYỆT ĐỐI KHÔNG tìm kiếm 'c.CustomerCode LIKE %h%' vì mã khách hàng luôn bắt đầu bằng 'KH-',
            // chứa chữ 'H' và 'K', dẫn đến LIKE '%h%' khớp 100% tất cả khách hàng trong phòng gym.
            String sql = "(p.FullName COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '\\')";
            params.add(namePattern);
            return new SearchCondition(sql, params);
        }
    }

    private String buildFilterCondition(String filter) {
        if (filter == null || filter.trim().isEmpty() || "ALL".equalsIgnoreCase(filter.trim())) {
            return "";
        }
        String upper = filter.trim().toUpperCase();
        if ("FROZEN".equals(upper)) {
            return " AND openFr.FreezeRecordId IS NOT NULL ";
        } else if ("ELIGIBLE".equals(upper)) {
            return " AND s.SubscriptionId IS NOT NULL AND s.Status = 'Active' AND openFr.FreezeRecordId IS NULL AND COALESCE(s.FreezeUsed, 0) < COALESCE(s.MaxFreezesSnapshot, 1) AND s.EndDate >= CAST(SYSDATETIME() AS DATE) ";
        } else if ("ACTIVE".equals(upper)) {
            return " AND s.SubscriptionId IS NOT NULL AND s.Status = 'Active' ";
        }
        return "";
    }

    /**
     * Đếm tổng số khách hàng khớp từ khóa và bộ lọc trạng thái phục vụ phân trang lưới quản lý hội viên.
     */
    public long countCustomerMemberships(String keyword, String filter) throws SQLException {
        SearchCondition cond = buildSearchCondition(keyword);
        String filterCond = buildFilterCondition(filter);
        String sql = """
                SELECT COUNT(*)
                FROM Customers c
                JOIN Persons p ON p.PersonId = c.PersonId
                OUTER APPLY (
                    SELECT TOP 1 sub.*, o.PackageId
                    FROM Subscriptions sub
                    JOIN Orders o ON o.OrderId = sub.OrderId
                    JOIN Packages pkg ON pkg.PackageId = o.PackageId
                    WHERE sub.CustomerId = c.CustomerId 
                      AND sub.SubscriptionType = 'Membership'
                      AND pkg.PackageType = 'Membership'
                      AND o.Status != 'Cancelled'
                    ORDER BY 
                        CASE sub.Status WHEN 'Frozen' THEN 1 WHEN 'Active' THEN 2 ELSE 3 END,
                        sub.EndDate DESC
                ) s
                LEFT JOIN FreezeRecords openFr ON openFr.SubscriptionId = s.SubscriptionId AND openFr.ActualEndDate IS NULL
                WHERE c.DeletedAt IS NULL
                """
                + (cond != null ? " AND " + cond.sqlCondition + " " : "")
                + filterCond;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (cond != null) {
                for (int i = 0; i < cond.params.size(); i++) {
                    stmt.setString(i + 1, cond.params.get(i));
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }

    public long countCustomerMemberships(String keyword) throws SQLException {
        return countCustomerMemberships(keyword, "ALL");
    }

    /**
     * Lấy danh sách hội viên kèm gói tập Membership tương ứng phục vụ hiển thị lưới GymMaster.
     */
    public List<CustomerMembershipRow> findCustomerMemberships(String keyword, String filter, int pageNumber, int pageSize)
            throws SQLException {
        SearchCondition cond = buildSearchCondition(keyword);
        String filterCond = buildFilterCondition(filter);
        int offset = Math.max(0, (pageNumber - 1) * pageSize);

        String sql = """
                SELECT 
                    c.CustomerId,
                    c.CustomerCode,
                    p.FullName,
                    p.Phone,
                    c.Status AS CustomerStatus,
                    s.SubscriptionId,
                    pkg.PackageName,
                    s.SubscriptionType,
                    s.StartDate,
                    s.EndDate,
                    COALESCE(s.FreezeUsed, 0) AS FreezeUsed,
                    s.MaxFreezesSnapshot,
                    s.Status AS SubscriptionStatus,
                    openFr.FreezeRecordId AS OpenFreezeId,
                    openFr.StartDate AS OpenFreezeStart,
                    openFr.PlannedEndDate AS OpenFreezeEnd
                FROM Customers c
                JOIN Persons p ON p.PersonId = c.PersonId
                OUTER APPLY (
                    SELECT TOP 1 sub.*, o.PackageId
                    FROM Subscriptions sub
                    JOIN Orders o ON o.OrderId = sub.OrderId
                    JOIN Packages pkg ON pkg.PackageId = o.PackageId
                    WHERE sub.CustomerId = c.CustomerId 
                      AND sub.SubscriptionType = 'Membership'
                      AND pkg.PackageType = 'Membership'
                      AND o.Status != 'Cancelled'
                    ORDER BY 
                        CASE sub.Status WHEN 'Frozen' THEN 1 WHEN 'Active' THEN 2 ELSE 3 END,
                        sub.EndDate DESC
                ) s
                LEFT JOIN Packages pkg ON pkg.PackageId = s.PackageId
                LEFT JOIN FreezeRecords openFr ON openFr.SubscriptionId = s.SubscriptionId AND openFr.ActualEndDate IS NULL
                WHERE c.DeletedAt IS NULL
                """
                + (cond != null ? " AND " + cond.sqlCondition + " " : "")
                + filterCond
                + """
                ORDER BY 
                    CASE WHEN openFr.FreezeRecordId IS NOT NULL THEN 0 
                         WHEN s.SubscriptionId IS NOT NULL AND s.Status = 'Active' THEN 1 
                         ELSE 2 END,
                    c.CustomerId DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<CustomerMembershipRow> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            if (cond != null) {
                for (String param : cond.params) {
                    stmt.setString(paramIndex++, param);
                }
            }
            stmt.setInt(paramIndex++, offset);
            stmt.setInt(paramIndex, pageSize > 0 ? pageSize : 15);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToCustomerMembershipRow(rs));
                }
            }
        }
        return list;
    }

    public List<CustomerMembershipRow> findCustomerMemberships(String keyword, int pageNumber, int pageSize)
            throws SQLException {
        return findCustomerMemberships(keyword, "ALL", pageNumber, pageSize);
    }

    // ===== 5. Ánh xạ ResultSet thành Entity / DTO (đặt cuối class) =====

    private FreezeRecord mapResultSetToFreezeRecord(ResultSet rs) throws SQLException {
        FreezeRecord r = new FreezeRecord();
        r.setFreezeRecordId(rs.getInt("FreezeRecordId"));
        r.setSubscriptionId(rs.getInt("SubscriptionId"));
        r.setHandledByEmployeeId(rs.getInt("HandledByEmployeeId"));

        Date s = rs.getDate("StartDate");
        r.setStartDate(s != null ? s.toLocalDate() : null);

        Date p = rs.getDate("PlannedEndDate");
        r.setPlannedEndDate(p != null ? p.toLocalDate() : null);

        Date a = rs.getDate("ActualEndDate");
        r.setActualEndDate(a != null ? a.toLocalDate() : null);

        r.setReason(rs.getString("Reason"));
        Timestamp ts = rs.getTimestamp("CreatedAt");
        r.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        return r;
    }

    private Subscription mapResultSetToSubscription(ResultSet rs) throws SQLException {
        int subscriptionId = rs.getInt("SubscriptionId");
        int orderId = rs.getInt("OrderId");
        int customerId = rs.getInt("CustomerId");

        String typeStr = rs.getString("SubscriptionType");
        Subscription.Type type = Subscription.Type.fromDbValue(typeStr);

        int ptId = rs.getInt("PtEmployeeId");
        Integer ptEmployeeId = rs.wasNull() ? null : ptId;

        Date start = rs.getDate("StartDate");
        LocalDate startDate = start != null ? start.toLocalDate() : null;

        Date end = rs.getDate("EndDate");
        LocalDate endDate = end != null ? end.toLocalDate() : null;

        java.sql.Time ft = rs.getTime("FixedStartTime");
        java.time.LocalTime fixedStartTime = ft != null ? ft.toLocalTime() : null;

        int sc = rs.getInt("GeneratedSessionCount");
        Integer generatedSessionCount = rs.wasNull() ? null : sc;

        int swapsUsed = rs.getInt("SwapsUsed");
        int ms = rs.getInt("MaxSwapsSnapshot");
        Integer maxSwapsSnapshot = rs.wasNull() ? null : ms;

        int freezeUsed = rs.getInt("FreezeUsed");
        int mf = rs.getInt("MaxFreezesSnapshot");
        Integer maxFreezesSnapshot = rs.wasNull() ? null : mf;

        Subscription.Status status = Subscription.Status.fromDbValue(rs.getString("Status"));

        return new Subscription(subscriptionId, orderId, customerId, type, ptEmployeeId,
                startDate, endDate, fixedStartTime, generatedSessionCount, swapsUsed,
                maxSwapsSnapshot, freezeUsed, maxFreezesSnapshot, status);
    }

    private FreezeRecordItemView mapResultSetToItemView(ResultSet rs) throws SQLException {
        FreezeRecordItemView item = new FreezeRecordItemView();
        item.setFreezeRecordId(rs.getInt("FreezeRecordId"));
        item.setSubscriptionId(rs.getInt("SubscriptionId"));
        item.setCustomerId(rs.getInt("CustomerId"));
        item.setCustomerCode(rs.getString("CustomerCode"));
        item.setCustomerName(rs.getString("CustomerName"));
        item.setCustomerPhone(rs.getString("CustomerPhone"));
        item.setPackageName(rs.getString("PackageName"));

        LocalDate start = rs.getDate("StartDate").toLocalDate();
        LocalDate planned = rs.getDate("PlannedEndDate").toLocalDate();
        Date actualD = rs.getDate("ActualEndDate");
        LocalDate actual = actualD != null ? actualD.toLocalDate() : null;

        item.setStartDate(start);
        item.setPlannedEndDate(planned);
        item.setActualEndDate(actual);
        item.setReason(rs.getString("Reason"));

        String empName = rs.getString("HandledByEmployeeName");
        String empCode = rs.getString("HandledByEmployeeCode");
        item.setHandledByEmployeeName(empCode + " - " + empName);

        Timestamp ts = rs.getTimestamp("CreatedAt");
        item.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);

        if (actual == null) {
            item.setStatusLabel("Đang bảo lưu");
            item.setStatusBadgeClass("badge-warning");
            item.setCanCancelEarly(true);
            long days = ChronoUnit.DAYS.between(start, LocalDate.now()) + 1;
            item.setDaysFrozen((int) Math.max(0, days));
        } else if (actual.isBefore(planned)) {
            item.setStatusLabel("Hủy sớm");
            item.setStatusBadgeClass("badge-info");
            item.setCanCancelEarly(false);
            long days = ChronoUnit.DAYS.between(start, actual) + 1;
            item.setDaysFrozen((int) Math.max(0, days));
        } else {
            item.setStatusLabel("Đã hoàn thành");
            item.setStatusBadgeClass("badge-ok");
            item.setCanCancelEarly(false);
            long days = ChronoUnit.DAYS.between(start, actual) + 1;
            item.setDaysFrozen((int) Math.max(0, days));
        }

        return item;
    }

    /**
     * Tra cứu tên gói tập theo OrderId.
     */
    public String findPackageNameByOrderId(int orderId) {
        String sql = """
                SELECT pkg.PackageName
                FROM Orders o
                JOIN Packages pkg ON o.PackageId = pkg.PackageId
                WHERE o.OrderId = ?
                """;
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PackageName");
                }
            }
        } catch (SQLException e) {
            // Trường hợp lỗi hoặc không tìm thấy, trả về giá trị mặc định
        }
        return "Gói Membership";
    }

    /**
     * Tra cứu mã và tên nhân viên phục vụ hiển thị.
     */
    public String findEmployeeDisplay(int employeeId) {
        String sql = """
                SELECT emp.EmployeeCode, p.FullName
                FROM Employees emp
                JOIN Persons p ON emp.PersonId = p.PersonId
                WHERE emp.EmployeeId = ?
                """;
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("EmployeeCode") + " - " + rs.getString("FullName");
                }
            }
        } catch (SQLException e) {
            // Trường hợp lỗi hoặc không tìm thấy, trả về giá trị mặc định
        }
        return "Lễ tân quầy";
    }

    private CustomerMembershipRow mapResultSetToCustomerMembershipRow(ResultSet rs) throws SQLException {
        CustomerMembershipRow row = new CustomerMembershipRow();
        row.setCustomerId(rs.getInt("CustomerId"));
        row.setCustomerCode(rs.getString("CustomerCode"));
        row.setFullName(rs.getString("FullName"));
        row.setPhone(rs.getString("Phone"));
        row.setCustomerStatus(rs.getString("CustomerStatus"));

        int subId = rs.getInt("SubscriptionId");
        if (!rs.wasNull() && subId > 0) {
            row.setSubscriptionId(subId);
            row.setPackageName(rs.getString("PackageName"));
            row.setSubscriptionType(rs.getString("SubscriptionType"));

            Date start = rs.getDate("StartDate");
            row.setStartDate(start != null ? start.toLocalDate() : null);

            Date end = rs.getDate("EndDate");
            row.setEndDate(end != null ? end.toLocalDate() : null);

            row.setFreezeUsed(rs.getInt("FreezeUsed"));
            int maxF = rs.getInt("MaxFreezesSnapshot");
            row.setMaxFreezesSnapshot(rs.wasNull() ? null : maxF);
            row.setSubscriptionStatus(rs.getString("SubscriptionStatus"));
        }

        int openFrId = rs.getInt("OpenFreezeId");
        if (!rs.wasNull() && openFrId > 0) {
            row.setOpenFreezeId(openFrId);
            Date fStart = rs.getDate("OpenFreezeStart");
            row.setOpenFreezeStart(fStart != null ? fStart.toLocalDate() : null);
            Date fEnd = rs.getDate("OpenFreezeEnd");
            row.setOpenFreezeEnd(fEnd != null ? fEnd.toLocalDate() : null);
        }

        return row;
    }
}


