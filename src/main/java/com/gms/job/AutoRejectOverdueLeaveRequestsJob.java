package com.gms.job;

import java.sql.SQLException;

/**
 * JOB-07 — Remind and auto-reject overdue leave requests (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: mỗi phút
 * Luật: trước mốc P-20 (20:00 ngày trước ngày nghỉ) nhắc Manager; quá mốc mà đơn còn Pending → Rejected và báo cho nhân viên.
 *
 * Người phụ trách gợi ý: TV3 (xem docs/team-plan.md).
 * TODO: gọi Service tương ứng trong run(); job KHÔNG tự viết SQL.
 */
public class AutoRejectOverdueLeaveRequestsJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-07 Remind and auto-reject overdue leave requests";
    }

    @Override
    public void run() throws SQLException {
        // TODO(JOB-07): gọi Service xử lý, VD: new XxxService().runJob();
    }
}
