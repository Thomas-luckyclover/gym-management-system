package com.gms.job;

import com.gms.service.FreezeService;
import com.gms.util.DateTimeUtil;

import java.sql.SQLException;

/**
 * JOB-01 — Resume expired membership freezes (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: hằng ngày 00:05 (đầu chuỗi hằng ngày)
 * Luật: bảo lưu có Planned End Date < hôm nay → gói Frozen → Active; cộng số ngày đã bảo lưu vào End Date của gói (DBR-51).
 *
 * Người phụ trách gợi ý: TV4 (xem docs/team-plan.md).
 */
public class ResumeExpiredFreezesJob implements BackgroundJob {

    private final FreezeService freezeService;

    public ResumeExpiredFreezesJob() {
        this(new FreezeService());
    }

    public ResumeExpiredFreezesJob(FreezeService freezeService) {
        this.freezeService = freezeService;
    }

    @Override
    public String getName() {
        return "JOB-01 Resume expired membership freezes";
    }

    @Override
    public void run() throws SQLException {
        freezeService.resumeExpiredFreezes(DateTimeUtil.today());
    }
}
