package com.gms.dto;

/**
 * Số buổi và hoa hồng của một PT trong tháng đang xem trên dashboard.
 * PtDashboardDAO tạo DTO từ Sessions và TimeSlots; PtDashboardService bổ sung các tỷ lệ.
 * Servlet truyền một object bằng attribute commissionSummary cho JSP.
 */
public class PtCommissionSummaryView {

    // ===== 1. PtDashboardDAO điền: kết quả buổi đã chốt =====

    // COUNT Sessions.Status = CheckedIn. JSP in "Đã check-in: 8 buổi".
    private final int checkedInCount;

    // COUNT Missed/NoCheckIn. JSP in "Không check-in: 2 buổi".
    private final int noCheckInCount;

    // COUNT Missed/NoMembership. JSP in "Thiếu membership: 1 buổi".
    private final int noMembershipCount;

    // COUNT Missed/MembershipFrozen. JSP in "Bảo lưu: 1 buổi".
    private final int membershipFrozenCount;

    // COUNT mọi Sessions.Status = Cancelled, kể cả hủy không hưởng hoa hồng.
    // JSP in "Đã hủy: 2 buổi".
    private final int cancelledCount;

    // SUM Sessions.CommissionAmount của CheckedIn, Missed và Cancelled ByPt/ByCustomer.
    // JSP định dạng tiền, ví dụ "Hoa hồng đã chốt: 1.610.000 đ".
    private final long finalizedCommissionAmount;

    // ===== 2. PtDashboardDAO điền: buổi vẫn Scheduled =====

    // COUNT Scheduled có TimeSlots.SlotDate + EndTime <= now giờ VN.
    // JSP in "Chờ hệ thống chốt: 10 buổi".
    private final int pendingSessionCount;

    // SUM Sessions.CommissionAmount của các buổi Pending; JSP in tiền đang chờ chốt.
    private final long pendingCommissionAmount;

    // COUNT Scheduled có TimeSlots.SlotDate + EndTime > now giờ VN.
    // JSP in "Dự kiến: 36 buổi".
    private final int projectedSessionCount;

    // SUM Sessions.CommissionAmount của các buổi Projected; JSP in tiền dự kiến.
    private final long projectedCommissionAmount;

    // ===== 3. PtDashboardService tính từ 5 số buổi đã chốt ở nhóm 1 =====

    // Tổng cả 5 loại, gồm Cancelled; là mẫu số của các tỷ lệ bên dưới.
    // JSP có thể in "14 buổi đã có kết quả".
    private int resolvedSessionCount;

    // Service: checkedInCount / resolvedSessionCount * 100; JSP in "57,14%".
    private double checkedInRatePercent;

    // Service: noCheckInCount / resolvedSessionCount * 100; JSP in tỷ lệ khách không check-in.
    private double noCheckInRatePercent;

    // Service: noMembershipCount / resolvedSessionCount * 100; JSP in tỷ lệ thiếu membership.
    private double noMembershipRatePercent;

    // Service: membershipFrozenCount / resolvedSessionCount * 100; JSP in tỷ lệ bảo lưu.
    private double membershipFrozenRatePercent;

    // Service: cancelledCount / resolvedSessionCount * 100; JSP in tỷ lệ hủy.
    private double cancelledRatePercent;

    // Service: Employees.CommissionRate của PT đang đăng nhập; đây là tỷ lệ hiện tại.
    // Tiền của các buổi cũ vẫn lấy từ Sessions.CommissionAmount đã lưu.
    private Integer commissionRate;

    /** DAO truyền mười số tổng hợp của một PT trong tháng vào đúng thứ tự các nhóm trên. */
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

    // ===== 4. Getter: số và tiền do DAO lấy; JSP chỉ đọc để hiển thị =====

    /** JSP đọc số buổi khách có check-in. */
    public int getCheckedInCount() {
        return checkedInCount;
    }

    /** JSP đọc số buổi Missed vì khách không check-in. */
    public int getNoCheckInCount() {
        return noCheckInCount;
    }

    /** JSP đọc số buổi Missed vì thiếu membership. */
    public int getNoMembershipCount() {
        return noMembershipCount;
    }

    /** JSP đọc số buổi Missed vì membership đang bảo lưu. */
    public int getMembershipFrozenCount() {
        return membershipFrozenCount;
    }

    /** JSP đọc số buổi hủy của mọi lý do. */
    public int getCancelledCount() {
        return cancelledCount;
    }

    /** JSP định dạng tổng hoa hồng đã chốt thành tiền Việt Nam. */
    public long getFinalizedCommissionAmount() {
        return finalizedCommissionAmount;
    }

    /** JSP đọc số buổi đã kết thúc nhưng đang chờ JOB-04. */
    public int getPendingSessionCount() {
        return pendingSessionCount;
    }

    /** JSP định dạng tiền của các buổi đang chờ JOB-04. */
    public long getPendingCommissionAmount() {
        return pendingCommissionAmount;
    }

    /** JSP đọc số buổi Scheduled chưa kết thúc. */
    public int getProjectedSessionCount() {
        return projectedSessionCount;
    }

    /** JSP định dạng tiền dự kiến của các buổi Scheduled chưa kết thúc. */
    public long getProjectedCommissionAmount() {
        return projectedCommissionAmount;
    }

    // ===== 5. Service điền kết quả tính toán; JSP chỉ đọc =====

    /** Service truyền mẫu số và năm tỷ lệ sau khi tính; DTO không tự tính nghiệp vụ. */
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

    /** JSP đọc mẫu số chung để giải thích năm tỷ lệ. */
    public int getResolvedSessionCount() {
        return resolvedSessionCount;
    }

    /** JSP in tỷ lệ CheckedIn theo phần trăm. */
    public double getCheckedInRatePercent() {
        return checkedInRatePercent;
    }

    /** JSP in tỷ lệ NoCheckIn theo phần trăm. */
    public double getNoCheckInRatePercent() {
        return noCheckInRatePercent;
    }

    /** JSP in tỷ lệ NoMembership theo phần trăm. */
    public double getNoMembershipRatePercent() {
        return noMembershipRatePercent;
    }

    /** JSP in tỷ lệ MembershipFrozen theo phần trăm. */
    public double getMembershipFrozenRatePercent() {
        return membershipFrozenRatePercent;
    }

    /** JSP in tỷ lệ mọi buổi Cancelled theo phần trăm. */
    public double getCancelledRatePercent() {
        return cancelledRatePercent;
    }

    /** Service gắn tỷ lệ hiện tại sau khi xác định đúng hồ sơ PT. */
    public void setCommissionRate(Integer commissionRate) {
        this.commissionRate = commissionRate;
    }

    /** JSP hiện tỷ lệ hoa hồng hiện tại của PT, ví dụ "30%". */
    public Integer getCommissionRate() {
        return commissionRate;
    }
}
