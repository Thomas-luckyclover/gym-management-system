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
 * DAO HỘP "XỬ LÝ BUỔI" trên Lịch dạy PT. Mọi hàm nhận connection của Service (có thể đang trong transaction).
 *
 * PHẦN 1. ĐỌC — PtSessionHandleService gọi khi mở hộp VÀ khi bấm Lưu (kiểm tra lại)
 *   findSession          1 buổi của PT đang đăng nhập (kèm gói, khách, hạn gói)
 *   findMySlots          khung Free của CHÍNH PT trong khoảng ngày        → "Dời buổi"
 *   findTransferSlots    khung Free của PT KHÁC đúng ngày + đúng giờ      → "Chuyển PT"
 *   findSlotById         1 khung đích PT đã chọn (bản khóa dòng)           → kiểm tra lại khi Lưu
 *   findCustomerAccounts tài khoản của khách + người tập cùng             → gửi thông báo
 *
 * PHẦN 2. GHI — chỉ chạy trong transaction của Service, KHÔNG tự commit
 *   bookSlot, releaseSlot, moveSession, insertMoveLog, cancelByPt
 */
public class PtSessionHandleDAO {


    /**
     * Câu SQL CHUNG đọc khung trống + 4 cờ, dùng cho cả 3 hàm tìm khung bên dưới (khác nhau ở dòng lọc cuối, %s).
     * Nhờ dùng chung, lúc MỞ HỘP và lúc BẤM LƯU kiểm tra đúng cùng một luật.
     *
     *   Chỉ lấy khung: Free · không bị đơn nào giữ · chưa có buổi Scheduled
     *                  · PT của khung đang làm việc VÀ tài khoản Active (PT bị khóa tài khoản không xem được lịch để dạy).
     *   4 cờ (Service đọc để quyết định khung có hợp lệ không):
     *     GymClosed     ngày đó phòng nghỉ cả ngày
     *     PtOnLeave     PT của khung có đơn nghỉ Pending/Approved trùng ca (ca sáng = bắt đầu trước 12:00)
     *     CustomerBusy  khách / người tập cùng có buổi Scheduled KHÁC chồng giờ (với bất kỳ PT nào)
     *     HasGymAccess  ít nhất 1 người trong gói có Membership/DayPass phủ ngày đó và không bảo lưu
     *
     * 7 dấu ? đầu (setCommonParams): mã buổi (bỏ chính nó khi xét trùng), rồi cặp (khách, người tập cùng) ×3 lần.
     */
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
                                       AND NOT EXISTS (SELECT 1 FROM Subscriptions m
                                                       JOIN FreezeRecords f ON f.SubscriptionId = m.SubscriptionId
                                                       WHERE m.CustomerId = gym.CustomerId
                                                         AND m.SubscriptionType = 'Membership'
                                                         AND t.SlotDate BETWEEN f.StartDate
                                                             AND COALESCE(f.ActualEndDate, f.PlannedEndDate)))
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


    // ============================================================================================================
    // PHỤC VỤ PtSessionHandleService.getHandleForm (mở hộp) — ĐỌC
    // Cũng dùng lại khi Lưu (lock = true) để kiểm lại có khóa
    // ============================================================================================================

    /**
     * [getHandleForm (lock = false); reschedule / transfer / cancel (lock = true)] Sessions + TimeSlots + Subscriptions + Orders + Customers + Persons + Employees: R 1 dòng.
     * HÀM CHÍNH — Service gọi đầu mỗi hàm (mở hộp, dời, chuyển, hủy).
     * Chỉ tìm buổi có Sessions.PtEmployeeId = ptId → PT không mở được buổi của PT khác dù sửa ?handle= trên URL.
     * lock = true (khi Lưu): UPDLOCK giữ dòng buổi tới hết transaction, 2 lần bấm Lưu phải xếp hàng.
     * Không thấy → null.
     */
    public PtHandleView findSession(Connection connection, int sessionId, int ptId, boolean lock) throws SQLException {
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
            statement.setInt(2, ptId);    // ? = mã PT lấy từ session đăng nhập, không lấy từ URL

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readSession(rs) : null;
            }
        }
    }

    /** HÀM PHỤ — findSession gọi. Một dòng SQL → PtHandleView (phần 1 + 2). */
    private PtHandleView readSession(ResultSet rs) throws SQLException {
        int partnerId = rs.getInt("PartnerCustomerId");
        Integer partner = rs.wasNull() ? null : partnerId;
        return new PtHandleView(
                rs.getInt("SessionId"),                          // Buổi + khung + PT dạy
                rs.getString("Status"),
                rs.getInt("TimeSlotId"),
                rs.getInt("PtEmployeeId"),
                rs.getString("PtName"),
                rs.getInt("SessionValue"),
                rs.getInt("CommissionAmount"),
                rs.getDate("SlotDate").toLocalDate(),
                rs.getTime("StartTime").toLocalTime(),
                rs.getTime("EndTime").toLocalTime(),
                rs.getInt("SubscriptionId"),                     // Gói + khách
                rs.getString("SubscriptionType"),
                rs.getInt("CustomerId"),
                partner,
                rs.getDate("StartDate").toLocalDate(),
                rs.getDate("EndDate").toLocalDate(),
                rs.getString("PackageNameSnapshot"),
                rs.getString("CustomerNames"));
    }

    /**
     * [getHandleForm] TimeSlots của chính PT (+ ngày nghỉ, đơn nghỉ, buổi khác của khách): R nhiều dòng → tab "Dời buổi".
     * HÀM CHÍNH — Service gọi khi mở hộp (phần "Dời buổi").
     * Khung Free của CHÍNH PT từ fromDate đến toDate. Service lọc tiếp (đã qua giờ, ngoài hạn gói, 4 cờ).
     */
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
     * HÀM PHỤ — 3 hàm tìm khung gọi. Điền 7 dấu ? đầu của SLOT_SQL, trả số thứ tự dấu ? tiếp theo.
     * Gói 1 người: người tập cùng = chính khách đó (để IN (?, ?) vẫn đúng).
     */
    private int setCommonParams(PreparedStatement statement, PtHandleView session) throws SQLException {
        int customer = session.getCustomerId();
        int partner = session.getPartnerCustomerId() == null ? customer : session.getPartnerCustomerId();

        statement.setInt(1, session.getSessionId());   // CustomerBusy: bỏ qua chính buổi đang xử lý
        statement.setInt(2, customer);
        statement.setInt(3, partner);
        statement.setInt(4, customer);
        statement.setInt(5, partner);
        statement.setInt(6, customer);                 // HasGymAccess
        statement.setInt(7, partner);
        return 8;
    }

    /** HÀM PHỤ — chạy câu SQL khung, mỗi dòng → 1 PtHandleSlot. */
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

    /**
     * [getHandleForm] TimeSlots của PT khác đúng giờ buổi: R nhiều dòng → tab "Chuyển PT".
     * HÀM CHÍNH — Service gọi khi mở hộp (phần "Chuyển PT").
     * Khung Free của PT KHÁC có ĐÚNG ngày + giờ bắt đầu + giờ kết thúc của buổi (chuyển PT giữ nguyên giờ).
     */
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

    /**
     * [reschedule, transfer] TimeSlots: R 1 dòng có khóa (khung đích phải còn trống lúc Lưu).
     * HÀM CHÍNH — Service gọi khi bấm Lưu (Dời buổi / Chuyển PT).
     * Đọc lại ĐÚNG khung PT đã chọn, có khóa dòng: từ lúc này tới commit không ai giữ/đặt/gỡ được khung đó.
     * Khung không còn Free (vừa có người lấy) → null → Service báo MSG103.
     */
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


    // ============================================================================================================
    // PHỤC VỤ reschedule / transfer / cancel (bấm Lưu trong hộp) — GHI trong transaction của Service
    // ============================================================================================================

    /**
     * [reschedule, transfer] TimeSlots: U khung đích Free → Booked.
     * Khung đích Free → Booked. Điều kiện Status = 'Free' chặn 2 người cùng lấy 1 khung.
     */
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

    /**
     * [reschedule, transfer, cancel] TimeSlots: U khung cũ Booked → Free.
     * Khung cũ Booked → Free: khung lại hiện "Còn trống" trên lịch PT (chốt A: cả dời, chuyển, hủy).
     */
    public void releaseSlot(Connection connection, int timeSlotId) throws SQLException {
        String sql = "UPDATE TimeSlots SET Status = 'Free' WHERE TimeSlotId = ? AND Status = 'Booked'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, timeSlotId);
            statement.executeUpdate();
        }
    }

    /**
     * [reschedule, transfer] Sessions: U TimeSlotId / PtEmployeeId / CommissionAmount (buổi còn Scheduled).
     * Gắn buổi sang khung mới (+ PT mới, hoa hồng mới khi chuyển PT).
     * Dời buổi: newPtId = PT cũ, commission = hoa hồng cũ. Chỉ đổi khi buổi còn Scheduled.
     */
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

    /**
     * [reschedule, transfer] SessionMoveLogs: C 1 dòng (ai dời, từ đâu → đâu, lý do).
     * Ghi 1 dòng SessionMoveLogs. Màn khách (Gói của tôi) đọc bảng này để hiện "PT dời lịch" / "Chuyển PT dạy thay".
     * moveType: PtReschedule (PT cũ = PT mới) | PtTransfer (PT khác) — CHECK trong DB bắt đúng cặp này.
     */
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

    /**
     * [cancel] Sessions: U Scheduled → Cancelled (CancelType = ByPt, lý do, CancelledAt).
     * PT hủy buổi (CancelType = ByPt). CHECK trong DB bắt buộc: lý do ≥ 5 ký tự, RefundAgreed = 1, có người hủy.
     * Vẫn tính là buổi đã dùng và PT vẫn có hoa hồng (CommissionAmount không đổi).
     */
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


    /**
     * [reschedule, transfer, cancel → notify] Customers + Accounts: R tài khoản khách để gửi thông báo.
     * HÀM CHÍNH — Service gọi sau khi ghi xong, để gửi thông báo.
     * Tài khoản Active của khách + người tập cùng. Người không có tài khoản (hồ sơ phụ thuộc) → không có trong danh sách.
     */
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
