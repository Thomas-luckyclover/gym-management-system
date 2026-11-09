package com.gms.dao;

import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionRow;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DAO BUỔI PT CỦA KHÁCH — dùng chung cho 2 màn: "Lịch tập" (/customer/pt-sessions) và "Gói của tôi" (/customer/bookings).
 *
 * AI GỌI DAO NÀY?  MyPtSessionService (getWeek, getMonth, fillDisplay) · MyBookingService (getMyBookings).
 * Chỉ ĐỌC DB, không tính nghiệp vụ (việc đó ở Service).
 * BỐ CỤC: xếp theo HÀM CHÍNH của Service dùng tới.
 *   PHỤC VỤ getWeek / getMonth / getMyBookings   các buổi của khách (3 hàm find dùng chung querySessions, readSessionRow)
 *   PHỤC VỤ getMyBookings                        gói PT của khách (cột trái màn Gói của tôi)
 *   PHỤC VỤ fillDisplay                          quyền vào gym (Membership / DayPass / bảo lưu) + ngày phòng nghỉ
 */
public class MyPtSessionDAO {
    /**
     * Câu SQL gốc lấy buổi của khách (đứng tên gói, hoặc đi kèm gói đôi). 2 dấu ?: customerId, customerId.
     *   Bước 1 (CTE): lấy MỌI buổi của các gói đã thanh toán, đánh số #1, #2… theo ngày giờ trong từng gói.
     *                 Phải đánh số TRƯỚC khi lọc, nếu không tuần nào cũng đếm lại từ 1.
     *   Bước 2:       JOIN sẵn tên + SĐT PT, tên 2 người của gói đôi → không phải gọi DB lại từng buổi.
     * Hàm gọi sẽ nối thêm "WHERE …" của riêng nó, rồi ORDER_BY.
     */
    private static final String SESSIONS_SQL = """
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
            """;

    private static final String ORDER_BY = " ORDER BY ms.SlotDate, ms.StartTime, ms.SessionId";



    // ============================================================================================================
    // PHỤC VỤ HÀM CHÍNH getWeek / getMonth (findSessionsInRange) + getMyBookings (findSessionsOfPackage, findSingleSessions)
    //   Ba hàm find dùng chung querySessions → readSessionRow → getNullableInt ngay dưới
    // ============================================================================================================

    /** Lịch tập (getWeek, getMonth): buổi có ngày tập trong [fromDate, toDate] (1 tuần hoặc 1 tháng). */
    public List<MyPtSessionRow> findSessionsInRange(int customerId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = SESSIONS_SQL + " WHERE ms.SlotDate BETWEEN ? AND ?" + ORDER_BY;
        return querySessions(
                sql,
                customerId,
                Date.valueOf(fromDate),
                Date.valueOf(toDate));
    }

    /** Gói của tôi: mọi buổi của 1 gói (#1 → #N). Gói không phải của khách → danh sách rỗng. */
    public List<MyPtSessionRow> findSessionsOfPackage(int customerId, int subscriptionId) throws SQLException {
        String sql = SESSIONS_SQL + " WHERE ms.SubscriptionId = ?" + ORDER_BY;
        return querySessions(sql, customerId, subscriptionId);
    }

    /** Gói của tôi: mọi buổi lẻ của khách. */
    public List<MyPtSessionRow> findSingleSessions(int customerId) throws SQLException {
        String sql = SESSIONS_SQL + " WHERE ms.SubscriptionType = 'SingleSession'" + ORDER_BY;
        return querySessions(sql, customerId);
    }

    /**
     * HÀM PHỤ (findSessionsInRange, findSessionsOfPackage, findSingleSessions) — chạy SESSIONS_SQL + WHERE riêng.
     * B1: gán 2 dấu ? đầu = customerId → B2: gán các dấu ? còn lại theo thứ tự → B3: đọc từng dòng.
     */
    private List<MyPtSessionRow> querySessions(String sql, int customerId, Object... extraParams) throws SQLException {
        List<MyPtSessionRow> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            // B1: 2 dấu ? đầu = mã khách
            statement.setInt(1, customerId);
            statement.setInt(2, customerId);

            // B2: các dấu ? còn lại (ngày hoặc mã gói)
            for (int i = 0; i < extraParams.length; i++) {
                statement.setObject(i + 3, extraParams[i]);
            }

            // B3: mỗi dòng → 1 buổi
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readSessionRow(rs));
                }
            }
        }
        return rows;
    }

    /** HÀM PHỤ (querySessions) — đọc 1 dòng của SESSIONS_SQL thành 1 buổi. */
    private MyPtSessionRow readSessionRow(ResultSet rs) throws SQLException {
        // B1: đọc các cột
        Timestamp cancelledAt = rs.getTimestamp("CancelledAt");
        String type = rs.getString("SubscriptionType");
        java.sql.Date startDate = rs.getDate("StartDate");
        java.sql.Date endDate = rs.getDate("EndDate");

        // B2: gom thành 1 buổi (Service điền phần hiển thị sau)
        return new MyPtSessionRow(
                rs.getInt("SessionId"), rs.getString("Status"), rs.getString("MissedReason"),
                rs.getString("CancelType"), rs.getString("CancelReason"),
                cancelledAt == null ? null : cancelledAt.toLocalDateTime(),
                rs.getDate("SlotDate").toLocalDate(), rs.getTime("StartTime").toLocalTime(),
                rs.getTime("EndTime").toLocalTime(), rs.getInt("SubscriptionId"), type,
                startDate == null ? null : startDate.toLocalDate(),
                endDate == null ? null : endDate.toLocalDate(), rs.getInt("CustomerId"),
                "PTCouple".equals(type) ? getNullableInt(rs, "PartnerCustomerId") : null,
                rs.getString("PackageNameSnapshot"), rs.getInt("SessionNo"),
                rs.getInt("GeneratedSessionCount"), rs.getInt("PtEmployeeId"),
                rs.getString("PtName"), rs.getString("PtPhone"), rs.getString("HolderName"),
                rs.getString("PartnerFullName"));
    }

    /** HÀM PHỤ (readSessionRow, findMyPackages) — rs.getInt trả 0 khi NULL → hàm này trả đúng null. */
    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }



    // ============================================================================================================
    // PHỤC VỤ HÀM CHÍNH getMyBookings — mọi gói PT cá nhân / đôi đã thanh toán của khách (cột trái màn Gói của tôi)
    // ============================================================================================================

    /**
     * Gói của tôi (getMyBookings B2): mọi gói PT cá nhân / đôi đã thanh toán của khách (đang tập và đã kết thúc), 1 câu SQL.
     * PastSessions = số buổi có ngày tập TRƯỚC hôm nay → thanh tiến độ "đã qua 30/45 buổi". 3 dấu ?: hôm nay, customerId, customerId.
     */
    public List<MyPtPackageView> findMyPackages(int customerId, LocalDate today) throws SQLException {
        String sql = """
                SELECT sub.SubscriptionId, sub.SubscriptionType, sub.Status, sub.CustomerId,
                       sub.StartDate, sub.EndDate, sub.GeneratedSessionCount,
                       o.PackageNameSnapshot, o.PartnerCustomerId,
                       ptPerson.FullName      AS PtName,
                       ptPerson.Phone         AS PtPhone,
                       holderPerson.FullName  AS HolderName,
                       partnerPerson.FullName AS PartnerFullName,
                       (SELECT COUNT(*)
                        FROM Sessions s
                        JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
                        WHERE s.SubscriptionId = sub.SubscriptionId
                          AND t.SlotDate < ?) AS PastSessions
                FROM Subscriptions sub
                JOIN Orders o                   ON o.OrderId = sub.OrderId
                JOIN Employees e                ON e.EmployeeId = sub.PtEmployeeId
                JOIN Persons ptPerson           ON ptPerson.PersonId = e.PersonId
                JOIN Customers holder           ON holder.CustomerId = sub.CustomerId
                JOIN Persons holderPerson       ON holderPerson.PersonId = holder.PersonId
                LEFT JOIN Customers partner     ON partner.CustomerId = o.PartnerCustomerId
                LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
                WHERE o.Status = 'Paid'
                  AND sub.SubscriptionType IN ('PTSingle', 'PTCouple')
                  AND (sub.CustomerId = ?
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId = ?))
                ORDER BY sub.EndDate DESC, sub.SubscriptionId DESC
                """;

        List<MyPtPackageView> packages = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDate(1, Date.valueOf(today));
            statement.setInt(2, customerId);
            statement.setInt(3, customerId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    MyPtPackageView p = new MyPtPackageView();
                    p.setSubscriptionId(rs.getInt("SubscriptionId"));
                    p.setType(rs.getString("SubscriptionType"));
                    p.setStatus(rs.getString("Status"));
                    p.setHolderCustomerId(rs.getInt("CustomerId"));
                    p.setStartDate(rs.getDate("StartDate").toLocalDate());
                    p.setEndDate(rs.getDate("EndDate").toLocalDate());
                    p.setTotalSessions(rs.getInt("GeneratedSessionCount"));
                    p.setPastSessions(rs.getInt("PastSessions"));
                    p.setPackageName(rs.getString("PackageNameSnapshot"));
                    p.setPartnerCustomerId(p.isCouple() ? getNullableInt(rs, "PartnerCustomerId") : null);
                    p.setPtName(rs.getString("PtName"));
                    p.setPtPhone(rs.getString("PtPhone"));
                    p.setHolderName(rs.getString("HolderName"));
                    p.setPartnerFullName(rs.getString("PartnerFullName"));
                    packages.add(p);
                }
            }
        }
        return packages;
    }



    // ============================================================================================================
    // PHỤC VỤ HÀM CHÍNH fillDisplay — quyền vào gym (buildGymWarning, canUseGym) + ngày phòng nghỉ (B1)
    //   hasGymPass / hasDayPass / isFrozen dùng chung câu COUNT countByCustomerAndDate ngay dưới
    // ============================================================================================================

    /** fillDisplay (canUseGym): khách có Membership HOẶC DayPass phủ ngày này không? */
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

    /** Khách có VÉ NGÀY phủ ngày này không? (vé ngày không bị bảo lưu chặn — CBR-117) */
    public boolean hasDayPass(int customerId, LocalDate date) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM Subscriptions
                WHERE CustomerId = ?
                  AND ? BETWEEN StartDate AND EndDate
                  AND SubscriptionType = 'DayPass' AND Status IN ('Active', 'Used', 'Expired')
                """;
        return countByCustomerAndDate(sql, customerId, date) > 0;
    }

    /** Ngày này có nằm trong kỳ BẢO LƯU Membership của khách không? */
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

    /** HÀM PHỤ (hasGymPass, hasDayPass, isFrozen) — chạy câu COUNT có 2 dấu ?: customerId, ngày. */
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

    /** fillDisplay B1: các ngày phòng NGHỈ CẢ NGÀY (Manager khai báo) trong [fromDate, toDate]. */
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
}
