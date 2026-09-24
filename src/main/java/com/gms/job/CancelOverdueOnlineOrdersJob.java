package com.gms.job;

import java.sql.SQLException;

/**
 * JOB-05 — Cancel overdue online orders (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: mỗi phút
 * Luật: đơn online còn Pending quá P-09 (20 phút) kể từ lúc tạo → Cancelled (CBR-70); hoàn lại lượt dùng voucher; slot PT đang Held → Free (DBR-36).
 *
 * Người phụ trách gợi ý: TV1 (xem docs/team-plan.md).
 * TODO: gọi Service tương ứng trong run(); job KHÔNG tự viết SQL.
 */
public class CancelOverdueOnlineOrdersJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-05 Cancel overdue online orders";
    }

    @Override
    public void run() throws SQLException {
        // TODO(JOB-05): gọi Service xử lý, VD: new XxxService().runJob();
    }
}
