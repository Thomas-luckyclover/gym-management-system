package com.gms.job;

import com.gms.service.ExpirePackagesService;
import java.sql.SQLException;

/**
 * JOB-02 — Expire membership packages (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: hằng ngày, ngay sau JOB-01
 * Luật: gói tập / gói PT có End Date < hôm nay → Expired; gói PT: buổi Scheduled sau ngày hết hạn → Cancelled
 * (PackageExpired, CBR-55); hội viên không còn gói tập / vé ngày nào → Expired (CBR-03). Logic: ExpirePackagesService.
 */
public class ExpireMembershipPackagesJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-02 Expire membership packages";
    }

    @Override
    public void run() throws SQLException {
        new ExpirePackagesService().runJob();
    }
}
