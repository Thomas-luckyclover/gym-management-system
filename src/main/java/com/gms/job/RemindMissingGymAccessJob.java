package com.gms.job;

import com.gms.service.SessionReminderService;
import java.sql.SQLException;

/**
 * JOB-08 — nhắc khách có buổi PT NGÀY MAI nhưng chưa có quyền vào gym (không có gói / vé ngày, hoặc đang bảo lưu).
 * Ai chạy: BackgroundJobScheduler, mỗi ngày 00:05, chạy SAU các job hết hạn gói để xét quyền vào gym mới nhất.
 */
public class RemindMissingGymAccessJob implements BackgroundJob {
    // Tên job in ra log.
    @Override
    public String getName() {
        return "JOB-08 Remind customers missing gym access for tomorrow's PT sessions";
    }

    // Gọi SessionReminderService.runJob().
    @Override
    public void run() throws SQLException {
        new SessionReminderService().runJob();
    }
}
