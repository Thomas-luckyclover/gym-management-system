package com.gms.job;

import com.gms.service.SessionReminderService;
import java.sql.SQLException;

/**
 * JOB-08 — tự nhắc khách có buổi PT ngày mai nhưng chưa vào được phòng tập.
 *
 * AI GỌI?  BackgroundJobScheduler, lượt hằng ngày 00:05 (CUỐI DAILY_JOBS, sau các job hết hạn gói).
 * LÀM GÌ?  Chỉ gọi SessionReminderService.runJob(). Quy tắc nằm ở Service, SQL nằm ở SessionReminderDAO.
 * KẾT QUẢ HIỆN Ở ĐÂU?  Chuông thông báo trên topbar Gói của tôi / Lịch tập của khách.
 * Thay cho nút "Nhắc khách" bấm tay ở PT Dashboard (đã bỏ).
 */
public class RemindMissingGymAccessJob implements BackgroundJob {

    /** Tên in ra log của scheduler. */
    @Override
    public String getName() {
        return "JOB-08 Remind customers missing gym access for tomorrow's PT sessions";
    }

    /** HÀM CHÍNH — BackgroundJobScheduler gọi mỗi ngày. */
    @Override
    public void run() throws SQLException {
        new SessionReminderService().runJob();
    }
}
