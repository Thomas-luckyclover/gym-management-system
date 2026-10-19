package com.gms.dao;

import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionRow;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DAO màn "Lịch tập của tôi". MỖI HÀM = 1 CÂU SQL NHỎ. DAO chỉ ĐỌC DB, KHÔNG tính toán nghiệp vụ.
 * [CHOT 19/10] Màn chỉ xem (thời khóa biểu), không còn hàm ghi.
 *
 * TÌM NHANH (Ctrl+F gõ tag):
 *   [TKB]           thời khóa biểu tuần / tháng     → findSessionsInRange (1 câu SQL, có số thứ tự buổi)
 *   [CANH-BAO-GYM]  quyền vào gym, ngày phòng nghỉ  → hasGymPass, isFrozen, findClosedDays
 *   [GOI-PT]        thẻ "Gói PT của bạn"            → findPtPackages, countUpcomingSessionsOfPackage
 *   [TEN]           tên + SĐT cho thẻ gói           → findCustomerName, findEmployeeName, findEmployeePhone
 */
public class MyPtSessionDAO {

    // =====================================================================
    // [TKB] THỜI KHÓA BIỂU — lấy mọi buổi của khách trong 1 khoảng ngày (tuần hoặc tháng)
    // =====================================================================

    /**
     * [TKB] Câu SQL lấy buổi cho thời khóa biểu. 4 dấu ?: customerId, customerId, từ ngày, đến ngày.
     *
     * Bước 1 (CTE MySessions): lấy MỌI buổi thuộc gói ĐÃ THANH TOÁN mà tôi đứng tên, hoặc gói đôi tôi đi kèm,
     *         rồi đánh số thứ tự trong từng gói bằng ROW_NUMBER theo NGÀY GIỜ (buổi sớm nhất = #1).
     *         Phải đánh số TRƯỚC khi lọc ngày; lọc tuần trước thì tuần nào cũng đếm lại từ 1 → sai.
     *         [CHOT 19/10: đánh số theo ngày giờ]
     * Bước 2 (SELECT ngoài): JOIN tên + SĐT PT, tên người đứng tên và người đi kèm
     *         (1 câu SQL là đủ, không gọi DB lại cho từng buổi), rồi mới lọc theo khoảng ngày.
     */
    private static final String TIMETABLE_SQL = """
            WITH MySessions AS (
                SELECT s.SessionId, s.Status, s.PtEmployeeId,
                       s.MissedReason, s.CancelType, s.CancelReason, s.CancelledAt,
                       t.SlotDate, t.StartTime, t.EndTime,
                       sub.SubscriptionId, sub.SubscriptionType, sub.CustomerId,
                       sub.StartDate, sub.EndDate, sub.GeneratedSessionCount,
                       o.PackageNameSnapshot, o.PartnerCustomerId,
                       ROW_NUMBER() OVER (PARTITION BY s.SubscriptionId
                                          ORDER BY t.SlotDate, t.StartTime, s.SessionId) AS SessionNo
                FROM Sessions s
                JOIN TimeSlots t       ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o          ON o.OrderId = sub.OrderId
                WHERE o.Status = 'Paid'
                  AND sub.SubscriptionType IN ('PTSingle', 'PTCouple', 'SingleSession')
                  AND (sub.CustomerId = ?
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
            )
            SELECT ms.*,
                   ptPerson.FullName      AS PtName,
                   ptPerson.Phone         AS PtPhone,
                   holderPerson.FullName  AS HolderName,
                   partnerPerson.FullName AS PartnerFullName
            FROM MySessions ms
            JOIN Employees e                ON e.EmployeeId = ms.PtEmployeeId
            JOIN Persons ptPerson           ON ptPerson.PersonId = e.PersonId
            JOIN Customers holder           ON holder.CustomerId = ms.CustomerId
            JOIN Persons holderPerson       ON holderPerson.PersonId = holder.PersonId
            LEFT JOIN Customers partner     ON partner.CustomerId = ms.PartnerCustomerId
            LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
            WHERE ms.SlotDate BETWEEN ? AND ?
            ORDER BY ms.SlotDate, ms.StartTime, ms.SessionId
            """;

    /**
     * [TKB] Mọi buổi của khách (đứng tên hoặc đi kèm gói đôi) có ngày tập trong [fromDate, toDate],
     * xếp theo ngày giờ. Dùng chung cho xem theo TUẦN và theo THÁNG.
     */
    public List<MyPtSessionRow> findSessionsInRange(int customerId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        List<MyPtSessionRow> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(TIMETABLE_SQL)) {

            statement.setInt(1, customerId);                  // sub.CustomerId = ?
            statement.setInt(2, customerId);                  // o.PartnerCustomerId = ?
            statement.setDate(3, Date.valueOf(fromDate));     // từ ngày
            statement.setDate(4, Date.valueOf(toDate));       // đến ngày

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readSessionRow(rs));
                }
            }
        }
        return rows;
    }

    /** [DTO-BUOI] Đọc 1 dòng ResultSet của TIMETABLE_SQL thành 1 MyPtSessionRow. */
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
        row.setHolderCustomerId(rs.getInt("CustomerId"));
        row.setPackageName(rs.getString("PackageNameSnapshot"));
        row.setSessionNo(rs.getInt("SessionNo"));
        row.setTotalSessions(rs.getInt("GeneratedSessionCount"));

        // Buổi lẻ không có "thời hạn gói"
        if (!row.isSingleSession()) {
            row.setPackageStartDate(getNullableDate(rs, "StartDate"));
            row.setPackageEndDate(getNullableDate(rs, "EndDate"));
        }

        // Chỉ gói đôi mới có người đi kèm
        row.setPartnerCustomerId(row.isCouple() ? getNullableInt(rs, "PartnerCustomerId") : null);

        // --- người (đã JOIN sẵn) ---
        row.setPtName(rs.getString("PtName"));
        row.setPtPhone(rs.getString("PtPhone"));
        row.setHolderName(rs.getString("HolderName"));
        row.setPartnerFullName(rs.getString("PartnerFullName"));

        return row;
    }


    // =====================================================================
    // [CANH-BAO-GYM] QUYỀN VÀO GYM + NGÀY PHÒNG NGHỈ (giống điều kiện JOB-04 — notes 10/10)
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

    /**
     * [CANH-BAO-GYM] Các ngày phòng NGHỈ CẢ NGÀY trong [fromDate, toDate] (Manager khai báo — notes 10/10).
     * Buổi rơi vào ngày này vẫn Scheduled để PT dời giúp khách (UC-35) → màn khách hiện "Cần chú ý".
     */
    public Set<LocalDate> findClosedDays(LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT ApplyDate
                FROM OperatingHourExceptions
                WHERE IsClosedAllDay = 1
                  AND ApplyDate BETWEEN ? AND ?
                """;

        Set<LocalDate> days = new HashSet<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDate(1, Date.valueOf(fromDate));
            statement.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    days.add(rs.getDate("ApplyDate").toLocalDate());
                }
            }
        }
        return days;
    }


    // =====================================================================
    // [GOI-PT] GÓI PT (thẻ "Gói PT của bạn" — sẽ chuyển sang màn "Gói của tôi" ở GĐ4)
    // =====================================================================

    /**
     * [GOI-PT] Lấy các gói PT (cá nhân / đôi) của khách.
     *
     * @param activeOnly true  = chỉ gói Active (thẻ "Gói PT của bạn")
     *                   false = mọi gói (kể cả gói đã kết thúc)
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
    // [TEN] TÊN + SĐT cho thẻ gói
    // =====================================================================

    /** [TEN] Họ tên của 1 khách hàng. */
    public String findCustomerName(int customerId) throws SQLException {
        String sql = """
                SELECT p.FullName
                FROM Customers c
                JOIN Persons p ON p.PersonId = c.PersonId
                WHERE c.CustomerId = ?
                """;
        return findOneText(sql, customerId);
    }

    /** [TEN] Họ tên của 1 nhân viên (PT). */
    public String findEmployeeName(int employeeId) throws SQLException {
        String sql = """
                SELECT p.FullName
                FROM Employees e
                JOIN Persons p ON p.PersonId = e.PersonId
                WHERE e.EmployeeId = ?
                """;
        return findOneText(sql, employeeId);
    }

    /** [SDT-PT] Số điện thoại của PT (Persons.Phone). Không có → null. */
    public String findEmployeePhone(int employeeId) throws SQLException {
        String sql = """
                SELECT p.Phone
                FROM Employees e
                JOIN Persons p ON p.PersonId = e.PersonId
                WHERE e.EmployeeId = ?
                """;
        return findOneText(sql, employeeId);
    }


    // =====================================================================
    // HÀM PHỤ DÙNG CHUNG
    // =====================================================================

    /** Chạy câu SQL lấy 1 giá trị chữ (1 dấu ? là mã). Không thấy trả null. */
    private String findOneText(String sql, int id) throws SQLException {
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
