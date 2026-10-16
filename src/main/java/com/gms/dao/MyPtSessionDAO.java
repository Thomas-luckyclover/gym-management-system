package com.gms.dao;

import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionFilter;
import com.gms.dto.MyPtSessionRow;
import com.gms.dto.PtSessionInfo;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO màn "Lịch tập của tôi". MỖI HÀM = 1 CÂU SQL NHỎ. DAO chỉ đọc/ghi DB, KHÔNG tính toán nghiệp vụ.
 *
 * TÌM NHANH (Ctrl+F gõ tag):
 *   [GOI-PT]        thẻ "Gói PT của bạn"         → findPtPackages, countUpcomingSessionsOfPackage
 *   [TEN]           tên người                     → findCustomerName, findEmployeeName
 *   [CANH-BAO-GYM]  quyền vào gym (cảnh báo vàng) → hasGymPass, isFrozen
 *   [SAP-TOI]       lịch sắp tới                  → findUpcomingDays, findUpcomingSessions
 *   [LICH-SU]       lịch sử                       → findHistoryDays, findHistorySessions
 *   [PHAN-TRANG]    phân trang theo ngày          → find...Days (bước 1), find...Sessions (bước 2)
 *   [LOC]           bộ lọc                        → addFilter
 *   [HUY-BUOI]      hủy buổi                      → findSessionInfo, cancelSession, insertCancelAuditLog
 *   [THONG-BAO]     người nhận thông báo          → findRecipientAccounts
 */
public class MyPtSessionDAO {

    // =====================================================================
    // CÁC ĐOẠN SQL DÙNG CHUNG — ghép lại thành câu hoàn chỉnh ở [SAP-TOI], [LICH-SU]
    // =====================================================================

    /** Các cột của 1 buổi. */
    private static final String SESSION_COLUMNS = """
            SELECT s.SessionId, s.Status, s.PtEmployeeId,
                   s.MissedReason, s.CancelType, s.CancelReason, s.CancelledAt,
                   t.SlotDate, t.StartTime, t.EndTime,
                   sub.SubscriptionId, sub.SubscriptionType, sub.Status AS SubscriptionStatus,
                   sub.CustomerId, sub.StartDate, sub.EndDate,
                   sub.MaxSwapsSnapshot, sub.SwapsUsed,
                   o.PackageNameSnapshot, o.PartnerCustomerId
            """;

    /**
     * [QUYEN] "Buổi của tôi" = buổi thuộc gói ĐÃ THANH TOÁN mà tôi ĐỨNG TÊN,
     *                          hoặc gói ĐÔI mà tôi là NGƯỜI ĐI KÈM.
     * 2 dấu ?: customerId, customerId.
     */
    private static final String FROM_MY_SESSIONS = """
            FROM Sessions s
            JOIN TimeSlots t       ON t.TimeSlotId = s.TimeSlotId
            JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
            JOIN Orders o          ON o.OrderId = sub.OrderId
            WHERE o.Status = 'Paid'
              AND sub.SubscriptionType IN ('PTSingle', 'PTCouple', 'SingleSession')
              AND (sub.CustomerId = ?
                   OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
            """;

    /**
     * [SAP-TOI] Buổi SẮP TỚI = còn Scheduled và CHƯA KẾT THÚC.
     * 3 dấu ?: hôm nay, hôm nay, giờ hiện tại.
     */
    private static final String IS_UPCOMING = """
              AND s.Status = 'Scheduled'
              AND (t.SlotDate > ? OR (t.SlotDate = ? AND t.EndTime > CAST(? AS TIME)))
            """;

    /**
     * [LICH-SU] Buổi LỊCH SỬ = đã có kết quả (CheckedIn / Missed / Cancelled)
     *                        HOẶC còn Scheduled nhưng ĐÃ KẾT THÚC (chờ JOB-04 chốt).
     * 3 dấu ?: hôm nay, hôm nay, giờ hiện tại.
     */
    private static final String IS_HISTORY = """
              AND (s.Status IN ('CheckedIn', 'Missed', 'Cancelled')
                   OR (s.Status = 'Scheduled'
                       AND (t.SlotDate < ? OR (t.SlotDate = ? AND t.EndTime <= CAST(? AS TIME)))))
            """;


    // =====================================================================
    // [GOI-PT] GÓI PT
    // =====================================================================

    /**
     * [GOI-PT] Lấy các gói PT (cá nhân / đôi) của khách.
     *
     * @param activeOnly true  = chỉ gói Active (thẻ "Gói PT của bạn")
     *                   false = mọi gói (ô lọc "Gói PT", để tra lịch sử gói cũ)
     */
    public List<MyPtPackageView> findPtPackages(int customerId, boolean activeOnly) throws SQLException {
        String sql = """
                SELECT sub.SubscriptionId, sub.SubscriptionType, sub.CustomerId, sub.PtEmployeeId,
                       sub.StartDate, sub.EndDate, sub.GeneratedSessionCount,
                       sub.MaxSwapsSnapshot, sub.SwapsUsed,
                       o.PackageNameSnapshot, o.PartnerCustomerId
                FROM Subscriptions sub
                JOIN Orders o ON o.OrderId = sub.OrderId
                WHERE o.Status = 'Paid'
                  AND sub.SubscriptionType IN ('PTSingle', 'PTCouple')
                  AND (sub.CustomerId = ?
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
                """;

        if (activeOnly) {
            sql += " AND sub.Status = 'Active'";
        }
        sql += " ORDER BY sub.EndDate, sub.StartDate, sub.SubscriptionId";

        List<MyPtPackageView> packages = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, customerId);
            statement.setInt(2, customerId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    MyPtPackageView p = new MyPtPackageView();
                    p.setSubscriptionId(rs.getInt("SubscriptionId"));
                    p.setType(rs.getString("SubscriptionType"));
                    p.setHolderCustomerId(rs.getInt("CustomerId"));
                    p.setPtEmployeeId(rs.getInt("PtEmployeeId"));
                    p.setStartDate(rs.getDate("StartDate").toLocalDate());
                    p.setEndDate(rs.getDate("EndDate").toLocalDate());
                    p.setGeneratedSessionCount(rs.getInt("GeneratedSessionCount"));
                    p.setMaxSwaps(rs.getInt("MaxSwapsSnapshot"));
                    p.setSwapsUsed(rs.getInt("SwapsUsed"));
                    p.setPackageName(rs.getString("PackageNameSnapshot"));
                    p.setPartnerCustomerId(p.isCouple() ? getNullableInt(rs, "PartnerCustomerId") : null);
                    packages.add(p);
                }
            }
        }
        return packages;
    }

    /** [GOI-PT] Đếm số buổi CÒN Scheduled và CHƯA BẮT ĐẦU của 1 gói (cột "Buổi sắp tới" trên thẻ gói). */
    public int countUpcomingSessionsOfPackage(int subscriptionId, LocalDateTime now) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM Sessions s
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                WHERE s.SubscriptionId = ?
                  AND s.Status = 'Scheduled'
                  AND (t.SlotDate > ? OR (t.SlotDate = ? AND t.StartTime > CAST(? AS TIME)))
                """;

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, subscriptionId);
            statement.setDate(2, Date.valueOf(now.toLocalDate()));
            statement.setDate(3, Date.valueOf(now.toLocalDate()));
            statement.setTime(4, Time.valueOf(now.toLocalTime()));

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }


    // =====================================================================
    // [TEN] TÊN NGƯỜI
    // =====================================================================

    /** [TEN] Họ tên của 1 khách hàng. */
    public String findCustomerName(int customerId) throws SQLException {
        String sql = """
                SELECT p.FullName
                FROM Customers c
                JOIN Persons p ON p.PersonId = c.PersonId
                WHERE c.CustomerId = ?
                """;
        return findOneName(sql, customerId);
    }

    /** [TEN] Họ tên của 1 nhân viên (PT). */
    public String findEmployeeName(int employeeId) throws SQLException {
        String sql = """
                SELECT p.FullName
                FROM Employees e
                JOIN Persons p ON p.PersonId = e.PersonId
                WHERE e.EmployeeId = ?
                """;
        return findOneName(sql, employeeId);
    }


    // =====================================================================
    // [CANH-BAO-GYM] QUYỀN VÀO GYM VÀO 1 NGÀY (giống điều kiện JOB-04 — notes 10/10)
    // =====================================================================

    /** [CANH-BAO-GYM] Khách có Membership HOẶC DayPass phủ ngày này không? */
    public boolean hasGymPass(int customerId, LocalDate date) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM Subscriptions
                WHERE CustomerId = ?
                  AND ? BETWEEN StartDate AND EndDate
                  AND (   (SubscriptionType = 'Membership' AND Status IN ('Active', 'Frozen', 'Expired'))
                       OR (SubscriptionType = 'DayPass'    AND Status IN ('Active', 'Used', 'Expired')))
                """;
        return countByCustomerAndDate(sql, customerId, date) > 0;
    }

    /** [CANH-BAO-GYM] Ngày này có nằm trong kỳ BẢO LƯU Membership của khách không? */
    public boolean isFrozen(int customerId, LocalDate date) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM FreezeRecords f
                JOIN Subscriptions sub ON sub.SubscriptionId = f.SubscriptionId
                WHERE sub.CustomerId = ?
                  AND sub.SubscriptionType = 'Membership'
                  AND ? BETWEEN f.StartDate AND COALESCE(f.ActualEndDate, f.PlannedEndDate)
                """;
        return countByCustomerAndDate(sql, customerId, date) > 0;
    }


    // =====================================================================
    // [SAP-TOI] LỊCH SẮP TỚI
    // [PHAN-TRANG] 2 bước: lấy danh sách NGÀY trước → Service cắt 8 ngày → lấy BUỔI của 8 ngày đó
    // =====================================================================

    /** [SAP-TOI][PHAN-TRANG] Bước 1: các NGÀY có buổi sắp tới (gần nhất trước). */
    public List<LocalDate> findUpcomingDays(int customerId, LocalDateTime now, MyPtSessionFilter filter)
            throws SQLException {

        StringBuilder sql = new StringBuilder("SELECT DISTINCT t.SlotDate " + FROM_MY_SESSIONS + IS_UPCOMING);
        List<Object> params = startParams(customerId, now);

        addFilter(sql, params, filter, false);
        sql.append(" ORDER BY t.SlotDate");

        return queryDays(sql.toString(), params);
    }

    /** [SAP-TOI][PHAN-TRANG] Bước 2: các BUỔI sắp tới trong khoảng [fromDate, toDate] (= các ngày của trang). */
    public List<MyPtSessionRow> findUpcomingSessions(int customerId, LocalDateTime now, MyPtSessionFilter filter,
            LocalDate fromDate, LocalDate toDate) throws SQLException {

        StringBuilder sql = new StringBuilder(SESSION_COLUMNS + FROM_MY_SESSIONS + IS_UPCOMING);
        List<Object> params = startParams(customerId, now);

        addFilter(sql, params, filter, false);

        sql.append(" AND t.SlotDate BETWEEN ? AND ?");
        params.add(fromDate);
        params.add(toDate);

        sql.append(" ORDER BY t.SlotDate, t.StartTime, s.SessionId");

        return querySessions(sql.toString(), params);
    }


    // =====================================================================
    // [LICH-SU] LỊCH SỬ — giống [SAP-TOI], chỉ khác IS_HISTORY và sắp MỚI NHẤT trước
    // =====================================================================

    /** [LICH-SU][PHAN-TRANG] Bước 1: các NGÀY có buổi lịch sử (mới nhất trước). */
    public List<LocalDate> findHistoryDays(int customerId, LocalDateTime now, MyPtSessionFilter filter)
            throws SQLException {

        StringBuilder sql = new StringBuilder("SELECT DISTINCT t.SlotDate " + FROM_MY_SESSIONS + IS_HISTORY);
        List<Object> params = startParams(customerId, now);

        addFilter(sql, params, filter, true);
        sql.append(" ORDER BY t.SlotDate DESC");

        return queryDays(sql.toString(), params);
    }

    /** [LICH-SU][PHAN-TRANG] Bước 2: các BUỔI lịch sử trong khoảng [fromDate, toDate]. */
    public List<MyPtSessionRow> findHistorySessions(int customerId, LocalDateTime now, MyPtSessionFilter filter,
            LocalDate fromDate, LocalDate toDate) throws SQLException {

        StringBuilder sql = new StringBuilder(SESSION_COLUMNS + FROM_MY_SESSIONS + IS_HISTORY);
        List<Object> params = startParams(customerId, now);

        addFilter(sql, params, filter, true);

        sql.append(" AND t.SlotDate BETWEEN ? AND ?");
        params.add(fromDate);
        params.add(toDate);

        sql.append(" ORDER BY t.SlotDate DESC, t.StartTime DESC, s.SessionId DESC");

        return querySessions(sql.toString(), params);
    }


    // =====================================================================
    // [HUY-BUOI] 1 BUỔI: đọc để kiểm tra + hủy (cũng được [DOI-BUOI] dùng lại)
    // =====================================================================

    /** [HUY-BUOI] Đọc thông tin 1 buổi (tự mở connection). null = không có buổi này. */
    public PtSessionInfo findSessionInfo(int sessionId) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return findSessionInfo(connection, sessionId, false);
        }
    }

    /**
     * [HUY-BUOI][DOI-BUOI] Đọc thông tin 1 buổi trên connection có sẵn (dùng trong transaction).
     *
     * @param lock true = KHÓA dòng buổi + gói (UPDLOCK, HOLDLOCK) → người khác phải chờ mình xong
     */
    public PtSessionInfo findSessionInfo(Connection connection, int sessionId, boolean lock) throws SQLException {
        String lockHint = lock ? "WITH (UPDLOCK, HOLDLOCK)" : "";

        String sql = """
                SELECT s.SessionId, s.Status, s.TimeSlotId, s.PtEmployeeId,
                       t.SlotDate, t.StartTime, t.EndTime,
                       sub.SubscriptionId, sub.SubscriptionType, sub.Status AS SubscriptionStatus,
                       sub.CustomerId, sub.StartDate, sub.EndDate, sub.SwapsUsed, sub.MaxSwapsSnapshot,
                       o.Status AS OrderStatus, o.PartnerCustomerId,
                       e.Status AS PtStatus, e.PersonId AS PtPersonId, p.FullName AS PtName
                FROM Sessions s %s
                JOIN Subscriptions sub %s ON sub.SubscriptionId = s.SubscriptionId
                JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                JOIN Orders o    ON o.OrderId = sub.OrderId
                JOIN Employees e ON e.EmployeeId = s.PtEmployeeId
                JOIN Persons p   ON p.PersonId = e.PersonId
                WHERE s.SessionId = ?
                """.formatted(lockHint, lockHint);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, sessionId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                PtSessionInfo info = new PtSessionInfo();
                info.setSessionId(rs.getInt("SessionId"));
                info.setSessionStatus(rs.getString("Status"));
                info.setTimeSlotId(rs.getInt("TimeSlotId"));
                info.setPtEmployeeId(rs.getInt("PtEmployeeId"));
                info.setSlotDate(rs.getDate("SlotDate").toLocalDate());
                info.setStartTime(rs.getTime("StartTime").toLocalTime());
                info.setEndTime(rs.getTime("EndTime").toLocalTime());
                info.setSubscriptionId(rs.getInt("SubscriptionId"));
                info.setSubscriptionType(rs.getString("SubscriptionType"));
                info.setSubscriptionStatus(rs.getString("SubscriptionStatus"));
                info.setCustomerId(rs.getInt("CustomerId"));
                info.setPackageStartDate(getNullableDate(rs, "StartDate"));
                info.setPackageEndDate(getNullableDate(rs, "EndDate"));
                info.setSwapsUsed(rs.getInt("SwapsUsed"));
                info.setMaxSwaps(rs.getInt("MaxSwapsSnapshot"));
                info.setOrderStatus(rs.getString("OrderStatus"));

                // Người đi kèm chỉ có nghĩa với gói đôi
                boolean isCouple = "PTCouple".equals(info.getSubscriptionType());
                info.setPartnerCustomerId(isCouple ? getNullableInt(rs, "PartnerCustomerId") : null);

                info.setPtStatus(rs.getString("PtStatus"));
                info.setPtPersonId(rs.getInt("PtPersonId"));
                info.setPtName(rs.getString("PtName"));
                return info;
            }
        }
    }

    /**
     * [HUY-BUOI] Khách hủy buổi:
     *   Status = Cancelled, CancelType = ByCustomer, lưu lý do (có thể null) + người hủy + giờ hủy,
     *   RefundAgreed = 0 (BR-79: vẫn trừ buổi, không hoàn tiền).
     *   "AND Status = 'Scheduled'" = chống bấm 2 lần / người khác vừa đổi trạng thái.
     *
     * @return true nếu cập nhật đúng 1 dòng
     */
    public boolean cancelSession(Connection connection, int sessionId, int accountId, String reason)
            throws SQLException {
        String sql = """
                UPDATE Sessions
                SET Status               = 'Cancelled',
                    CancelType           = 'ByCustomer',
                    CancelReason         = ?,
                    CancelledAt          = SYSDATETIME(),
                    CancelledByAccountId = ?,
                    RefundAgreed         = 0
                WHERE SessionId = ?
                  AND Status = 'Scheduled'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reason);
            statement.setInt(2, accountId);
            statement.setInt(3, sessionId);
            return statement.executeUpdate() == 1;
        }
    }

    /** [HUY-BUOI][AUDIT] Ghi nhật ký: ai hủy buổi nào (GBR-07). reason có thể null. */
    public void insertCancelAuditLog(Connection connection, int accountId, int sessionId, String reason)
            throws SQLException {
        String sql = """
                INSERT INTO AuditLogs (AccountId, ActingRole, Action, EntityName, EntityId,
                                       FieldChanged, OldValue, NewValue, Reason)
                VALUES (?, 'Customer', N'Khách hủy buổi PT', 'Sessions', ?,
                        'Status', 'Scheduled', 'Cancelled', ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            statement.setInt(2, sessionId);
            statement.setString(3, reason);
            statement.executeUpdate();
        }
    }


    // =====================================================================
    // [THONG-BAO] NGƯỜI NHẬN THÔNG BÁO (dùng cho [HUY-BUOI] và [DOI-BUOI])
    // =====================================================================

    /**
     * [THONG-BAO] Tài khoản (đang Active) cần nhận thông báo: khách đứng tên, người đi kèm, PT (CBR-46).
     * Người không có tài khoản thì không nhận (không gửi ra kênh ngoài).
     */
    public List<Integer> findRecipientAccounts(Connection connection, int customerId, Integer partnerCustomerId,
            int ptEmployeeId) throws SQLException {
        String sql = """
                SELECT DISTINCT a.AccountId
                FROM Accounts a
                WHERE a.Status = 'Active'
                  AND (   a.PersonId IN (SELECT PersonId FROM Customers WHERE CustomerId IN (?, ?))
                       OR a.PersonId IN (SELECT PersonId FROM Employees WHERE EmployeeId = ?))
                """;

        List<Integer> accountIds = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setInt(2, partnerCustomerId == null ? customerId : partnerCustomerId);
            statement.setInt(3, ptEmployeeId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    accountIds.add(rs.getInt(1));
                }
            }
        }
        return accountIds;
    }


    // =====================================================================
    // HÀM PHỤ DÙNG CHUNG
    // =====================================================================

    /** 5 tham số đầu: cho FROM_MY_SESSIONS (2 dấu ?) + IS_UPCOMING / IS_HISTORY (3 dấu ?). */
    private List<Object> startParams(int customerId, LocalDateTime now) {
        List<Object> params = new ArrayList<>();
        params.add(customerId);              // sub.CustomerId = ?
        params.add(customerId);              // o.PartnerCustomerId = ?
        params.add(now.toLocalDate());       // t.SlotDate > / < ?
        params.add(now.toLocalDate());       // t.SlotDate = ?
        params.add(now.toLocalTime());       // t.EndTime > / <= ?
        return params;
    }

    /**
     * [LOC] Ghép điều kiện BỘ LỌC vào cuối WHERE. Ô nào null thì bỏ qua.
     * Giá trị luôn đi qua dấu ? (KHÔNG nối chuỗi người dùng nhập → chống SQL Injection).
     *
     * @param isHistory true = khối [LICH-SU], được lọc thêm theo tab trạng thái
     */
    private void addFilter(StringBuilder sql, List<Object> params, MyPtSessionFilter filter, boolean isHistory) {

        // [LOC] Gói PT
        if (filter.getSubscriptionId() != null) {
            sql.append(" AND sub.SubscriptionId = ?");
            params.add(filter.getSubscriptionId());
        }

        // [LOC] Loại buổi
        if (filter.getType() != null) {
            sql.append(" AND sub.SubscriptionType = ?");
            params.add(filter.getType());
        }

        // [LOC] Từ ngày / Đến ngày (gồm cả 2 đầu)
        if (filter.getDateFrom() != null) {
            sql.append(" AND t.SlotDate >= ?");
            params.add(filter.getDateFrom());
        }
        if (filter.getDateTo() != null) {
            sql.append(" AND t.SlotDate <= ?");
            params.add(filter.getDateTo());
        }

        // [LOC] Buổi trong ngày — so giờ BẮT ĐẦU: Sáng < 12:00 | Chiều 12:00–18:00 | Tối >= 18:00
        if (filter.getTimeOfDay() != null) {
            switch (filter.getTimeOfDay()) {
                case "morning" -> {
                    sql.append(" AND t.StartTime < CAST(? AS TIME)");
                    params.add(LocalTime.of(12, 0));
                }
                case "afternoon" -> {
                    sql.append(" AND t.StartTime >= CAST(? AS TIME) AND t.StartTime < CAST(? AS TIME)");
                    params.add(LocalTime.of(12, 0));
                    params.add(LocalTime.of(18, 0));
                }
                case "evening" -> {
                    sql.append(" AND t.StartTime >= CAST(? AS TIME)");
                    params.add(LocalTime.of(18, 0));
                }
                default -> { }
            }
        }

        // [LOC][LICH-SU] Tab trạng thái — chỉ khối Lịch sử
        if (isHistory && filter.getHistoryStatus() != null) {
            sql.append(" AND s.Status = ?");
            params.add(toDbStatus(filter.getHistoryStatus()));
        }
    }

    /** [LOC] Đổi giá trị tab trên URL sang Sessions.Status trong DB. */
    private String toDbStatus(String historyStatus) {
        return switch (historyStatus) {
            case "pending"   -> "Scheduled";   // đã kết thúc, chờ JOB-04 chốt
            case "checkedIn" -> "CheckedIn";
            case "missed"    -> "Missed";
            default          -> "Cancelled";   // "cancelled"
        };
    }

    /** Gán lần lượt giá trị vào các dấu ? (JDBC đánh số từ 1). */
    private void setParams(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            int position = i + 1;

            if (value instanceof Integer number) {
                statement.setInt(position, number);
            } else if (value instanceof String text) {
                statement.setString(position, text);
            } else if (value instanceof LocalDate date) {
                statement.setDate(position, Date.valueOf(date));
            } else if (value instanceof LocalTime time) {
                statement.setTime(position, Time.valueOf(time));
            } else {
                throw new SQLException("Kiểu tham số chưa hỗ trợ: " + value);
            }
        }
    }

    /** Chạy câu SQL trả về 1 cột SlotDate → List ngày. */
    private List<LocalDate> queryDays(String sql, List<Object> params) throws SQLException {
        List<LocalDate> days = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            setParams(statement, params);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    days.add(rs.getDate("SlotDate").toLocalDate());
                }
            }
        }
        return days;
    }

    /** Chạy câu SQL trả về các buổi → List MyPtSessionRow. */
    private List<MyPtSessionRow> querySessions(String sql, List<Object> params) throws SQLException {
        List<MyPtSessionRow> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            setParams(statement, params);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readSessionRow(rs));
                }
            }
        }
        return rows;
    }

    /** [DTO-BUOI] Đọc 1 dòng ResultSet thành 1 MyPtSessionRow. */
    private MyPtSessionRow readSessionRow(ResultSet rs) throws SQLException {
        MyPtSessionRow row = new MyPtSessionRow();

        // --- buổi ---
        row.setSessionId(rs.getInt("SessionId"));
        row.setStatus(rs.getString("Status"));
        row.setPtEmployeeId(rs.getInt("PtEmployeeId"));
        row.setMissedReason(rs.getString("MissedReason"));
        row.setCancelType(rs.getString("CancelType"));
        row.setCancelReason(rs.getString("CancelReason"));
        Timestamp cancelledAt = rs.getTimestamp("CancelledAt");
        row.setCancelledAt(cancelledAt == null ? null : cancelledAt.toLocalDateTime());

        // --- giờ ---
        row.setSlotDate(rs.getDate("SlotDate").toLocalDate());
        row.setStartTime(rs.getTime("StartTime").toLocalTime());
        row.setEndTime(rs.getTime("EndTime").toLocalTime());

        // --- gói ---
        row.setSubscriptionId(rs.getInt("SubscriptionId"));
        row.setType(rs.getString("SubscriptionType"));
        row.setSubscriptionStatus(rs.getString("SubscriptionStatus"));
        row.setMaxSwaps(rs.getInt("MaxSwapsSnapshot"));
        row.setSwapsUsed(rs.getInt("SwapsUsed"));
        row.setHolderCustomerId(rs.getInt("CustomerId"));
        row.setPackageName(rs.getString("PackageNameSnapshot"));

        // Buổi lẻ không có "thời hạn gói"
        if (!row.isSingleSession()) {
            row.setPackageStartDate(getNullableDate(rs, "StartDate"));
            row.setPackageEndDate(getNullableDate(rs, "EndDate"));
        }

        // Chỉ gói đôi mới có người đi kèm
        row.setPartnerCustomerId(row.isCouple() ? getNullableInt(rs, "PartnerCustomerId") : null);

        return row;
    }

    /** [TEN] Chạy câu SQL lấy 1 tên (1 dấu ? là mã). Không thấy trả null. */
    private String findOneName(String sql, int id) throws SQLException {
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** [CANH-BAO-GYM] Chạy câu COUNT có 2 dấu ?: customerId, date. */
    private int countByCustomerAndDate(String sql, int customerId, LocalDate date) throws SQLException {
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, customerId);
            statement.setDate(2, Date.valueOf(date));

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** rs.getInt trả 0 khi NULL → dùng hàm này để nhận đúng null. */
    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDate getNullableDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
