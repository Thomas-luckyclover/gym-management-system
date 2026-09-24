package com.gms.job;

import java.sql.SQLException;

/**
 * JOB-03 — Expire unused day passes (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: hằng ngày, ngay sau JOB-02
 * Luật: vé ngày đã qua ngày hiệu lực mà chưa check-in → Expired.
 *
 * Người phụ trách gợi ý: TV4 (xem docs/team-plan.md).
 * TODO: gọi Service tương ứng trong run(); job KHÔNG tự viết SQL.
 */
public class ExpireUnusedDayPassesJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-03 Expire unused day passes";
    }

    @Override
    public void run() throws SQLException {
        // TODO(JOB-03): gọi Service xử lý, VD: new XxxService().runJob();
    }
}
