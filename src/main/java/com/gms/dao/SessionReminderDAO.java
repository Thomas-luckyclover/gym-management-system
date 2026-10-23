package com.gms.dao;

import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO CỦA JOB-08 — tự nhắc khách có buổi PT ngày mai nhưng chưa vào được phòng tập.
 *
 *   findCustomersToRemind   tìm những người cần nhắc (đọc)
 *   sendOnce                gửi 1 thông báo hệ thống cho 1 tài khoản, nếu chưa gửi (ghi)
 *
 * DAO chỉ đọc/ghi. Quyết định câu chữ lời nhắc nằm ở SessionReminderService.
 */
public class SessionReminderDAO {

    /**
     * Một người cần nhắc. record = lớp chỉ chứa dữ liệu, Java tự tạo getter: row.accountId(), row.startTime()...
     *
     * @param accountId  tài khoản của khách (nhận thông báo ở chuông)
     * @param startTime  giờ bắt đầu buổi PT ngày mai
     * @param ptName     tên PT dạy buổi đó
     * @param frozen     true = membership đang bảo lưu đúng ngày tập; false = chưa có membership / vé ngày
     */
    public record Reminder(int accountId, LocalTime startTime, String ptName, boolean frozen) {
    }


    /**
     * HÀM CHÍNH — SessionReminderService gọi ở B2 của runJob.
     * Những khách có buổi PT Scheduled vào ngày sessionDate mà KHÔNG dùng được gym ngày đó.
     *
     * Quy tắc "dùng được gym" giống hệt JOB-04 (AutoCloseSessionService.canUseGym):
     *   có Membership HOẶC vé ngày (DayPass) phủ ngày tập   VÀ   không đang bảo lưu ngày đó.
     *
     * Gói PT đôi: cả người đứng tên lẫn người tập cùng đều phải dùng được gym, nên xét cả hai.
     * Chỉ lấy khách có tài khoản Active (người không có tài khoản thì không có chuông để nhận).
     * 3 dấu ?: ngày tập (dùng 3 lần).
     */
    public List<Reminder> findCustomersToRemind(LocalDate sessionDate) throws SQLException {
        String sql = """
                WITH Attendees AS (
                    -- Người đứng tên gói PT
                    SELECT t.StartTime, s.PtEmployeeId, pt.CustomerId
                    FROM Sessions s
                    JOIN TimeSlots t      ON t.TimeSlotId = s.TimeSlotId
                    JOIN Subscriptions pt ON pt.SubscriptionId = s.SubscriptionId
                    WHERE s.Status = 'Scheduled' AND t.SlotDate = ?

                    UNION ALL

                    -- Người tập cùng (chỉ gói PT đôi)
                    SELECT t.StartTime, s.PtEmployeeId, o.PartnerCustomerId
                    FROM Sessions s
                    JOIN TimeSlots t      ON t.TimeSlotId = s.TimeSlotId
                    JOIN Subscriptions pt ON pt.SubscriptionId = s.SubscriptionId
                    JOIN Orders o         ON o.OrderId = pt.OrderId
                    WHERE s.Status = 'Scheduled' AND t.SlotDate = ?
                      AND pt.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId IS NOT NULL
                )
                SELECT acc.AccountId, a.StartTime, ptPerson.FullName AS PtName,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           JOIN FreezeRecords freeze ON freeze.SubscriptionId = gym.SubscriptionId
                           WHERE gym.CustomerId = a.CustomerId
                             AND gym.SubscriptionType = 'Membership'
                             AND ? BETWEEN freeze.StartDate AND COALESCE(freeze.ActualEndDate, freeze.PlannedEndDate)
                       ) THEN 1 ELSE 0 END AS IsFrozen,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM Subscriptions gym
                           WHERE gym.CustomerId = a.CustomerId
                             AND gym.SubscriptionType IN ('Membership', 'DayPass')
                             AND gym.Status IN ('Active', 'Frozen')
                             AND ? BETWEEN gym.StartDate AND gym.EndDate
                       ) THEN 1 ELSE 0 END AS HasAccess
                FROM Attendees a
                JOIN Customers c        ON c.CustomerId = a.CustomerId
                JOIN Accounts acc       ON acc.PersonId = c.PersonId AND acc.Status = 'Active'
                JOIN Employees e        ON e.EmployeeId = a.PtEmployeeId
                JOIN Persons ptPerson   ON ptPerson.PersonId = e.PersonId
                ORDER BY a.StartTime
                """;

        List<Reminder> reminders = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            Date date = Date.valueOf(sessionDate);
            statement.setDate(1, date);   // người đứng tên
            statement.setDate(2, date);   // người tập cùng
            statement.setDate(3, date);   // có đang bảo lưu ngày đó?
            statement.setDate(4, date);   // có membership / vé ngày phủ ngày đó?

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    boolean frozen = rs.getBoolean("IsFrozen");
                    boolean hasAccess = rs.getBoolean("HasAccess");

                    // Dùng được gym (có quyền và không bảo lưu) → không cần nhắc.
                    if (hasAccess && !frozen) {
                        continue;
                    }
                    reminders.add(new Reminder(
                            rs.getInt("AccountId"),
                            rs.getTime("StartTime").toLocalTime(),
                            rs.getString("PtName"),
                            frozen));
                }
            }
        }
        return reminders;
    }


    /**
     * HÀM CHÍNH — SessionReminderService gọi ở B3 của runJob, mỗi người một lần.
     * Gửi 1 thông báo HỆ THỐNG (SentByEmployeeId = NULL) cho 1 tài khoản, NẾU tài khoản đó chưa có
     * thông báo cùng tiêu đề. Tiêu đề chứa ngày tập, nên mỗi khách được nhắc tối đa 1 lần cho 1 ngày tập,
     * kể cả khi job bị chạy lại.
     *
     * Trả true = đã gửi; false = đã gửi trước đó rồi, bỏ qua.
     * 2 câu INSERT nằm trong 1 transaction: lỗi giữa chừng thì không để lại thông báo không người nhận.
     */
    public boolean sendOnce(int accountId, String title, String content) throws SQLException {
        String checkSql = """
                SELECT 1
                FROM Notifications n
                JOIN NotificationRecipients r ON r.NotificationId = n.NotificationId
                WHERE r.AccountId = ? AND n.Title = ? AND n.SentByEmployeeId IS NULL
                """;
        String notificationSql = """
                INSERT INTO Notifications (SentByEmployeeId, Title, Content, Audience, ScheduledAt, SentAt)
                VALUES (NULL, ?, ?, 'Individual', NULL, SYSDATETIME())
                """;
        String recipientSql = """
                INSERT INTO NotificationRecipients (NotificationId, AccountId, ReadAt)
                VALUES (?, ?, NULL)
                """;

        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // B1: đã nhắc rồi thì thôi.
                try (PreparedStatement check = connection.prepareStatement(checkSql)) {
                    check.setInt(1, accountId);
                    check.setString(2, title);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }

                // B2: tạo thông báo, lấy mã vừa sinh.
                int notificationId;
                try (PreparedStatement insert = connection.prepareStatement(notificationSql, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, title);
                    insert.setString(2, content);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        notificationId = keys.getInt(1);
                    }
                }

                // B3: gắn người nhận, rồi chốt cả hai câu.
                try (PreparedStatement insert = connection.prepareStatement(recipientSql)) {
                    insert.setInt(1, notificationId);
                    insert.setInt(2, accountId);
                    insert.executeUpdate();
                }
                connection.commit();
                return true;

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
