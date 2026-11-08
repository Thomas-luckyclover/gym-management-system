package com.gms.dto;

/**
 * DTO "KẾT QUẢ THÁNG VÀ HOA HỒNG" — DAO TẠO: PtDashboardDAO.findCommissionSummary (đếm buổi trong tháng theo kết quả);
 * Service ĐIỀN: getCommissionSummary (tỷ lệ hoa hồng của PT) + prepareCommissionSummary (tổng đã chốt + % từng loại).
 * Servlet: "commissionSummary" → pt/dashboard.jsp (biến cs).
 */
public class PtCommissionSummaryView {
    // ===== IN RA MÀN — 3 ô tiền =====
    // ← SUM hoa hồng buổi CheckedIn / Missed / Cancelled do PT hoặc khách → ô "Đã chốt"
    private final long finalizedCommissionAmount;
    // ← SUM hoa hồng buổi đã qua giờ mà còn Scheduled (chờ JOB-04) → ô "Chờ hệ thống chốt"
    private final long pendingCommissionAmount;
    // ← số buổi đó → "N buổi"
    private final int pendingSessionCount;
    // ← SUM hoa hồng buổi Scheduled chưa tới giờ → ô "Dự kiến"
    private final long projectedCommissionAmount;
    // ← số buổi đó → "N buổi"
    private final int projectedSessionCount;
    // ← Service: Employees.CommissionRate → "Tỷ lệ hoa hồng hiện tại: N%"
    private Integer commissionRate;

    // ===== IN RA MÀN — thanh tỷ lệ kết quả =====
    // ← prepareCommissionSummary: tổng 5 loại bên dưới → "N buổi đã chốt" (0 → "Chưa có buổi nào được chốt")
    private int resolvedSessionCount;
    // ← Status CheckedIn → đoạn xanh + chú thích "Có check-in N"
    private final int checkedInCount;
    // ← Missed + NoCheckIn → "Không check-in N"
    private final int noCheckInCount;
    // ← Missed + NoMembership → "Thiếu quyền vào gym N"
    private final int noMembershipCount;
    // ← Missed + MembershipFrozen → "Đang bảo lưu N"
    private final int membershipFrozenCount;
    // ← Status Cancelled → "Đã hủy N"
    private final int cancelledCount;
    // ← count × 100 / tổng (2 chữ số) → "%" cạnh chú thích
    private double checkedInRatePercent;
    // ← như trên
    private double noCheckInRatePercent;
    // ← như trên
    private double noMembershipRatePercent;
    // ← như trên
    private double membershipFrozenRatePercent;
    // ← như trên
    private double cancelledRatePercent;

    public PtCommissionSummaryView(int checkedInCount, int noCheckInCount,
            int noMembershipCount, int membershipFrozenCount, int cancelledCount,
            long finalizedCommissionAmount, int pendingSessionCount,
            long pendingCommissionAmount, int projectedSessionCount,
            long projectedCommissionAmount) {
        this.checkedInCount = checkedInCount;
        this.noCheckInCount = noCheckInCount;
        this.noMembershipCount = noMembershipCount;
        this.membershipFrozenCount = membershipFrozenCount;
        this.cancelledCount = cancelledCount;
        this.finalizedCommissionAmount = finalizedCommissionAmount;

        this.pendingSessionCount = pendingSessionCount;
        this.pendingCommissionAmount = pendingCommissionAmount;
        this.projectedSessionCount = projectedSessionCount;
        this.projectedCommissionAmount = projectedCommissionAmount;
    }

    public int getCheckedInCount() {
        return checkedInCount;
    }

    public int getNoCheckInCount() {
        return noCheckInCount;
    }

    public int getNoMembershipCount() {
        return noMembershipCount;
    }

    public int getMembershipFrozenCount() {
        return membershipFrozenCount;
    }

    public int getCancelledCount() {
        return cancelledCount;
    }

    public long getFinalizedCommissionAmount() {
        return finalizedCommissionAmount;
    }

    public int getPendingSessionCount() {
        return pendingSessionCount;
    }

    public long getPendingCommissionAmount() {
        return pendingCommissionAmount;
    }

    public int getProjectedSessionCount() {
        return projectedSessionCount;
    }

    public long getProjectedCommissionAmount() {
        return projectedCommissionAmount;
    }

    public void setResolvedStatistics(int resolvedSessionCount, double checkedInRatePercent,
            double noCheckInRatePercent, double noMembershipRatePercent,
            double membershipFrozenRatePercent, double cancelledRatePercent) {
        this.resolvedSessionCount = resolvedSessionCount;
        this.checkedInRatePercent = checkedInRatePercent;
        this.noCheckInRatePercent = noCheckInRatePercent;
        this.noMembershipRatePercent = noMembershipRatePercent;
        this.membershipFrozenRatePercent = membershipFrozenRatePercent;
        this.cancelledRatePercent = cancelledRatePercent;
    }

    public int getResolvedSessionCount() {
        return resolvedSessionCount;
    }

    public double getCheckedInRatePercent() {
        return checkedInRatePercent;
    }

    public double getNoCheckInRatePercent() {
        return noCheckInRatePercent;
    }

    public double getNoMembershipRatePercent() {
        return noMembershipRatePercent;
    }

    public double getMembershipFrozenRatePercent() {
        return membershipFrozenRatePercent;
    }

    public double getCancelledRatePercent() {
        return cancelledRatePercent;
    }

    public void setCommissionRate(Integer commissionRate) {
        this.commissionRate = commissionRate;
    }

    public Integer getCommissionRate() {
        return commissionRate;
    }
}
