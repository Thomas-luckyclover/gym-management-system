package com.gms.job;

import com.gms.service.AutoCloseSessionService;
import java.sql.SQLException;

/**
 * JOB-04 - chốt các buổi PT còn Scheduled sau khi ngày tập đã kết thúc.
 *
 * <p>Job chỉ gọi Service; toàn bộ SQL và quy tắc Frozen/Membership/CheckIn nằm trong Service.</p>
 */
public class AutoCloseUnmarkedSessionsJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-04 Auto close unmarked PT sessions";
    }

    @Override
    public void run() throws SQLException {
        // Scheduler gọi Job; Service xử lý từng buổi trong transaction riêng.
        new AutoCloseSessionService().runJob();
    }
}
