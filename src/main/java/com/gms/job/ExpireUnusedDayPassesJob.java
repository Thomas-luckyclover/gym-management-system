package com.gms.job;

import com.gms.service.ExpireDayPassService;
import java.sql.SQLException;

/**
 * JOB-03 — Expire unused day passes (SRS 13.3 & 16.3).
 *
 * Lịch chạy: hằng ngày lúc 00:05, ngay sau JOB-02.
 * Luật: vé ngày đã qua ngày hiệu lực mà chưa check-in -> Expired.
 */
public class ExpireUnusedDayPassesJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-03 Expire unused day passes";
    }

    @Override
    public void run() throws SQLException {
        int expiredCount = new ExpireDayPassService().runJob();
        System.out.println("[JOB-03] Đã chuyển Expired " + expiredCount + " vé ngày quá hạn chưa sử dụng.");
    }
}
