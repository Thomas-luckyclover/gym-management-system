package com.gms.dao;

import com.gms.dto.PtHandleSlot;
import com.gms.dto.PtHandleView;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO hộp XỬ LÝ BUỔI — đọc buổi, tìm khung nhận buổi, ghi Dời / Chuyển / Hủy.
 */
public class PtSessionHandleDAO {
    // Câu đọc khung trống dùng chung 3 hàm find…Slots (chỗ %s: gợi ý khóa + điều kiện riêng).
    // Mỗi khung Free, chưa giữ, chưa có buổi, của PT đang làm, kèm 4 cờ:
    //   GymClosed (phòng nghỉ) · PtOnLeave (PT có đơn nghỉ trùng ca) · CustomerBusy (khách có buổi khác chồng giờ)
    //   · HasGymAccess (CBR-117: khách có Membership không bảo lưu, HOẶC có DayPass, ngày đó).
    private static final String SLOT_SQL = """
            SELECT t.TimeSlotId, t.PtEmployeeId, p.FullName AS PtName, e.CommissionRate,
                   t.SlotDate, t.StartTime, t.EndTime,
                   CASE WHEN EXISTS (SELECT 1 FROM OperatingHourExceptions x
                                     WHERE x.ApplyDate = t.SlotDate AND x.IsClosedAllDay = 1)
                        THEN 1 ELSE 0 END AS GymClosed,
                   CASE WHEN EXISTS (SELECT 1 FROM LeaveRequests lr
                                     WHERE lr.EmployeeId = t.PtEmployeeId AND t.SlotDate BETWEEN lr.FromDate AND lr.ToDate
                                       AND lr.Status IN ('Pending', 'Approved')
                                       -- mốc ca 13:00 = AppConstants.AFTERNOON_SHIFT_START (khung thuộc ca theo giờ bắt đầu)
                                       AND (lr.ShiftType = 'FullDay'
                                            OR (lr.ShiftType = 'Morning' AND t.StartTime < '13:00')
                                            OR (lr.ShiftType = 'Afternoon' AND t.StartTime >= '13:00')))
                        THEN 1 ELSE 0 END AS PtOnLeave,
                   CASE WHEN EXISTS (SELECT 1 FROM Sessions s2
                                     JOIN TimeSlots t2       ON t2.TimeSlotId = s2.TimeSlotId
                                     JOIN Subscriptions sub2 ON sub2.SubscriptionId = s2.SubscriptionId
                                     JOIN Orders o2          ON o2.OrderId = sub2.OrderId
                                     WHERE s2.Status = 'Scheduled' AND s2.SessionId <> ?
                                       AND t2.SlotDate = t.SlotDate
                                       AND t2.StartTime < t.EndTime AND t.StartTime < t2.EndTime
                                       AND (sub2.CustomerId IN (?, ?)
                                            OR (sub2.SubscriptionType = 'PTCouple' AND o2.PartnerCustomerId IN (?, ?))))
                        THEN 1 ELSE 0 END AS CustomerBusy,
                   CASE WHEN EXISTS (SELECT 1 FROM Subscriptions gym
                                     WHERE gym.CustomerId IN (?, ?)
                                       AND gym.SubscriptionType IN ('Membership', 'DayPass')
                                       AND gym.Status IN ('Active', 'Frozen')
                                       AND t.SlotDate BETWEEN gym.StartDate AND gym.EndDate
                                       -- bảo lưu chỉ chặn gói thành viên; vé ngày vẫn tính (CBR-117)
                                       AND (gym.SubscriptionType = 'DayPass' OR NOT EXISTS (SELECT 1 FROM Subscriptions m
                                                       JOIN FreezeRecords f ON f.SubscriptionId = m.SubscriptionId
                                                       WHERE m.CustomerId = gym.CustomerId
                                                         AND m.SubscriptionType = 'Membership'
                                                         AND t.SlotDate BETWEEN f.StartDate
                                                             AND COALESCE(f.ActualEndDate, f.PlannedEndDate))))
                        THEN 1 ELSE 0 END AS HasGymAccess
            FROM TimeSlots t %s
            JOIN Employees e ON e.EmployeeId = t.PtEmployeeId AND e.Position = 'PT' AND e.Status = 'Working'
            JOIN Persons p   ON p.PersonId = e.PersonId
            JOIN Accounts acc ON acc.PersonId = e.PersonId AND acc.Status = 'Active'
            WHERE t.Status = 'Free' AND t.HeldByOrderId IS NULL
              AND NOT EXISTS (SELECT 1 FROM Sessions x WHERE x.TimeSlotId = t.TimeSlotId AND x.Status = 'Scheduled')
              AND %s
            ORDER BY t.SlotDate, t.StartTime, p.FullName
            """;

    // 1 buổi của PT (Sessions + khung + gói + khách + người tập cùng + PT). lock = true → KHÓA dòng buổi (UPDLOCK).
    // Trả về: PtHandleView | null (không phải buổi của PT này).
    public PtHandleView findSession(
            Connection connection,
            int sessionId,
            int ptId,
            boolean lock) throws SQLException {
        String sql = """
                SELECT s.SessionId, s.Status, s.TimeSlotId, s.PtEmployeeId, s.SessionValue, s.CommissionAmount,
                       t.SlotDate, t.StartTime, t.EndTime,
                       sub.SubscriptionId, sub.SubscriptionType, sub.CustomerId, sub.StartDate, sub.EndDate,
                       o.PackageNameSnapshot,
                       CASE WHEN sub.SubscriptionType = 'PTCouple' THEN o.PartnerCustomerId END AS PartnerCustomerId,
                       CASE WHEN partnerPerson.FullName IS NULL THEN holderPerson.FullName
                            ELSE CONCAT(holderPerson.FullName, N' & ', partnerPerson.FullName) END AS CustomerNames,
                       ptPerson.FullName AS PtName
                FROM Sessions s %s
                JOIN TimeSlots t       ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o          ON o.OrderId = sub.OrderId
                JOIN Customers holder  ON holder.CustomerId = sub.CustomerId
                JOIN Persons holderPerson ON holderPerson.PersonId = holder.PersonId
                LEFT JOIN Customers partner ON partner.CustomerId = o.PartnerCustomerId AND sub.SubscriptionType = 'PTCouple'
                LEFT JOIN Persons partnerPerson ON partnerPerson.PersonId = partner.PersonId
                JOIN Employees e       ON e.EmployeeId = s.PtEmployeeId
                JOIN Persons ptPerson  ON ptPerson.PersonId = e.PersonId
                WHERE s.SessionId = ? AND s.PtEmployeeId = ?
                """.formatted(lock ? "WITH (UPDLOCK, HOLDLOCK)" : "");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, sessionId);
            statement.setInt(2, ptId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readSession(rs) : null;
            }
        }
    }

    /**
     * HÀM PHỤ — 1 dòng → PtHandleView (tên khách "A & B" nếu gói đôi).
     */
    private PtHandleView readSession(ResultSet rs) throws SQLException {
        int partnerId = rs.getInt("PartnerCustomerId");
        Integer partner = rs.wasNull() ? null : partnerId;
        return new PtHandleView(
                rs.getInt("SessionId"),
                rs.getString("Status"),
                rs.getInt("TimeSlotId"),
                rs.getInt("PtEmployeeId"),
                rs.getString("PtName"),
                rs.getInt("SessionValue"),
                rs.getInt("CommissionAmount"),
                rs.getDate("SlotDate").toLocalDate(),
                rs.getTime("StartTime").toLocalTime(),
                rs.getTime("EndTime").toLocalTime(),
                rs.getInt("SubscriptionId"),
                rs.getString("SubscriptionType"),
                rs.getInt("CustomerId"),
                partner,
                rs.getDate("StartDate").toLocalDate(),
                rs.getDate("EndDate").toLocalDate(),
                rs.getString("PackageNameSnapshot"),
                rs.getString("CustomerNames"));
    }

    // Tab Dời buổi: khung trống của CHÍNH PT trong [fromDate, toDate].
    public List<PtHandleSlot> findMySlots(Connection connection, PtHandleView session, int ptId,
            LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = SLOT_SQL.formatted("", "t.PtEmployeeId = ? AND t.SlotDate BETWEEN ? AND ?");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = setCommonParams(statement, session);
            statement.setInt(next, ptId);
            statement.setDate(next + 1, Date.valueOf(fromDate));
            statement.setDate(next + 2, Date.valueOf(toDate));
            return readSlots(statement);
        }
    }

    /**
     * HÀM PHỤ — điền 7 tham số đầu của SLOT_SQL (buổi đang xử lý, khách, người tập cùng — gói đơn thì điền khách 2 lần).
     * Trả về: số thứ tự tham số tiếp theo (8).
     */
    private int setCommonParams(PreparedStatement statement, PtHandleView session) throws SQLException {
        int customer = session.getCustomerId();
        int partner = session.getPartnerCustomerId() == null ? customer : session.getPartnerCustomerId();

        statement.setInt(1, session.getSessionId());
        statement.setInt(2, customer);
        statement.setInt(3, partner);
        statement.setInt(4, customer);
        statement.setInt(5, partner);
        statement.setInt(6, customer);
        statement.setInt(7, partner);
        return 8;
    }

    /**
     * HÀM PHỤ — chạy câu, mỗi dòng → PtHandleSlot.
     */
    private List<PtHandleSlot> readSlots(PreparedStatement statement) throws SQLException {
        List<PtHandleSlot> rows = new ArrayList<>();

        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                PtHandleSlot slot = new PtHandleSlot(
                        rs.getInt("TimeSlotId"),
                        rs.getInt("PtEmployeeId"),
                        rs.getString("PtName"),
                        rs.getInt("CommissionRate"),
                        rs.getDate("SlotDate").toLocalDate(),
                        rs.getTime("StartTime").toLocalTime(),
                        rs.getTime("EndTime").toLocalTime(),
                        rs.getBoolean("GymClosed"),
                        rs.getBoolean("PtOnLeave"),
                        rs.getBoolean("CustomerBusy"),
                        rs.getBoolean("HasGymAccess"));
                rows.add(slot);
            }
        }
        return rows;
    }

    // Tab Chuyển PT: khung trống của PT KHÁC đúng ngày + giờ bắt đầu + giờ kết thúc của buổi.
    public List<PtHandleSlot> findTransferSlots(Connection connection, PtHandleView session, int ptId)
            throws SQLException {
        String sql = SLOT_SQL.formatted("", "t.PtEmployeeId <> ? AND t.SlotDate = ? "
                + "AND t.StartTime = CAST(? AS time) AND t.EndTime = CAST(? AS time)");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = setCommonParams(statement, session);
            statement.setInt(next, ptId);
            statement.setDate(next + 1, Date.valueOf(session.getSlotDate()));
            statement.setString(next + 2, session.getStartTime().toString());
            statement.setString(next + 3, session.getEndTime().toString());
            return readSlots(statement);
        }
    }

    // Khung đích khi bấm Dời / Chuyển, KHÓA (UPDLOCK, HOLDLOCK). null = không còn trống.
    public PtHandleSlot findSlotById(Connection connection, PtHandleView session, int timeSlotId)
            throws SQLException {
        String sql = SLOT_SQL.formatted("WITH (UPDLOCK, HOLDLOCK)", "t.TimeSlotId = ?");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = setCommonParams(statement, session);
            statement.setInt(next, timeSlotId);
            List<PtHandleSlot> rows = readSlots(statement);
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    // TimeSlots: Free → Booked (chỉ khi còn Free, không bị giữ). Trả về: true = lấy được khung.
    public boolean bookSlot(Connection connection, int timeSlotId) throws SQLException {
        String sql = """
                UPDATE TimeSlots SET Status = 'Booked'
                WHERE TimeSlotId = ? AND Status = 'Free' AND HeldByOrderId IS NULL
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, timeSlotId);
            return statement.executeUpdate() == 1;
        }
    }

    // TimeSlots: Booked → Free (khung cũ sau khi dời / hủy).
    public void releaseSlot(Connection connection, int timeSlotId) throws SQLException {
        String sql = "UPDATE TimeSlots SET Status = 'Free' WHERE TimeSlotId = ? AND Status = 'Booked'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, timeSlotId);
            statement.executeUpdate();
        }
    }

    // Sessions: đổi khung, PT, hoa hồng (chỉ khi buổi còn Scheduled). Trả về: true = đổi được.
    public boolean moveSession(Connection connection, int sessionId, int newSlotId, int newPtId, int commission)
            throws SQLException {
        String sql = """
                UPDATE Sessions SET TimeSlotId = ?, PtEmployeeId = ?, CommissionAmount = ?
                WHERE SessionId = ? AND Status = 'Scheduled'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, newSlotId);
            statement.setInt(2, newPtId);
            statement.setInt(3, commission);
            statement.setInt(4, sessionId);
            return statement.executeUpdate() == 1;
        }
    }

    // SessionMoveLogs: thêm 1 dòng lịch sử dời (màn Gói của tôi đọc để ghi "PT dời lịch · thay buổi …").
    public void insertMoveLog(Connection connection, PtHandleView session, PtHandleSlot target, String moveType,
            int accountId, String reason, LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO SessionMoveLogs (SessionId, MoveType, OldTimeSlotId, NewTimeSlotId,
                                             OldPtEmployeeId, NewPtEmployeeId, RequestedByAccountId, Reason, MovedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, session.getSessionId());
            statement.setString(2, moveType);
            statement.setInt(3, session.getTimeSlotId());
            statement.setInt(4, target.getTimeSlotId());
            statement.setInt(5, session.getPtEmployeeId());
            statement.setInt(6, target.getPtEmployeeId());
            statement.setInt(7, accountId);
            if (reason == null) {
                statement.setNull(8, Types.NVARCHAR);
            } else {
                statement.setNString(8, reason);
            }
            statement.setTimestamp(9, Timestamp.valueOf(now.withNano(0)));
            statement.executeUpdate();
        }
    }

    // Sessions: Scheduled → Cancelled, CancelType = ByPt, lý do, thời điểm, tài khoản hủy. Trả về: true = hủy được.
    public boolean cancelByPt(Connection connection, int sessionId, String reason, int accountId, LocalDateTime now)
            throws SQLException {
        String sql = """
                UPDATE Sessions
                SET Status = 'Cancelled', CancelType = 'ByPt', CancelReason = ?, CancelledAt = ?,
                    CancelledByAccountId = ?, RefundAgreed = 1
                WHERE SessionId = ? AND Status = 'Scheduled'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setNString(1, reason);
            statement.setTimestamp(2, Timestamp.valueOf(now.withNano(0)));
            statement.setInt(3, accountId);
            statement.setInt(4, sessionId);
            return statement.executeUpdate() == 1;
        }
    }

    // Tài khoản Active của khách + người tập cùng (để gửi thông báo).
    public List<Integer> findCustomerAccounts(Connection connection, PtHandleView session) throws SQLException {
        String sql = """
                SELECT DISTINCT a.AccountId
                FROM Customers c
                JOIN Accounts a ON a.PersonId = c.PersonId AND a.Status = 'Active'
                WHERE c.CustomerId IN (?, ?)
                """;

        List<Integer> accountIds = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int customer = session.getCustomerId();
            statement.setInt(1, customer);
            statement.setInt(2, session.getPartnerCustomerId() == null ? customer : session.getPartnerCustomerId());

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    accountIds.add(rs.getInt("AccountId"));
                }
            }
        }
        return accountIds;
    }
}
