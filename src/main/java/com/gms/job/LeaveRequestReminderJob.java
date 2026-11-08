package com.gms.job;

import com.gms.service.LeaveRequestService;
import java.sql.SQLException;

/** JOB-07: nhắc Manager về đơn đến hạn; chạy mỗi phút, không tự quyết định thay Manager. */
public class LeaveRequestReminderJob implements BackgroundJob {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestReminderJob() {
        this.leaveRequestService = new LeaveRequestService();
    }

    @Override
    public String getName() {
        return "JOB-07 Remind managers about due leave requests";
    }

    @Override
    public void run() throws SQLException {
        leaveRequestService.sendDueReminders();
    }
}
