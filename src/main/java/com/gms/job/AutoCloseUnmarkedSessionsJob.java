package com.gms.job;

import java.sql.SQLException;

/**
 * JOB-04 — Auto-close unmarked PT sessions (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: hằng ngày, ngay sau JOB-03
 * Luật: buổi còn Scheduled quá P-07 (24h) sau giờ kết thúc → xét trước: buổi thuộc gói PT mà khách không còn gói tập hợp lệ → Missed; khách có check-in trong ngày buổi tập → Completed; không check-in → Missed.
 *
 * Người phụ trách gợi ý: TV5 (xem docs/team-plan.md).
 * TODO: gọi Service tương ứng trong run(); job KHÔNG tự viết SQL.
 */
public class AutoCloseUnmarkedSessionsJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-04 Auto-close unmarked PT sessions";
    }

    @Override
    public void run() throws SQLException {
        // TODO(JOB-04): gọi Service xử lý, VD: new XxxService().runJob();
    }
}
