package com.gms.service;

import com.gms.dao.SessionReminderDAO;
import com.gms.dao.SessionReminderDAO.Reminder;
import com.gms.util.DateTimeUtil;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * JOB-08 — gửi thông báo cho khách có buổi PT NGÀY MAI nhưng chưa vào gym được (không gói / bảo lưu).
 * Mỗi khách chỉ nhận 1 lần cho 1 ngày (SessionReminderDAO.sendOnce kiểm trùng tiêu đề).
 JOB-08 là bước phòng ngừa: lúc 00:05 mỗi ngày, hệ thống tìm khách có buổi PT ngày mai nhưng chưa có quyền vào gym, rồi gửi thông báo nhắc mua gói hoặc vé ngày, hoặc liên hệ lễ tân nếu đang bảo lưu. Khách sửa kịp thì buổi không bị tính vắng.
 */
public class SessionReminderService {
    private final SessionReminderDAO reminderDAO = new SessionReminderDAO();

    // Chạy job (BackgroundJobScheduler gọi lúc 00:05). Trả về: số thông báo mới đã gửi.
    public int runJob() throws SQLException {
        // B1: ngày cần nhắc = ngày mai
        LocalDate tomorrow = DateTimeUtil.today().plusDays(1);

        // B2: SessionReminderDAO.findCustomersToRemind → các khách (cả người tập cùng) có buổi ngày mai
        //     mà CHƯA có quyền vào gym; đã bỏ ngày phòng nghỉ và buổi ngoài giờ mở cửa
        List<Reminder> reminders = reminderDAO.findCustomersToRemind(tomorrow);

        int sent = 0;

        // B3: mỗi khách → sendOnce: Notifications + NotificationRecipients thêm 1 dòng (đã gửi rồi thì bỏ qua)
        //     1 khách lỗi thì in lỗi, chạy tiếp khách sau
        for (Reminder reminder : reminders) {
            try {
                boolean isNew = reminderDAO.sendOnce(reminder.accountId(), buildTitle(tomorrow), buildContent(reminder, tomorrow));
                if (isNew) {
                    sent++;
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }

        // B4: in log số khách đã nhắc
        System.out.println("[JOB-08] Đã nhắc " + sent + " khách cho các buổi PT ngày " + DateTimeUtil.formatDate(tomorrow) + ".");
        return sent;
    }

    /**
     * HÀM PHỤ (runJob B3) — tiêu đề "Nhắc buổi PT ngày dd/MM/yyyy" (cũng là khóa chống gửi trùng).
     */
    static String buildTitle(LocalDate sessionDate) {
        return "Nhắc buổi PT ngày " + DateTimeUtil.formatDate(sessionDate);
    }

    /**
     * HÀM PHỤ (runJob B3) — nội dung: "Ngày mai … lúc … bạn có buổi PT với …" + lý do:
     *   đang bảo lưu → nhắc liên hệ lễ tân · không có gói / vé ngày → nhắc mua trước giờ tập.
     */
    static String buildContent(Reminder reminder, LocalDate sessionDate) {
        String when = "Ngày mai " + DateTimeUtil.formatDate(sessionDate)
                + " lúc " + DateTimeUtil.formatTime(reminder.startTime())
                + " bạn có buổi PT với " + reminder.ptName();

        if (reminder.frozen()) {
            return when + " nhưng membership của bạn đang bảo lưu vào ngày này. "
                    + "Hãy liên hệ lễ tân kiểm tra thời gian bảo lưu trước giờ tập để buổi không bị tính vắng.";
        }
        return when + " nhưng chưa có membership hoặc vé ngày hợp lệ. "
                + "Hãy mua gói thành viên hoặc vé ngày trước giờ tập để buổi không bị tính vắng.";
    }
}
