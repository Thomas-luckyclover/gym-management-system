package com.gms.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO chứa toàn bộ dữ liệu hiển thị và xuất CSV cho Báo cáo vận hành hệ thống Admin (UC-44, màn #68).
 *
 * <p>Tách riêng thành DTO có kiểu rõ ràng giúp tầng Service, Servlet, JSP và Unit Test
 * trao đổi dữ liệu an toàn mà không phải ép kiểu {@code Map<String, Object>} thủ công.</p>
 */
public class AdminDashboardView {

    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final long totalRevenue;
    private final int checkInCount;
    private final int dayPassVisits;
    private final long dayPassRevenue;
    private final List<PackageRevenueRow> revenueByPackage;
    private final List<PtCommissionRow> ptCommissions;
    private final int signInCount;
    private final int signInFailedCount;
    private final int tempLockedCount;
    private final int lockedAccountCount;
    private final int accountAuditChangeCount;
    private final List<SensitiveActionRow> sensitiveActions;
    private final List<SensitiveActorRow> sensitiveActors;

    /**
     * Khởi tạo đầy đủ số liệu cho Admin Dashboard trong khoảng ngày báo cáo.
     */
    public AdminDashboardView(
            LocalDate fromDate,
            LocalDate toDate,
            long totalRevenue,
            int checkInCount,
            int dayPassVisits,
            long dayPassRevenue,
            List<PackageRevenueRow> revenueByPackage,
            List<PtCommissionRow> ptCommissions,
            int signInCount,
            int signInFailedCount,
            int tempLockedCount,
            int lockedAccountCount,
            int accountAuditChangeCount,
            List<SensitiveActionRow> sensitiveActions,
            List<SensitiveActorRow> sensitiveActors
    ) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.totalRevenue = totalRevenue;
        this.checkInCount = checkInCount;
        this.dayPassVisits = dayPassVisits;
        this.dayPassRevenue = dayPassRevenue;
        this.revenueByPackage = revenueByPackage != null ? List.copyOf(revenueByPackage) : List.of();
        this.ptCommissions = ptCommissions != null ? List.copyOf(ptCommissions) : List.of();
        this.signInCount = signInCount;
        this.signInFailedCount = signInFailedCount;
        this.tempLockedCount = tempLockedCount;
        this.lockedAccountCount = lockedAccountCount;
        this.accountAuditChangeCount = accountAuditChangeCount;
        this.sensitiveActions = sensitiveActions != null ? List.copyOf(sensitiveActions) : List.of();
        this.sensitiveActors = sensitiveActors != null ? List.copyOf(sensitiveActors) : List.of();
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public long getTotalRevenue() {
        return totalRevenue;
    }

    public int getCheckInCount() {
        return checkInCount;
    }

    public int getDayPassVisits() {
        return dayPassVisits;
    }

    public long getDayPassRevenue() {
        return dayPassRevenue;
    }

    public List<PackageRevenueRow> getRevenueByPackage() {
        return revenueByPackage;
    }

    public List<PtCommissionRow> getPtCommissions() {
        return ptCommissions;
    }

    public int getSignInCount() {
        return signInCount;
    }

    public int getSignInFailedCount() {
        return signInFailedCount;
    }

    public int getTempLockedCount() {
        return tempLockedCount;
    }

    public int getLockedAccountCount() {
        return lockedAccountCount;
    }

    public int getAccountAuditChangeCount() {
        return accountAuditChangeCount;
    }

    public List<SensitiveActionRow> getSensitiveActions() {
        return sensitiveActions;
    }

    public List<SensitiveActorRow> getSensitiveActors() {
        return sensitiveActors;
    }

    /**
     * Kiểm tra toàn bộ báo cáo (cả vận hành lẫn audit) có hoàn toàn trống trong kỳ hay không (MSG03).
     *
     * @return {@code true} nếu không có bất kỳ số liệu vận hành hay kiểm toán nào phát sinh
     */
    public boolean isEmptyInRange() {
        return totalRevenue == 0L
                && checkInCount == 0
                && dayPassVisits == 0
                && dayPassRevenue == 0L
                && revenueByPackage.isEmpty()
                && ptCommissions.isEmpty()
                && signInCount == 0
                && signInFailedCount == 0
                && tempLockedCount == 0
                && lockedAccountCount == 0
                && accountAuditChangeCount == 0
                && sensitiveActions.isEmpty()
                && sensitiveActors.isEmpty();
    }

    /** Một dòng thống kê doanh thu và VAT theo từng gói dịch vụ. */
    public static class PackageRevenueRow {
        private final String packageName;
        private final int transactionCount;
        private final long revenue;
        private final long vatAmount;

        public PackageRevenueRow(String packageName, int transactionCount, long revenue, long vatAmount) {
            this.packageName = packageName;
            this.transactionCount = transactionCount;
            this.revenue = revenue;
            this.vatAmount = vatAmount;
        }

        public String getPackageName() {
            return packageName;
        }

        public int getTransactionCount() {
            return transactionCount;
        }

        public long getRevenue() {
            return revenue;
        }

        public long getVatAmount() {
            return vatAmount;
        }
    }

    /** Một dòng thống kê số buổi tính thưởng và tổng hoa hồng của từng PT. */
    public static class PtCommissionRow {
        private final String employeeCode;
        private final String fullName;
        private final int sessionCount;
        private final long commissionAmount;

        public PtCommissionRow(String employeeCode, String fullName, int sessionCount, long commissionAmount) {
            this.employeeCode = employeeCode;
            this.fullName = fullName;
            this.sessionCount = sessionCount;
            this.commissionAmount = commissionAmount;
        }

        public String getEmployeeCode() {
            return employeeCode;
        }

        public String getFullName() {
            return fullName;
        }

        public int getSessionCount() {
            return sessionCount;
        }

        public long getCommissionAmount() {
            return commissionAmount;
        }
    }

    /** Một dòng thống kê số lần thực hiện theo loại thao tác nhạy cảm (GBR-07). */
    public static class SensitiveActionRow {
        private final String actionLabel;
        private final int total;

        public SensitiveActionRow(String actionLabel, int total) {
            this.actionLabel = actionLabel;
            this.total = total;
        }

        public String getActionLabel() {
            return actionLabel;
        }

        public int getTotal() {
            return total;
        }
    }

    /** Một dòng thống kê số thao tác nhạy cảm theo người thực hiện và vai trò lúc thao tác. */
    public static class SensitiveActorRow {
        private final String actorName;
        private final String actorRoleLabel;
        private final int total;

        public SensitiveActorRow(String actorName, String actorRoleLabel, int total) {
            this.actorName = actorName;
            this.actorRoleLabel = actorRoleLabel;
            this.total = total;
        }

        public String getActorName() {
            return actorName;
        }

        public String getActorRoleLabel() {
            return actorRoleLabel;
        }

        public int getTotal() {
            return total;
        }
    }
}

