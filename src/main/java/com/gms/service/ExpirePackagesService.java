package com.gms.service;

import com.gms.dao.ExpirePackagesDAO;
import com.gms.dao.ExpirePackagesDAO.ExpiringPackage;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JOB-02 — hết hạn gói tập và gói PT (SRS 16.2; CBR-03, CBR-55, CBR-58, DBR-36, DBR-37).
 * Ai gọi: ExpireMembershipPackagesJob (00:05 hằng ngày, sau JOB-01, trước JOB-03 / JOB-04).
 *
 * HÀM CHÍNH runJob(today):
 *   B1: Id các gói Membership / PTSingle / PTCouple còn Active mà EndDate < hôm nay
 *   B2: xử lý TỪNG gói trong 1 transaction riêng (expireOne) — 1 gói lỗi không chặn gói khác
 *
 * expireOne:
 *   B1: khóa gói, kiểm lại còn Active + đã qua EndDate (null → bỏ qua)
 *   B2: gói → Expired
 *   B3: gói PT: buổi còn Scheduled SAU ngày hết hạn → Cancelled / PackageExpired, khung → Free (không hoàn tiền, không gia hạn).
 *       Buổi trong hạn để JOB-04 chốt theo check-in.
 *   B4: khách không còn gói thành viên Active/Frozen và vé ngày còn dùng → Expired (CBR-03)
 *   B5: AuditLogs "JOB-02" trong cùng transaction → commit
 */
public class ExpirePackagesService {

    private final ExpirePackagesDAO packagesDAO;
    private final AuditLogService auditLogService;

    public ExpirePackagesService() {
        this(new ExpirePackagesDAO(), new AuditLogService());
    }

    public ExpirePackagesService(ExpirePackagesDAO packagesDAO, AuditLogService auditLogService) {
        this.packagesDAO = packagesDAO;
        this.auditLogService = auditLogService;
    }

    /** Job gọi: chạy với hôm nay (giờ Việt Nam). Trả về số gói đã chuyển Expired. */
    public int runJob() throws SQLException {
        return runJob(DateTimeUtil.today());
    }

    /** Test gọi thẳng với 1 ngày cho trước. */
    public int runJob(LocalDate today) throws SQLException {
        // B1
        List<Integer> ids;
        try (Connection connection = openConnection()) {
            ids = packagesDAO.findExpiredIds(connection, today);
        }
        // B2
        int expired = 0;
        for (int id : ids) {
            if (expireOne(id, today)) {
                expired++;
            }
        }
        System.out.println("[JOB-02] Đã chuyển Expired " + expired + "/" + ids.size() + " gói.");
        return expired;
    }

    /** HÀM PHỤ (runJob B2) — 1 gói, 1 transaction. true = đã hết hạn gói này. */
    private boolean expireOne(int subscriptionId, LocalDate today) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                // B1
                ExpiringPackage pkg = packagesDAO.lockExpired(connection, subscriptionId, today);
                if (pkg == null) {
                    connection.rollback();
                    return false;
                }
                // B2
                if (!packagesDAO.expire(connection, subscriptionId)) {
                    connection.rollback();
                    return false;
                }
                auditLogService.logInTransaction(connection, null, "JOB-02", "Subscriptions", subscriptionId,
                        "Status", "Active", "Expired", "Hết hạn ngày " + DateTimeUtil.formatDate(pkg.endDate()));

                // B3
                boolean isPtPackage = !"Membership".equals(pkg.subscriptionType());
                if (isPtPackage) {
                    LocalDateTime now = DateTimeUtil.now();
                    int cancelled = packagesDAO.cancelSessionsAfterEnd(connection, subscriptionId, pkg.endDate(), now);
                    if (cancelled > 0) {
                        auditLogService.logInTransaction(connection, null, "JOB-02", "Subscriptions", subscriptionId,
                                "Sessions.Status", "Scheduled", "Cancelled",
                                cancelled + " buổi sau ngày hết hạn gói → PackageExpired (CBR-55)");
                    }
                }

                // B4
                if (packagesDAO.expireCustomerIfNoAccess(connection, pkg.customerId(), today)) {
                    auditLogService.logInTransaction(connection, null, "JOB-02", "Customers", pkg.customerId(),
                            "Status", null, "Expired", "Không còn gói tập / vé ngày còn hiệu lực (CBR-03)");
                }

                // B5
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            System.err.println("[JOB-02] SubscriptionId=" + subscriptionId);
            exception.printStackTrace();
            return false;
        }
    }

    /** Mở kết nối DB (test ghi đè để dùng kết nối giả). */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }
}
