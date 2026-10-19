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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO màn "Lịch tập của tôi". MỖI HÀM = 1 CÂU SQL NHỎ. DAO chỉ ĐỌC DB, KHÔNG tính toán nghiệp vụ.
 * [CHOT 19/10] Màn chỉ xem: đã bỏ các hàm ghi (hủy buổi / đổi buổi).
 *
 * TÌM NHANH (Ctrl+F gõ tag):
 *   [GOI-PT]        thẻ "Gói PT của bạn"         → findPtPackages, countUpcomingSessionsOfPackage
 *   [TEN]           tên người                     → findCustomerName, findEmployeeName
 *   [SDT-PT]        số điện thoại PT              → findEmployeePhone
 *   [CANH-BAO-GYM]  quyền vào gym (cảnh báo vàng) → hasGymPass, isFrozen
 *   [SAP-TOI]       lịch sắp tới                  → findUpcomingDays, findUpcomingSessions
 *   [LICH-SU]       lịch sử                       → findHistoryDays, findHistorySessions
 *   [PHAN-TRANG]    phân trang theo ngày          → find...Days (bước 1), find...Sessions (bước 2)
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
                   sub.SubscriptionId, sub.SubscriptionType,
                   sub.CustomerId, sub.StartDate, sub.EndDate,
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

    /** [SDT-PT] Số điện thoại của PT (Persons.Phone) để khách liên hệ khi cần. Không có → null. */
    public String findEmployeePhone(int employeeId) throws SQLException {
        String sql = """
                SELECT p.Phone
                FROM Employees e
                JOIN Persons p ON p.PersonId = e.PersonId
                WHERE e.EmployeeId = ?
                """;
        return findOneName(sql, employeeId);   // dùng chung hàm đọc 1 cột chữ
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
    public List<LocalDate> findUpcomingDays(int customerId, LocalDateTime now) throws SQLException {
        String sql = "SELECT DISTINCT t.SlotDate " + FROM_MY_SESSIONS + IS_UPCOMING + " ORDER BY t.SlotDate";
        return queryDays(sql, startParams(customerId, now));
    }

    /** [SAP-TOI][PHAN-TRANG] Bước 2: các BUỔI sắp tới trong khoảng [fromDate, toDate] (= các ngày của trang). */
    public List<MyPtSessionRow> findUpcomingSessions(int customerId, LocalDateTime now,
            LocalDate fromDate, LocalDate toDate) throws SQLException {

        String sql = SESSION_COLUMNS + FROM_MY_SESSIONS + IS_UPCOMING
                + " AND t.SlotDate BETWEEN ? AND ?"
                + " ORDER BY t.SlotDate, t.StartTime, s.SessionId";

        List<Object> params = startParams(customerId, now);
        params.add(fromDate);
        params.add(toDate);
        return querySessions(sql, params);
    }


    // =====================================================================
    // [LICH-SU] LỊCH SỬ — giống [SAP-TOI], chỉ khác IS_HISTORY và sắp MỚI NHẤT trước
    // =====================================================================

    /** [LICH-SU][PHAN-TRANG] Bước 1: các NGÀY có buổi lịch sử (mới nhất trước). */
    public List<LocalDate> findHistoryDays(int customerId, LocalDateTime now) throws SQLException {
        String sql = "SELECT DISTINCT t.SlotDate " + FROM_MY_SESSIONS + IS_HISTORY + " ORDER BY t.SlotDate DESC";
        return queryDays(sql, startParams(customerId, now));
    }

    /** [LICH-SU][PHAN-TRANG] Bước 2: các BUỔI lịch sử trong khoảng [fromDate, toDate]. */
    public List<MyPtSessionRow> findHistorySessions(int customerId, LocalDateTime now,
            LocalDate fromDate, LocalDate toDate) throws SQLException {

        String sql = SESSION_COLUMNS + FROM_MY_SESSIONS + IS_HISTORY
                + " AND t.SlotDate BETWEEN ? AND ?"
                + " ORDER BY t.SlotDate DESC, t.StartTime DESC, s.SessionId DESC";

        List<Object> params = startParams(customerId, now);
        params.add(fromDate);
        params.add(toDate);
        return querySessions(sql, params);
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

    /** Gán lần lượt giá trị vào các dấu ? (JDBC đánh số từ 1). */
    private void setParams(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            int position = i + 1;

            if (value instanceof Integer number) {
                statement.setInt(position, number);
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
