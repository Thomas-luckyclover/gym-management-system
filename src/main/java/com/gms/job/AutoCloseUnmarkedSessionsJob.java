package com.gms.job;

import java.sql.SQLException;
import com.gms.service.AutoCloseSessionService;

/** Chạy JOB-04 lúc 00:05 giờ VN qua Service. */
public class AutoCloseUnmarkedSessionsJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-04 Auto-close unmarked PT sessions";
    }

    @Override
    public void run() throws SQLException {
        new AutoCloseSessionService().runJob();
    }
}
