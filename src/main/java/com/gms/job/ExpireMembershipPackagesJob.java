package com.gms.job;

import java.sql.SQLException;

/**
 * JOB-02 — Expire membership packages (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: hằng ngày, ngay sau JOB-01
 * Luật: gói có End Date < hôm nay → Expired; hội viên không còn gói hợp lệ nào → trạng thái hội viên Expired.
 *
 * Người phụ trách gợi ý: TV4 (xem docs/team-plan.md).
 * TODO: gọi Service tương ứng trong run(); job KHÔNG tự viết SQL.
 */
public class ExpireMembershipPackagesJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-02 Expire membership packages";
    }

    @Override
    public void run() throws SQLException {
        // TODO(JOB-02): gọi Service xử lý, VD: new XxxService().runJob();
    }
}
