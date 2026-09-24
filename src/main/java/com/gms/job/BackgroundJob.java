package com.gms.job;

import java.sql.SQLException;

/**
 * Một tác vụ chạy ngầm theo lịch (GBR-14, SRS "Background Job Inventory" JOB-01 … JOB-07).
 *
 * Quy tắc chung SRS 5.0 mà mọi job phải theo khi implement run():
 * - Chọn bản ghi theo TRẠNG THÁI, không theo "hôm qua" → lần chạy bị lỡ sẽ được lần sau xử lý bù.
 * - Mỗi bản ghi xử lý trong 1 transaction riêng, kiểm tra lại trạng thái ngay trước khi ghi;
 *   1 bản ghi lỗi không được chặn các bản ghi khác.
 * - Mọi thay đổi trạng thái ghi AuditLog với người thực hiện = System.
 * - Job chỉ gọi Service (không viết SQL trong job) — job giống 1 "Servlet chạy theo giờ".
 */
public interface BackgroundJob {

    /**
     * @return mã + tên job để ghi log, VD "JOB-05 Cancel overdue online orders"
     */
    String getName();

    /**
     * Chạy 1 lượt job.
     * @throws SQLException khi không kết nối được DB — scheduler sẽ thử lại theo SRS 5.0
     */
    void run() throws SQLException;
}
