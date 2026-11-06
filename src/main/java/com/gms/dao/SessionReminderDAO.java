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
 * DAO của JOB-08 — tìm khách cần nhắc + ghi thông báo.
 */
public class SessionReminderDAO {
    // 1 khách cần nhắc: tài khoản nhận · giờ bắt đầu buổi · tên PT · đang bảo lưu? (để chọn câu nhắc)
    public record Reminder(
            int accountId,
            LocalTime startTime,
            String ptName,
            boolean frozen) {
    }

    // Đọc khách có buổi Scheduled vào sessionDate (người đứng tên + người tập cùng của gói đôi).
    //   Bỏ: ngày phòng nghỉ cả ngày, buổi ngoài giờ mở cửa (OperatingHourExceptions / P-19) — JOB-04 sẽ tự hủy GymClosed.
    //   Mỗi người: IsFrozen (FreezeRecords phủ ngày) + HasAccess (Membership / DayPass Active|Frozen phủ ngày).
    // Trả về: List<Reminder> — CHỈ người không vào gym được (HasAccess = 0 hoặc đang bảo lưu).
    public List<Reminder> findCustomersToRemind(LocalDate sessionDate) throws SQLException {
        String sql = """
                WITH Attendees AS (
                    -- Người đứng tên gói PT
                    SELECT t.StartTime, s.PtEmployeeId, pt.CustomerId
                    FROM Sessions s
                    JOIN TimeSlots t      ON t.TimeSlotId = s.TimeSlotId
                    JOIN Subscriptions pt ON pt.SubscriptionId = s.SubscriptionId
                    LEFT JOIN OperatingHourExceptions ex ON ex.ApplyDate = t.SlotDate
                    LEFT JOIN SystemConfigs cfg ON cfg.ConfigKey = 'P-19'
                    WHERE s.Status = 'Scheduled' AND t.SlotDate = ?
                      -- CBR-56: bỏ ngày nghỉ cả ngày VÀ buổi nằm ngoài giờ mở cửa ngày đó (JOB-04 sẽ hủy GymClosed)
                      AND ISNULL(ex.IsClosedAllDay, 0) = 0
                      AND t.StartTime >= COALESCE(ex.OpenTime, TRY_CONVERT(time(0), LEFT(cfg.ConfigValue, 5)))
                      AND t.EndTime <= COALESCE(ex.CloseTime, TRY_CONVERT(time(0), RIGHT(cfg.ConfigValue, 5)))

                    UNION ALL

                    -- Người tập cùng (chỉ gói PT đôi)
                    SELECT t.StartTime, s.PtEmployeeId, o.PartnerCustomerId
                    FROM Sessions s
                    JOIN TimeSlots t      ON t.TimeSlotId = s.TimeSlotId
                    JOIN Subscriptions pt ON pt.SubscriptionId = s.SubscriptionId
                    JOIN Orders o         ON o.OrderId = pt.OrderId
                    LEFT JOIN OperatingHourExceptions ex ON ex.ApplyDate = t.SlotDate
                    LEFT JOIN SystemConfigs cfg ON cfg.ConfigKey = 'P-19'
                    WHERE s.Status = 'Scheduled' AND t.SlotDate = ?
                      AND pt.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId IS NOT NULL
                      -- CBR-56: bỏ ngày nghỉ cả ngày VÀ buổi nằm ngoài giờ mở cửa ngày đó (JOB-04 sẽ hủy GymClosed)
                      AND ISNULL(ex.IsClosedAllDay, 0) = 0
                      AND t.StartTime >= COALESCE(ex.OpenTime, TRY_CONVERT(time(0), LEFT(cfg.ConfigValue, 5)))
                      AND t.EndTime <= COALESCE(ex.CloseTime, TRY_CONVERT(time(0), RIGHT(cfg.ConfigValue, 5)))
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
            // ? 1, ? 2 = ngày tập (2 nhánh UNION) · ? 3, ? 4 = ngày tập (kiểm bảo lưu / quyền vào gym)
            statement.setDate(1, date);
            statement.setDate(2, date);
            statement.setDate(3, date);
            statement.setDate(4, date);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    boolean frozen = rs.getBoolean("IsFrozen");
                    boolean hasAccess = rs.getBoolean("HasAccess");

                    // Vào gym được bình thường → không cần nhắc
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

    // Gửi 1 thông báo cho 1 tài khoản, KHÔNG gửi trùng (cùng tiêu đề, do hệ thống gửi). Trả về: true = vừa gửi mới.
    // Notifications (1 dòng = 1 thông báo)
    // NotificationId, SentByEmployeeId (NULL = System Send), Title, Content, Audience (Individual = gửi riêng 1 ngày), SendAt (Thời điểm gửi)
    // NotificationRecipents (Ai nhận, 1 dòng = 1 người), NotificationId trỏ về thông báo, AccountId (Tài khoản nhận), ReadAt (NULL = chưa đọc)
    // Tách 2 bảng vì một thông báo có thể gửi cho nhiều người. Ví dụ quản lý gửi “Tết nghỉ 3 ngày” cho mọi khách: chỉ 1 dòng nội dung, nhưng nhiều dòng người nhận, mỗi người có ReadAt riêng.
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
            // Mở transaction
            connection.setAutoCommit(false);
            try {
                // B1: đã có thông báo cùng tiêu đề cho tài khoản này → rollback, trả false
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

                // B2: Notifications: thêm 1 dòng (Audience = Individual, người gửi = hệ thống) → lấy NotificationId
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

                // B3: NotificationRecipients: thêm 1 dòng (người nhận, chưa đọc)
                try (PreparedStatement insert = connection.prepareStatement(recipientSql)) {
                    insert.setInt(1, notificationId);
                    insert.setInt(2, accountId);
                    insert.executeUpdate();
                }
                // B4: commit
                connection.commit();
                return true;

            } catch (SQLException exception) {
                // Lỗi → rollback cả 2 lệnh INSERT
                connection.rollback();
                throw exception;
            }
        }
    }
}
