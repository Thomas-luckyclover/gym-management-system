package com.gms.job;

import com.gms.service.SessionReminderService;
import java.sql.SQLException;

/**
 * JOB-08 — tự nhắc khách có buổi PT ngày mai nhưng chưa vào được phòng tập.
 *
 * Thay cho nút "Nhắc khách" bấm tay ở PT Dashboard. Quy tắc nằm trong SessionReminderService.
 * Job chỉ gọi Service (giống mọi job khác), không viết SQL ở đây.
 */
public class RemindMissingGymAccessJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-08 Remind customers missing gym access for tomorrow's PT sessions";
    }

    @Override
    public void run() throws SQLException {
        new SessionReminderService().runJob();
    }
}
