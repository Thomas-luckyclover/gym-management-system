package com.gms.job;

import com.gms.service.AutoCloseSessionService;
import java.sql.SQLException;

/**
 * JOB-04 — tự chốt trạng thái buổi tập mà PT chưa xử lý (Hủy buổi)
 * Ai chạy: BackgroundJobScheduler, mỗi ngày 00:05 (sau JOB-01/02/03, trước JOB-08).
 */
public class AutoCloseUnmarkedSessionsJob implements BackgroundJob {
    // Tên job in ra log.
    // Hàm này chỉ dùng cho log. Khi lỗi, Scheduler in "[JOB] JOB-04 Auto close… lỗi DB, thử lại sau 5 phút". @Override báo cho compiler kiểm tra: hàm này đúng là hàm trong interface. Gõ sai tên thì compiler báo lỗi ngay
    @Override
    public String getName() {
        return "JOB-04 Auto close unmarked PT sessions";
    }

    // Gọi AutoCloseSessionService.runJob() (ngày = hôm nay).
    // new AutoCloseSessionService(): mỗi lượt tạo một Service mới. Service không giữ dữ liệu giữa các lần chạy, nên tạo mới cho sạch.
    // .runJob() không truyền ngày. Bên trong, nó gọi runJob(DateTimeUtil.today()), tức lấy hôm nay theo giờ Việt Nam.
    
    @Override
    public void run() throws SQLException {
        new AutoCloseSessionService().runJob();
    }
}
