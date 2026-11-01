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
import java.util.ArrayList;
import java.util.List;

/**
 * DAO MÀN LỊCH DẠY PT (/pt/schedule). Chỉ đọc/ghi DB, KHÔNG tính nghiệp vụ (việc đó của Service).
 *
 * PHẦN 1. ĐỌC LỊCH TUẦN — PtScheduleService.getWeek gọi
 *   findPtEmployeeId     tài khoản đăng nhập → mã PT
 *   findSessions         các buổi có khách trong tuần (kèm tên khách, gói)
 *   findOpenSlots        các khung Free / Held trong tuần
 *   findLeaves           đơn nghỉ Pending / Approved
 *   findHourExceptions   ngày phòng đổi giờ hoặc nghỉ cả ngày
 *
 * PHẦN 2. CÔNG BỐ — PtScheduleService.publishInTransaction gọi, dùng chung MỘT connection (transaction)
 *   lockPtForPublish     khóa hồ sơ PT để hai lần Công bố không chạy cùng lúc
 *   lockScheduleConfigs  giữ P-06 / P-19 không đổi trong lúc công bố
 *   withdrawFreeSlot     GỠ khung: Free → Withdrawn
 *   findSlotsForPublish  các khung đang có, để kiểm tra trùng giờ
 *   insertOrReopenSlot   THÊM khung: mở lại khung Withdrawn cùng giờ, hoặc INSERT mới
 */
public class PtScheduleDAO {


    // ================================================================================================================
    // PHẦN 1. ĐỌC LỊCH TUẦN — mỗi hàm mở connection riêng, đọc xong tự đóng
    // ================================================================================================================

    /**
     * HÀM CHÍNH — Service gọi ở B1 của getWeek.
     * Tài khoản → mã nhân viên PT đang làm việc. Không phải PT / đã nghỉ việc → null.
     */
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

            statement.setInt(1, accountId);   // ? = tài khoản lấy từ session, không lấy từ URL

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("EmployeeId");
                }
            }
        }
        return null;
    }


    /**
     * Câu SQL đọc buổi có khách. Giữ MỌI trạng thái (Scheduled / CheckedIn / Missed / Cancelled).
     *
     *   Bước 1 (WITH ... AS): đánh số buổi #1, #2... theo giờ trong TỪNG GÓI, trên toàn bộ gói.
     *                         Nhờ vậy buổi 20/45 vẫn là "20" dù tuần này chỉ thấy vài buổi.
     *   Bước 2:               JOIN tên khách (gói đôi ghép "A & B"), rồi mới lọc PT + khoảng ngày.
     *
     * 3 dấu ?: mã PT, từ ngày, đến ngày.
     */
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

    /**
     * HÀM CHÍNH — Service gọi ở B4 của getWeek.
     * Các buổi PT dạy từ fromDate đến toDate (tính cả hai đầu). Không có → danh sách rỗng.
     */
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

    /** HÀM PHỤ — findSessions gọi cho mỗi dòng. Một dòng SQL → một thẻ buổi có khách. */
    private PtScheduleItem readSessionRow(ResultSet rs) throws SQLException {
        PtScheduleItem row = new PtScheduleItem();

        // Buổi
        row.setSessionId(rs.getInt("SessionId"));
        row.setSessionStatus(rs.getString("SessionStatus"));

        // Khung giờ
        row.setTimeSlotId(rs.getInt("TimeSlotId"));
        row.setSlotDate(rs.getDate("SlotDate").toLocalDate());
        row.setStartTime(rs.getTime("StartTime").toLocalTime());
        row.setEndTime(rs.getTime("EndTime").toLocalTime());

        // Gói
        row.setPackageName(rs.getString("PackageNameSnapshot"));
        row.setSessionNo(rs.getInt("SessionNo"));
        row.setTotalSessions(rs.getInt("GeneratedSessionCount"));

        // Khách
        row.setCustomerNames(rs.getString("CustomerNames"));
        row.setCustomerPhone(rs.getString("CustomerPhone"));
        return row;
    }


    /**
     * HÀM CHÍNH — Service gọi ở B4 của getWeek.
     * Các khung Free / Held của PT trong khoảng ngày.
     * NOT EXISTS: bỏ khung đã có buổi Scheduled (buổi đó đã được findSessions đọc rồi).
     */
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
     * HÀM PHỤ — findOpenSlots và findSlotsForPublish gọi cho mỗi dòng.
     * Một dòng TimeSlots → một thẻ khung. kind tạm giữ chữ Status trong DB (Free / Held / Withdrawn...);
     * Service sẽ đổi thành free / held / locked.
     */
    private PtScheduleItem readSlot(ResultSet rs) throws SQLException {
        PtScheduleItem row = new PtScheduleItem();
        row.setTimeSlotId(rs.getInt("TimeSlotId"));
        row.setSlotDate(rs.getDate("SlotDate").toLocalDate());
        row.setStartTime(rs.getTime("StartTime").toLocalTime());
        row.setEndTime(rs.getTime("EndTime").toLocalTime());
        row.setKind(rs.getString("Status"));
        return row;
    }


    /** HÀM CHÍNH — Service gọi ở B4 của getWeek. Mở connection riêng rồi dùng bản có connection bên dưới. */
    public List<LeaveInfo> findLeaves(int employeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return findLeaves(connection, employeeId, fromDate, toDate);
        }
    }

    /**
     * HÀM CHÍNH — bản dùng connection có sẵn: Service gọi ở B3 của publishInTransaction (trong transaction).
     * Đơn nghỉ Pending / Approved của PT trong khoảng ngày. Đơn Rejected không lấy.
     * Một đơn nghỉ là KHOẢNG ngày FromDate..ToDate (schema develop 26/10) → tách thành 1 LeaveInfo cho MỖI ngày
     * nằm trong [fromDate, toDate], để isOnLeave vẫn so từng ngày như cũ.
     * HOLDLOCK: giữ dữ liệu đơn nghỉ không đổi tới khi transaction kết thúc.
     */
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
            statement.setDate(2, Date.valueOf(toDate));      // đơn bắt đầu trước khi khoảng xem kết thúc
            statement.setDate(3, Date.valueOf(fromDate));    // và kết thúc sau khi khoảng xem bắt đầu

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

    /** HÀM PHỤ — findLeaves gọi cho MỖI NGÀY của 1 đơn nghỉ → một LeaveInfo. */
    private LeaveInfo readLeave(ResultSet rs, LocalDate day) throws SQLException {
        LeaveInfo row = new LeaveInfo();
        row.setLeaveDate(day);
        row.setShiftType(rs.getString("ShiftType"));      // Morning / Afternoon / FullDay
        row.setStatus(rs.getString("Status"));            // Pending / Approved
        row.setReason(rs.getString("Reason"));
        return row;
    }


    /** HÀM CHÍNH — Service gọi ở B4 của getWeek. Mở connection riêng rồi dùng bản có connection bên dưới. */
    public List<HourException> findHourExceptions(LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return findHourExceptions(connection, fromDate, toDate);
        }
    }

    /**
     * HÀM CHÍNH — bản dùng connection có sẵn: Service gọi ở B3 của publishInTransaction.
     * Các ngày phòng gym đổi giờ hoặc nghỉ cả ngày. Ngày không có dòng nào → Service dùng giờ P-19.
     */
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

    /** HÀM PHỤ — findHourExceptions gọi cho mỗi dòng. Ngày nghỉ cả ngày thì OpenTime/CloseTime là NULL. */
    private HourException readHourException(ResultSet rs) throws SQLException {
        HourException row = new HourException();
        row.setApplyDate(rs.getDate("ApplyDate").toLocalDate());
        row.setClosedAllDay(rs.getBoolean("IsClosedAllDay"));

        Time openTime = rs.getTime("OpenTime");
        Time closeTime = rs.getTime("CloseTime");
        row.setOpenTime(openTime == null ? null : openTime.toLocalTime());
        row.setCloseTime(closeTime == null ? null : closeTime.toLocalTime());
        return row;
    }


    // ================================================================================================================
    // PHẦN 2. CÔNG BỐ — mọi hàm nhận connection của Service, KHÔNG tự commit
    // ================================================================================================================

    /**
     * HÀM CHÍNH — Service gọi ở B1 của publishInTransaction.
     * Khóa dòng hồ sơ PT (UPDLOCK, HOLDLOCK): lần Công bố thứ hai của cùng PT phải chờ lần đầu xong.
     * Trả mã PT; tài khoản không phải PT đang làm việc → null.
     */
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

    /** HÀM CHÍNH — Service gọi ở B1 của publishInTransaction. Giữ P-06 / P-19 không bị sửa giữa chừng. */
    public void lockScheduleConfigs(Connection connection) throws SQLException {
        String sql = """
                SELECT ConfigKey FROM SystemConfigs WITH (HOLDLOCK)
                WHERE ConfigKey IN ('P-06', 'P-19')
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                // Chỉ cần đọc hết các dòng để giữ khóa tới cuối transaction.
            }
        }
    }

    /**
     * HÀM CHÍNH — Service gọi ở B2 của publishInTransaction, mỗi khung cần gỡ một lần.
     * Đổi Free → Withdrawn (DBR-36): giữ dòng để làm lịch sử, không xóa.
     *
     * Chỉ đổi khi ĐỦ 4 điều kiện:
     *   đúng PT  ·  đang Free (không Held/Booked)  ·  chưa tới giờ bắt đầu  ·  không có buổi Scheduled gắn vào.
     *
     * 5 dấu ?: mã khung, mã PT, hôm nay, hôm nay, giờ hiện tại.
     * Trả true nếu đổi được đúng 1 dòng; false = khung không còn gỡ được.
     */
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

    /**
     * HÀM CHÍNH — Service gọi ở B3 của publishInTransaction.
     * Mọi khung (kể cả Withdrawn) trong khoảng ngày, để Service kiểm tra trùng giờ.
     * Khung có buổi Scheduled của PT này luôn được coi là 'Booked', dù Status trong TimeSlots lệch.
     * UPDLOCK, HOLDLOCK: không ai chen thêm khung vào khoảng này tới khi transaction kết thúc.
     */
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

    /**
     * HÀM CHÍNH — Service gọi ở B4 của publishInTransaction, mỗi khung mới một lần.
     *
     *   Cách 1: đã có khung Withdrawn ĐÚNG giờ này → đổi lại thành Free (giữ mã cũ, giữ lịch sử).
     *   Cách 2: chưa có → INSERT dòng mới với Status = 'Free'.
     *
     * Giờ gửi bằng chuỗi + CAST(? AS time) để driver không đổi thành kiểu DATETIME.
     */
    public void insertOrReopenSlot(Connection connection, int ptId, PtSlotDraft draft) throws SQLException {

        // Cách 1: mở lại khung Withdrawn cùng giờ.
        String reopenSql = """
                UPDATE TimeSlots SET Status = 'Free'
                WHERE PtEmployeeId = ? AND SlotDate = ? AND StartTime = CAST(? AS time) AND EndTime = CAST(? AS time)
                  AND Status = 'Withdrawn'
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

        // Cách 2: thêm khung mới (DBR-33: chặn chồng giờ ngay trong SQL — lớp cuối chống 2 khung đè nhau).
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
            // Các dấu ? trong NOT EXISTS (theo đúng thứ tự xuất hiện trong SQL):
            statement.setInt(5, ptId);                                       // t.PtEmployeeId = ?
            statement.setDate(6, Date.valueOf(draft.getSlotDate()));         // t.SlotDate = ?
            statement.setString(7, draft.getEndTime().toString());           // t.StartTime < @end
            statement.setString(8, draft.getStartTime().toString());         // t.EndTime  > @start
            int inserted = statement.executeUpdate();
            if (inserted == 0) {
                // IllegalArgumentException để Service trả đúng câu lỗi cho PT (không phải lỗi kết nối DB).
                throw new IllegalArgumentException("Khung giờ này trùng với khung giờ đã có ("
                        + draft.getStartTime() + "–" + draft.getEndTime() + ").");
            }
        }
    }
}
