package com.gms.job;

import com.gms.service.LeaveRequestService;
import java.sql.SQLException;

/**
 * JOB-07 — Remind and auto-reject overdue leave requests (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: mỗi phút
 * Luật: trước mốc P-20 (20:00 ngày trước ngày nghỉ) nhắc Manager; quá mốc mà đơn còn Pending → Rejected và báo cho nhân viên.
 *
 * Người phụ trách gợi ý: TV3 (xem docs/team-plan.md).
 */
public class AutoRejectOverdueLeaveRequestsJob implements BackgroundJob {

    private final LeaveRequestService leaveRequestService;

    public AutoRejectOverdueLeaveRequestsJob() {
        this.leaveRequestService = new LeaveRequestService();
    }

    @Override
    public String getName() {
        return "JOB-07 Remind and auto-reject overdue leave requests";
    }

    @Override
    public void run() throws SQLException {
        leaveRequestService.processJob07();
    }
}
