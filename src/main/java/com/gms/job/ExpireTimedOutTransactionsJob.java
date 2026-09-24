package com.gms.job;

import java.sql.SQLException;

/**
 * JOB-06 — Expire timed-out payment transactions (SRS 5.3 Background Job Inventory).
 *
 * Lịch chạy: mỗi phút
 * Luật: giao dịch Pending quá hạn riêng của nó (hạn link thanh toán, không vượt hạn của đơn) → Expired; đơn vẫn Pending để khách thanh toán lại.
 *
 * Người phụ trách gợi ý: TV1 (xem docs/team-plan.md).
 * TODO: gọi Service tương ứng trong run(); job KHÔNG tự viết SQL.
 */
public class ExpireTimedOutTransactionsJob implements BackgroundJob {

    @Override
    public String getName() {
        return "JOB-06 Expire timed-out payment transactions";
    }

    @Override
    public void run() throws SQLException {
        // TODO(JOB-06): gọi Service xử lý, VD: new XxxService().runJob();
    }
}
