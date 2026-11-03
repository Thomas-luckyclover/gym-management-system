package com.gms.job;

import com.gms.service.AutoCloseSessionService;
import java.sql.SQLException;

/**
 * JOB-04 — tự chốt buổi PT đã qua ngày mà PT chưa xử lý (vẫn Scheduled).
 * Ai chạy: BackgroundJobScheduler, mỗi ngày 00:05 (sau JOB-01/02/03, trước JOB-08).
 */
public class AutoCloseUnmarkedSessionsJob implements BackgroundJob {
    // Tên job in ra log.
    @Override
    public String getName() {
        return "JOB-04 Auto close unmarked PT sessions";
    }

    // Gọi AutoCloseSessionService.runJob() (ngày = hôm nay).
    @Override
    public void run() throws SQLException {
        new AutoCloseSessionService().runJob();
    }
}
