package com.gms.service;

import com.gms.dao.SessionReminderDAO;
import com.gms.dao.SessionReminderDAO.Reminder;
import com.gms.util.DateTimeUtil;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * SERVICE CỦA JOB-08 — TỰ NHẮC KHÁCH TRƯỚC BUỔI PT 1 NGÀY.
 *
 * QUY TẮC (thay cho nút "Nhắc khách" bấm tay của PT):
 *   Mỗi ngày lúc 00:05, hệ thống xét các buổi PT NGÀY MAI.
 *   Khách nào ngày mai chưa vào được phòng tập (thiếu membership / vé ngày, hoặc đang bảo lưu)
 *   → nhận 1 thông báo ở chuông, nói rõ giờ tập, PT và việc cần làm.
 *   Ngày phòng nghỉ cả ngày thì KHÔNG nhắc (CBR-56: PT phải dời buổi; còn sót thì JOB-04 hủy GymClosed).
 *   Mỗi khách chỉ nhận 1 lời nhắc cho 1 ngày tập (job chạy lại cũng không gửi trùng).
 *   Có 2 buổi cùng ngày thì lời nhắc nêu buổi sớm nhất — thiếu quyền vào gym là chuyện của cả ngày.
 *
 * Vì sao nhắc? Không vào được phòng tập thì JOB-04 sẽ chốt buổi là Vắng → khách mất buổi.
 * Nhắc trước 1 ngày để khách kịp mua gói / vé ngày.
 */
public class SessionReminderService {

    private final SessionReminderDAO reminderDAO = new SessionReminderDAO();


    /**
     * HÀM CHÍNH — RemindMissingGymAccessJob.run() gọi mỗi ngày (BackgroundJobScheduler, lượt 00:05).
     * Trả số thông báo đã gửi trong lượt này (để in log).
     */
    public int runJob() throws SQLException {
        // B1: ngày cần xét = ngày mai theo giờ Việt Nam.
        LocalDate tomorrow = DateTimeUtil.today().plusDays(1);

        // B2: DAO tìm những người ngày mai chưa vào được phòng tập.
        List<Reminder> reminders = reminderDAO.findCustomersToRemind(tomorrow);

        // B3: gửi từng người. Mỗi người một transaction riêng: một người lỗi không chặn người khác.
        int sent = 0;
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

        System.out.println("[JOB-08] Đã nhắc " + sent + " khách cho các buổi PT ngày " + DateTimeUtil.formatDate(tomorrow) + ".");
        return sent;
    }

    /**
     * HÀM PHỤ — gọi ở B3 của runJob.
     * Tiêu đề có ngày tập để chống gửi trùng (SessionReminderDAO.sendOnce so tiêu đề).
     * Ví dụ: "Nhắc buổi PT ngày 27/10/2026".
     */
    static String buildTitle(LocalDate sessionDate) {
        return "Nhắc buổi PT ngày " + DateTimeUtil.formatDate(sessionDate);
    }

    /**
     * HÀM PHỤ — gọi ở B3 của runJob. Câu nhắc tùy lý do (bảo lưu hay chưa có gói).
     * Ví dụ: "Ngày mai 27/10/2026 lúc 18:00 bạn có buổi PT với Đinh Công Sơn nhưng chưa có membership..."
     */
    static String buildContent(Reminder reminder, LocalDate sessionDate) {
        String when = "Ngày mai " + DateTimeUtil.formatDate(sessionDate)
                + " lúc " + DateTimeUtil.formatTime(reminder.startTime())
                + " bạn có buổi PT với " + reminder.ptName();

        // Đang bảo lưu thì mua vé ngày cũng KHÔNG vào được (luật JOB-04: có gói VÀ không bảo lưu)
        // → không khuyên mua vé ngày, chỉ bảo liên hệ lễ tân.
        if (reminder.frozen()) {
            return when + " nhưng membership của bạn đang bảo lưu vào ngày này. "
                    + "Hãy liên hệ lễ tân kiểm tra thời gian bảo lưu trước giờ tập để buổi không bị tính vắng.";
        }
        return when + " nhưng chưa có membership hoặc vé ngày hợp lệ. "
                + "Hãy mua gói thành viên hoặc vé ngày trước giờ tập để buổi không bị tính vắng.";
    }
}
