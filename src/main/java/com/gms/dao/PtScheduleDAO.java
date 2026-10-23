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
import java.util.ArrayList;
import java.util.List;

/**
 * DAO màn "Lịch dạy" của PT (/pt/schedule).
 *   findPtEmployeeId : tài khoản đăng nhập → mã nhân viên PT đang làm việc.
 *   findSessions    : các buổi PT dạy trong khoảng ngày, kèm khách và gói.
 *   findOpenSlots    : các khung Free / Held của PT trong khoảng ngày.
 *   findLeaves       : các ngày/ca nghỉ Pending / Approved của PT trong khoảng ngày.
 *   findHourExceptions : giờ mở cửa ngoại lệ / ngày nghỉ cả ngày của phòng gym.
 * Service điều phối transaction công bố; DAO chỉ đọc/ghi, không tính nghiệp vụ.
 */
public class PtScheduleDAO {


    /**
     * Câu SQL lấy buổi PT dạy, giữ mọi trạng thái Scheduled / CheckedIn / Missed / Cancelled.
     *   Bước 1 (CTE): đánh số #1, #2… theo ngày giờ trong từng gói, chưa lọc PT hoặc tuần.
     *                 Buổi chuyển PT vẫn giữ vị trí trong gói, không đếm lại từ 1.
     *   Bước 2:       JOIN tên khách, SĐT người đứng tên và tên người đi kèm gói đôi.
     *                 Sau đó mới lọc PT DẠY buổi (Sessions.PtEmployeeId) và khoảng ngày.
     * 3 dấu ?: ptEmployeeId, fromDate, toDate.
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

    // ================================================================================================================
    // 1. PT ĐĂNG NHẬP — findPtEmployeeId đổi mã tài khoản thành mã PT đang làm việc
    // ================================================================================================================

    /** Tài khoản → EmployeeId của PT đang Working. Không có hồ sơ phù hợp → null. */
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

            // B1: tài khoản lấy từ session, không phải mã PT trên URL
            statement.setInt(1, accountId);

            // B2: có hồ sơ PT đang làm việc → trả mã nhân viên
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("EmployeeId");
                }
            }
        }

        return null;
    }

    // ================================================================================================================
    // 2. BUỔI CÓ KHÁCH — findSessions gọi readSessionRow ngay dưới để tạo DTO buổi
    // ================================================================================================================

    /** Buổi PT dạy trong [fromDate, toDate], tính cả hai ngày biên. Không có buổi → danh sách rỗng. */
    public List<PtScheduleItem> findSessions(int ptEmployeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        List<PtScheduleItem> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(SESSIONS_SQL)) {

            // B1: mã nhân viên PT dạy buổi và khoảng ngày cần xem
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));

            // B2: mỗi dòng → 1 buổi, khách và gói đã JOIN sẵn
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readSessionRow(rs));
                }
            }
        }
        return rows;
    }

    /** Đọc 1 dòng của SESSIONS_SQL thành 1 buổi. Nhãn, màu và quyền thao tác do Service tính. */
    private PtScheduleItem readSessionRow(ResultSet rs) throws SQLException {
        PtScheduleItem row = new PtScheduleItem();

        // Buổi
        row.setSessionId(rs.getInt("SessionId"));
        row.setSessionStatus(rs.getString("SessionStatus"));

        // Khung và giờ
        row.setTimeSlotId(rs.getInt("TimeSlotId"));
        row.setSlotDate(rs.getDate("SlotDate").toLocalDate());
        row.setStartTime(rs.getTime("StartTime").toLocalTime());
        row.setEndTime(rs.getTime("EndTime").toLocalTime());

        // Gói (số buổi đã tính trên cả gói trước khi lọc)
        row.setPackageName(rs.getString("PackageNameSnapshot"));
        row.setSessionNo(rs.getInt("SessionNo"));
        row.setTotalSessions(rs.getInt("GeneratedSessionCount"));

        // Khách (gói đôi ghép tên A & B; SĐT luôn của người đứng tên)
        row.setCustomerNames(rs.getString("CustomerNames"));
        row.setCustomerPhone(rs.getString("CustomerPhone"));
        return row;
    }

    // ================================================================================================================
    // 3. KHUNG FREE / HELD — findOpenSlots gọi readSlot ngay dưới để tạo DTO khung
    // ================================================================================================================

    /**
     * Khung Free / Held của PT trong [fromDate, toDate], tính cả hai ngày biên.
     * NOT EXISTS: bỏ khung đã có buổi Scheduled; buổi có khách được đọc riêng.
     * 3 dấu ?: ptEmployeeId, fromDate, toDate. Không có khung → danh sách rỗng.
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

            // B1: mã nhân viên PT và khoảng ngày cần xem
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));

            // B2: mỗi dòng → 1 khung giờ
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readSlot(rs));
                }
            }
        }
        return rows;
    }

    /** Đọc 1 dòng TimeSlots thành 1 khung. Chưa có dữ liệu buổi, khách hoặc gói. */
    private PtScheduleItem readSlot(ResultSet rs) throws SQLException {
        PtScheduleItem row = new PtScheduleItem();

        // Khung
        row.setTimeSlotId(rs.getInt("TimeSlotId"));

        // Giờ
        row.setSlotDate(rs.getDate("SlotDate").toLocalDate());
        row.setStartTime(rs.getTime("StartTime").toLocalTime());
        row.setEndTime(rs.getTime("EndTime").toLocalTime());

        // Trạng thái gốc Free / Held; Service sẽ đổi thành free / held / locked
        row.setKind(rs.getString("Status"));
        return row;
    }

    // ================================================================================================================
    // 4. ĐƠN NGHỈ — findLeaves gọi readLeave ngay dưới để tạo DTO đơn nghỉ
    // ================================================================================================================

    /**
     * Đơn nghỉ Pending / Approved của PT trong [fromDate, toDate], tính cả hai ngày biên.
     * Không lấy Rejected: đơn bị từ chối không còn dùng để tạm khóa khung.
     * 3 dấu ?: employeeId, fromDate, toDate. Không có đơn → danh sách rỗng.
     */
    public List<LeaveInfo> findLeaves(int employeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return findLeaves(connection, employeeId, fromDate, toDate);
        }
    }

    /** Đọc đơn nghỉ trong cùng transaction công bố; giữ dữ liệu ổn định tới commit/rollback. */
    public List<LeaveInfo> findLeaves(Connection connection, int employeeId, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT lr.LeaveDate, lr.ShiftType, lr.Status
                FROM LeaveRequests lr WITH (HOLDLOCK)
                WHERE lr.EmployeeId = ?
                  AND lr.LeaveDate BETWEEN ? AND ?
                  AND lr.Status IN ('Pending', 'Approved')
                ORDER BY lr.LeaveDate, lr.ShiftType
                """;

        List<LeaveInfo> rows = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            // B1: mã nhân viên PT và khoảng ngày đang xem
            statement.setInt(1, employeeId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));

            // B2: mỗi dòng → 1 ngày/ca nghỉ; không sửa đơn hoặc trạng thái khung
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readLeave(rs));
                }
            }
        }
        return rows;
    }

    /** Đọc 1 dòng LeaveRequests thành ngày/ca nghỉ. Nhãn nghỉ và khung bị khóa do Service tính. */
    private LeaveInfo readLeave(ResultSet rs) throws SQLException {
        LeaveInfo row = new LeaveInfo();

        // Ngày và ca nghỉ (Morning / Afternoon / FullDay)
        row.setLeaveDate(rs.getDate("LeaveDate").toLocalDate());
        row.setShiftType(rs.getString("ShiftType"));

        // Trạng thái đơn (Pending / Approved)
        row.setStatus(rs.getString("Status"));
        return row;
    }

    // ================================================================================================================
    // 5. GIỜ NGOẠI LỆ — findHourExceptions gọi readHourException ngay dưới
    // ================================================================================================================

    /**
     * Các ngày có giờ ngoại lệ hoặc nghỉ cả ngày trong [fromDate, toDate], tính cả hai ngày biên.
     * Không có bản ghi cho một ngày → Service dùng giờ mặc định P-19 cho ngày đó.
     * 2 dấu ?: fromDate, toDate. Không có ngoại lệ → danh sách rỗng.
     */
    public List<HourException> findHourExceptions(LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return findHourExceptions(connection, fromDate, toDate);
        }
    }

    /** Đọc giờ ngoại lệ bằng connection của transaction; không mở connection khác. */
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

            // B1: khoảng ngày đang xem; giờ mở cửa là của phòng gym, không phải riêng từng PT
            statement.setDate(1, Date.valueOf(fromDate));
            statement.setDate(2, Date.valueOf(toDate));

            // B2: mỗi dòng → 1 ngày ngoại lệ, gồm cả ngày đóng cửa và ngày đổi giờ
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readHourException(rs));
                }
            }
        }
        return rows;
    }

    /** Đọc 1 ngày ngoại lệ. Nghỉ cả ngày thì OpenTime / CloseTime trong DB là NULL. */
    private HourException readHourException(ResultSet rs) throws SQLException {
        HourException row = new HourException();

        // Ngày áp dụng và cờ nghỉ cả ngày
        row.setApplyDate(rs.getDate("ApplyDate").toLocalDate());
        row.setClosedAllDay(rs.getBoolean("IsClosedAllDay"));

        // Giờ (kiểm tra NULL trước khi đổi kiểu, giống cách đọc CancelledAt của MyPtSessionDAO)
        Time openTime = rs.getTime("OpenTime");
        Time closeTime = rs.getTime("CloseTime");
        row.setOpenTime(openTime == null ? null : openTime.toLocalTime());
        row.setCloseTime(closeTime == null ? null : closeTime.toLocalTime());
        return row;
    }

    // ================================================================================================================
    // 6. CÔNG BỐ — Service giữ transaction; khóa và kiểm tra xong cả danh sách mới ghi
    // ================================================================================================================

    /** Khóa hồ sơ PT để hai lần công bố của cùng PT không chạy đồng thời. */
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

    /** Giữ P-06/P-19 không đổi trong lúc Service đọc cấu hình và ghi các khung. */
    public void lockScheduleConfigs(Connection connection) throws SQLException {
        String sql = """
                SELECT ConfigKey FROM SystemConfigs WITH (HOLDLOCK)
                WHERE ConfigKey IN ('P-06', 'P-19')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) { /* Đọc hết để giữ khóa cả hai tham số tới cuối transaction. */ }
        }
    }

    /** Khóa khung trong khoảng ngày; buổi Scheduled của PT cũng chặn dù trạng thái slot bị lệch. */
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
                while (rs.next()) rows.add(readSlot(rs));
            }
        }
        return rows;
    }

    /** Mở lại đúng khung Withdrawn hoặc thêm Free mới; giữ giờ cũ để không đổi lịch sử buổi. */
    public void insertOrReopenSlot(Connection connection, int ptId, PtSlotDraft draft) throws SQLException {
        String reopenSql = """
                UPDATE TimeSlots SET Status = 'Free'
                WHERE PtEmployeeId = ? AND SlotDate = ? AND StartTime = CAST(? AS time) AND EndTime = CAST(? AS time)
                  AND Status = 'Withdrawn'
                """;
        try (PreparedStatement statement = connection.prepareStatement(reopenSql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(draft.getSlotDate()));
            // SQL khai báo TIME rõ ràng, tránh driver gửi setTime thành DATETIME.
            statement.setString(3, draft.getStartTime().toString());
            statement.setString(4, draft.getEndTime().toString());
            if (statement.executeUpdate() == 1) return;
        }
        String insertSql = """
                INSERT INTO TimeSlots (PtEmployeeId, SlotDate, StartTime, EndTime, Status)
                VALUES (?, ?, CAST(? AS time), CAST(? AS time), 'Free')
                """;
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(draft.getSlotDate()));
            statement.setString(3, draft.getStartTime().toString());
            statement.setString(4, draft.getEndTime().toString());
            statement.executeUpdate();
        }
    }
}
