package com.gms.service;

import com.gms.dao.ExpireDayPassDAO;
import com.gms.entity.Subscription;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * SERVICE CỦA JOB-03 — TỰ ĐỘNG HẾT HẠN VÉ NGÀY CHƯA SỬ DỤNG.
 *
 * Luật: vé ngày (DayPass) đã qua ngày hiệu lực (EndDate < today) mà chưa check-in -> đổi thành Expired.
 */
public class ExpireDayPassService {

    private final ExpireDayPassDAO expireDayPassDAO;
    private final AuditLogService auditLogService;

    public ExpireDayPassService() {
        this(new ExpireDayPassDAO(), new AuditLogService());
    }

    public ExpireDayPassService(ExpireDayPassDAO expireDayPassDAO, AuditLogService auditLogService) {
        this.expireDayPassDAO = expireDayPassDAO;
        this.auditLogService = auditLogService;
    }

    /**
     * HÀM CHÍNH — ExpireUnusedDayPassesJob.run() gọi mỗi ngày lúc 00:05.
     */
    public int runJob() throws SQLException {
        return runJob(DateTimeUtil.today());
    }

    /**
     * HÀM CHÍNH — Quét các vé ngày quá hạn chưa dùng và chuyển trạng thái sang Expired.
     */
    public int runJob(LocalDate today) throws SQLException {
        if (today == null) {
            today = DateTimeUtil.today();
        }

        // B1: lấy danh sách mã vé ngày quá hạn chưa check-in
        List<Integer> expiredIds = expireDayPassDAO.findExpiredUnusedDayPassIds(today);
        int expiredCount = 0;

        // B2: duyệt từng vé, đổi Status -> Expired và ghi AuditLog
        for (Integer subscriptionId : expiredIds) {
            try (Connection conn = openConnection()) {
                conn.setAutoCommit(false);
                try {
                    boolean updated = expireDayPassDAO.markDayPassExpired(conn, subscriptionId);
                    if (!updated) {
                        conn.rollback();
                        continue;
                    }

                    auditLogService.logInTransaction(
                            conn,
                            null,
                            "JOB-03",
                            "Subscriptions",
                            subscriptionId,
                            "Status",
                            Subscription.Status.ACTIVE.getDbValue(),
                            Subscription.Status.EXPIRED.getDbValue(),
                            "Vé ngày hết hạn chưa sử dụng");

                    conn.commit();
                    expiredCount++;
                } catch (SQLException e) {
                    conn.rollback();
                    e.printStackTrace();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return expiredCount;
    }

    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }
}
