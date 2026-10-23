package com.gms.job;

import com.gms.service.AutoCloseSessionService;
import java.sql.SQLException;

/**
 * JOB-04 — tự chốt các buổi PT còn Scheduled sau khi ngày tập đã kết thúc.
 *
 * AI GỌI?  BackgroundJobScheduler, lượt hằng ngày 00:05 (thứ 4 trong DAILY_JOBS).
 * LÀM GÌ?  Chỉ gọi AutoCloseSessionService.runJob(). Luật chốt nằm ở Service, SQL nằm ở AutoCloseSessionDAO.
 * KẾT QUẢ HIỆN Ở ĐÂU?  Nhãn "Đã check-in" / "Vắng" trên Lịch tập + Gói của tôi của khách,
 *                      và số buổi đã chốt + hoa hồng trên PT Dashboard.
 */
public class AutoCloseUnmarkedSessionsJob implements BackgroundJob {

    /** Tên in ra log của scheduler. */
    @Override
    public String getName() {
        return "JOB-04 Auto close unmarked PT sessions";
    }

    /** HÀM CHÍNH — BackgroundJobScheduler gọi mỗi ngày. Service tự chốt từng buổi trong transaction riêng. */
    @Override
    public void run() throws SQLException {
        new AutoCloseSessionService().runJob();
    }
}
