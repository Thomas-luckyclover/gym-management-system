package com.gms.dao;

import com.gms.dto.HourException;
import com.gms.dto.LeaveInfo;
import com.gms.dto.PtScheduleItem;
import com.gms.dto.PtSlotDraft;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO màn LỊCH DẠY — đọc lịch tuần, ghi khung khi Công bố. Một số hàm dùng chung với Xử lý buổi và Bán gói PT
 * (findLeaves, findHourExceptions, lockPtForPublish).
 */
public class PtScheduleDAO {
    // Buổi có khách của PT trong khoảng ngày: Sessions JOIN TimeSlots, Subscriptions, Orders, khách (+ người tập cùng PTCouple).
    // SessionNo = số thứ tự buổi trong gói (ROW_NUMBER) → "Buổi n/N".
    private static final String SESSIONS_SQL = """
            WITH NumberedSessions AS (
                SELECT s.SessionId, s.Status AS SessionStatus, s.PtEmployeeId,
                       t.TimeSlotId, t.SlotDate, t.StartTime, t.EndTime,
                       sub.SubscriptionType, sub.CustomerId, sub.GeneratedSessionCount,
                       o.PackageNameSnapshot, o.PartnerCustomerId,
                       ROW_NUMBER() OVER (PARTITION BY s.SubscriptionId
                                          ORDER BY t.SlotDate, t.StartTime, s.SessionId) AS SessionNo
                FROM Sessions s
                JOIN TimeSlots t       ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o          ON o.OrderId = sub.OrderId
            )
            SELECT ns.TimeSlotId, ns.SlotDate, ns.StartTime, ns.EndTime,
                   ns.SessionId, ns.SessionStatus,
                   ns.PackageNameSnapshot, ns.SessionNo, ns.GeneratedSessionCount,
                   CASE WHEN partnerPerson.FullName IS NULL THEN holderPerson.FullName
                        ELSE CONCAT(holderPerson.FullName, N' & ', partnerPerson.FullName)
                   END AS CustomerNames,
                   holderPerson.Phone AS CustomerPhone
            FROM NumberedSessions ns
            JOIN Customers holder           ON holder.CustomerId = ns.CustomerId
            JOIN Persons holderPerson       ON holderPerson.PersonId = holder.PersonId
            LEFT JOIN Customers partner     ON partner.CustomerId = ns.PartnerCustomerId
                                               AND ns.SubscriptionType = 'PTCouple'
            LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
            WHERE ns.PtEmployeeId = ?
              AND ns.SlotDate BETWEEN ? AND ?
            ORDER BY ns.SlotDate, ns.StartTime, ns.SessionId
            """;

    // Tài khoản → EmployeeId (Position = PT, Status = Working). null = không phải PT đang làm.
    public Integer findPtEmployeeId(int accountId) throws SQLException {
        String sql = """
                SELECT e.EmployeeId
                FROM Accounts a
                JOIN Persons p   ON p.PersonId = a.PersonId
                JOIN Employees e ON e.PersonId = p.PersonId
                WHERE a.AccountId = ?
                  AND e.Position = 'PT'
                  AND e.Status = 'Working'
                """;

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("EmployeeId");
                }
            }
        }
        return null;
    }

    // Chạy SESSIONS_SQL (? PT, từ ngày, tới ngày) → List<PtScheduleItem> có sessionId.
    public List<PtScheduleItem> findSessions(int ptEmployeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        List<PtScheduleItem> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(SESSIONS_SQL)) {
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readSessionRow(rs));
                }
            }
        }
        return rows;
    }

    /**
     * HÀM PHỤ — 1 dòng buổi → PtScheduleItem (tên khách "A & B" nếu gói đôi).
     */
    private PtScheduleItem readSessionRow(ResultSet rs) throws SQLException {
        return new PtScheduleItem(
                rs.getInt("SessionId"),
                rs.getString("SessionStatus"),
                rs.getInt("TimeSlotId"),
                rs.getDate("SlotDate").toLocalDate(),
                rs.getTime("StartTime").toLocalTime(),
                rs.getTime("EndTime").toLocalTime(),
                rs.getString("PackageNameSnapshot"),
                rs.getInt("SessionNo"),
                rs.getInt("GeneratedSessionCount"),
                rs.getString("CustomerNames"),
                rs.getString("CustomerPhone"));
    }

    // Khung của PT trong khoảng ngày còn Free / Held và chưa có buổi Scheduled → List<PtScheduleItem> (kind = Free | Held).
    public List<PtScheduleItem> findOpenSlots(int ptEmployeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT t.TimeSlotId, t.SlotDate, t.StartTime, t.EndTime, t.Status
                FROM TimeSlots t
                WHERE t.PtEmployeeId = ?
                  AND t.SlotDate BETWEEN ? AND ?
                  AND t.Status IN ('Free', 'Held')
                  AND NOT EXISTS (
                      SELECT 1 FROM Sessions s
                      WHERE s.TimeSlotId = t.TimeSlotId
                        AND s.Status = 'Scheduled'
                  )
                ORDER BY t.SlotDate, t.StartTime
                """;

        List<PtScheduleItem> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readSlot(rs));
                }
            }
        }
        return rows;
    }

    /**
     * HÀM PHỤ — 1 dòng khung → PtScheduleItem.
     */
    private PtScheduleItem readSlot(ResultSet rs) throws SQLException {
        return new PtScheduleItem(
                rs.getInt("TimeSlotId"),
                rs.getDate("SlotDate").toLocalDate(),
                rs.getTime("StartTime").toLocalTime(),
                rs.getTime("EndTime").toLocalTime(),
                rs.getString("Status"));
    }

    // Đơn nghỉ Pending / Approved của PT giao với khoảng ngày (tự mở kết nối).
    public List<LeaveInfo> findLeaves(int employeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return findLeaves(
                    connection,
                    employeeId,
                    fromDate,
                    toDate);
        }
    }

    // Như trên nhưng trong transaction đang mở (WITH HOLDLOCK: không cho duyệt / thêm đơn nghỉ giữa chừng).
    // Mỗi đơn nhiều ngày → tách thành từng LeaveInfo 1 ngày (chỉ ngày nằm trong khoảng).
    public List<LeaveInfo> findLeaves(Connection connection, int employeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT lr.FromDate, lr.ToDate, lr.ShiftType, lr.Status, lr.Reason
                FROM LeaveRequests lr WITH (HOLDLOCK)
                WHERE lr.EmployeeId = ?
                  AND lr.FromDate <= ? AND lr.ToDate >= ?
                  AND lr.Status IN ('Pending', 'Approved')
                ORDER BY lr.FromDate, lr.ShiftType
                """;

        List<LeaveInfo> rows = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, employeeId);
            statement.setDate(2, Date.valueOf(toDate));
            statement.setDate(3, Date.valueOf(fromDate));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    LocalDate first = rs.getDate("FromDate").toLocalDate();
                    LocalDate last = rs.getDate("ToDate").toLocalDate();
                    LocalDate day = first.isBefore(fromDate) ? fromDate : first;
                    LocalDate end = last.isAfter(toDate) ? toDate : last;
                    for (; !day.isAfter(end); day = day.plusDays(1)) {
                        rows.add(readLeave(rs, day));
                    }
                }
            }
        }
        return rows;
    }

    /**
     * HÀM PHỤ — 1 đơn + 1 ngày → LeaveInfo.
     */
    private LeaveInfo readLeave(ResultSet rs, LocalDate day) throws SQLException {
        return new LeaveInfo(
                day,
                rs.getString("ShiftType"),
                rs.getString("Status"),
                rs.getString("Reason"));
    }

    // Ngày giờ đặc biệt (OperatingHourExceptions) trong khoảng ngày (tự mở kết nối).
    public List<HourException> findHourExceptions(LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return findHourExceptions(connection, fromDate, toDate);
        }
    }

    // Như trên trong transaction (WITH HOLDLOCK).
    public List<HourException> findHourExceptions(Connection connection, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT ApplyDate, IsClosedAllDay, OpenTime, CloseTime
                FROM OperatingHourExceptions WITH (HOLDLOCK)
                WHERE ApplyDate BETWEEN ? AND ?
                ORDER BY ApplyDate
                """;

        List<HourException> rows = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(fromDate));
            statement.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readHourException(rs));
                }
            }
        }
        return rows;
    }

    /**
     * HÀM PHỤ — 1 dòng → HourException (giờ mở / đóng có thể NULL khi nghỉ cả ngày).
     */
    private HourException readHourException(ResultSet rs) throws SQLException {
        Time openTime = rs.getTime("OpenTime");
        Time closeTime = rs.getTime("CloseTime");
        return new HourException(
                rs.getDate("ApplyDate").toLocalDate(),
                rs.getBoolean("IsClosedAllDay"),
                openTime == null ? null : openTime.toLocalTime(),
                closeTime == null ? null : closeTime.toLocalTime());
    }

    // KHÓA dòng Employees của PT (UPDLOCK, HOLDLOCK) tới hết transaction: Công bố lịch, Xử lý buổi, Bán gói PT cùng PT phải xếp hàng.
    // Trả về: EmployeeId (tài khoản Active, PT Working) | null.
    public Integer lockPtForPublish(Connection connection, int accountId) throws SQLException {
        String sql = """
                SELECT e.EmployeeId
                FROM Employees e WITH (UPDLOCK, HOLDLOCK)
                JOIN Persons p ON p.PersonId = e.PersonId
                JOIN Accounts a ON a.PersonId = p.PersonId
                WHERE a.AccountId = ? AND a.Status = 'Active'
                  AND e.Position = 'PT' AND e.Status = 'Working'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("EmployeeId") : null;
            }
        }
    }

    // Khóa đọc P-06, P-19 trong SystemConfigs tới hết transaction.
    public void lockScheduleConfigs(Connection connection) throws SQLException {
        String sql = """
                SELECT ConfigKey FROM SystemConfigs WITH (HOLDLOCK)
                WHERE ConfigKey IN ('P-06', 'P-19')
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
            }
        }
    }

    // Mọi khung trong khoảng ngày của PT (kể cả khung PT khác mà PT này đang dạy buổi Scheduled), KHÓA (UPDLOCK, HOLDLOCK).
    // Khung có buổi Scheduled → Status "Booked". Dùng để kiểm khung nháp không chồng giờ.
    public List<PtScheduleItem> findSlotsForPublish(Connection connection, int ptId,
            LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT t.TimeSlotId, t.SlotDate, t.StartTime, t.EndTime,
                       CASE WHEN EXISTS (SELECT 1 FROM Sessions s WITH (HOLDLOCK)
                            WHERE s.TimeSlotId = t.TimeSlotId AND s.PtEmployeeId = ?
                              AND s.Status = 'Scheduled') THEN 'Booked' ELSE t.Status END AS Status
                FROM TimeSlots t WITH (UPDLOCK, HOLDLOCK)
                WHERE t.SlotDate BETWEEN ? AND ?
                  AND (t.PtEmployeeId = ? OR EXISTS (
                      SELECT 1 FROM Sessions s WITH (HOLDLOCK)
                      WHERE s.TimeSlotId = t.TimeSlotId AND s.PtEmployeeId = ? AND s.Status = 'Scheduled'))
                ORDER BY t.SlotDate, t.StartTime
                """;

        List<PtScheduleItem> rows = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));
            statement.setInt(4, ptId);
            statement.setInt(5, ptId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readSlot(rs));
                }
            }
        }
        return rows;
    }

    // Gỡ 1 khung: TimeSlots Free → Withdrawn, chỉ khi của PT này, chưa tới giờ, không có buổi Scheduled. Trả về: true = gỡ được.
    public boolean withdrawFreeSlot(Connection connection, int ptId, int timeSlotId, LocalDateTime now)
            throws SQLException {
        String sql = """
                UPDATE TimeSlots SET Status = 'Withdrawn'
                WHERE TimeSlotId = ? AND PtEmployeeId = ? AND Status = 'Free'
                  AND (SlotDate > ? OR (SlotDate = ? AND StartTime > CAST(? AS time)))
                  AND NOT EXISTS (SELECT 1 FROM Sessions s
                                  WHERE s.TimeSlotId = TimeSlots.TimeSlotId AND s.Status = 'Scheduled')
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, timeSlotId);
            statement.setInt(2, ptId);
            statement.setDate(3, Date.valueOf(now.toLocalDate()));
            statement.setDate(4, Date.valueOf(now.toLocalDate()));
            statement.setString(5, now.toLocalTime().withNano(0).toString());
            return statement.executeUpdate() == 1;
        }
    }

    // Id khung của PT đúng ngày + giờ bắt đầu (UQ_TimeSlots_PtDateStart → tối đa 1 dòng). null = chưa có.
    // Dùng sau insertOrReopenSlot để lấy id khung vừa tạo (Dời buổi → "Giờ khác").
    public Integer findSlotIdByStart(Connection connection, int ptId, LocalDate date, LocalTime start)
            throws SQLException {
        String sql = """
                SELECT TimeSlotId FROM TimeSlots
                WHERE PtEmployeeId = ? AND SlotDate = ? AND StartTime = CAST(? AS time)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(date));
            statement.setString(3, start.toString());

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("TimeSlotId") : null;
            }
        }
    }

    // Công bố 1 khung nháp:
    public void insertOrReopenSlot(Connection connection, int ptId, PtSlotDraft draft) throws SQLException {
        // B1: có khung Withdrawn đúng ngày + giờ bắt đầu + giờ kết thúc → mở lại: Withdrawn → Free (xong)
        //     Lớp chặn cuối trong SQL (giống B2): KHÔNG mở lại nếu đang có khung khác còn hiệu lực chồng giờ
        //     → rơi xuống B2, B2 cũng không chèn được → báo "trùng khung". (Service validateDrafts đã kiểm trước.)
        String reopenSql = """
                UPDATE TimeSlots SET Status = 'Free'
                WHERE PtEmployeeId = ? AND SlotDate = ? AND StartTime = CAST(? AS time) AND EndTime = CAST(? AS time)
                  AND Status = 'Withdrawn'
                  AND NOT EXISTS (
                      SELECT 1 FROM TimeSlots other
                      WHERE other.PtEmployeeId = TimeSlots.PtEmployeeId AND other.SlotDate = TimeSlots.SlotDate
                        AND other.TimeSlotId <> TimeSlots.TimeSlotId AND other.Status <> 'Withdrawn'
                        AND other.StartTime < TimeSlots.EndTime AND other.EndTime > TimeSlots.StartTime
                  )
                """;

        try (PreparedStatement statement = connection.prepareStatement(reopenSql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(draft.getSlotDate()));
            statement.setString(3, draft.getStartTime().toString());
            statement.setString(4, draft.getEndTime().toString());

            boolean reopened = statement.executeUpdate() == 1;
            if (reopened) {
                return;
            }
        }

        // B2: không có → INSERT TimeSlots Free, chỉ khi không chồng khung còn hiệu lực nào (không chèn được → lỗi "trùng khung")
        String insertSql = """
                INSERT INTO TimeSlots (PtEmployeeId, SlotDate, StartTime, EndTime, Status)
                SELECT ?, ?, CAST(? AS time), CAST(? AS time), 'Free'
                WHERE NOT EXISTS (
                    SELECT 1 FROM TimeSlots t
                    WHERE t.PtEmployeeId = ? AND t.SlotDate = ? AND t.Status <> 'Withdrawn'
                      AND t.StartTime < CAST(? AS time) AND t.EndTime > CAST(? AS time)
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(draft.getSlotDate()));
            statement.setString(3, draft.getStartTime().toString());
            statement.setString(4, draft.getEndTime().toString());

            statement.setInt(5, ptId);
            statement.setDate(6, Date.valueOf(draft.getSlotDate()));
            statement.setString(7, draft.getEndTime().toString());
            statement.setString(8, draft.getStartTime().toString());
            int inserted = statement.executeUpdate();
            if (inserted == 0) {
                throw new IllegalArgumentException("Khung giờ này trùng với khung giờ đã có ("
                        + draft.getStartTime() + "–" + draft.getEndTime() + ").");
            }
        }
    }
}
