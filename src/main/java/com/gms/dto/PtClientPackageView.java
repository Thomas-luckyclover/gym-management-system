package com.gms.dto;

import com.gms.entity.Package;
import java.time.LocalDate;

/**
 * DTO trung gian (KHÔNG lên JSP) — DAO TẠO: PtDashboardDAO.findClientPackages:
 * 1 dòng = 1 khách × 1 gói PT × 1 ngày có buổi trong 14 ngày tới, kèm quyền vào gym ngày đó.
 * PtDashboardService.buildAttentionItems lọc dòng có vấn đề → PtAttentionView.
 */
public class PtClientPackageView {
    // ===== DÙNG ĐỂ QUYẾT ĐỊNH "CẦN CHÚ Ý" =====
    // ← người đứng tên hoặc người tập cùng (gói đôi tách 2 dòng)
    private final int customerId;
    // ← tên khách → câu "… vẫn dùng được gym" của gói đôi
    private final String customerName;
    // ← gói PT → gom dòng trùng, tìm người kia của gói đôi
    private final int ptSubscriptionId;
    // ← PTSingle | PTCouple → gói đôi thì xét người kia
    private final Package.Type ptSubscriptionType;
    // ← ngày có buổi → giữ ngày SỚM NHẤT cho mỗi khách + gói
    private final LocalDate sessionDate;
    // ← có Membership Active/Frozen phủ ngày đó
    private final boolean hasMembershipOnSessionDate;
    // ← có FreezeRecords phủ ngày đó
    private final boolean frozenOnSessionDate;
    // ← ngày hết membership gần nhất → nhãn "Đã hết hạn" / "Không bao phủ ngày tập"
    private final LocalDate membershipEndDate;
    // ← ngày hết bảo lưu
    private final LocalDate freezeEndDate;
    // ← ngày hết gói PT
    private final LocalDate ptEndDate;
    // ← số buổi Scheduled còn lại của gói
    private final int scheduledSessionCount;

    public PtClientPackageView(int customerId, String customerName, int ptSubscriptionId,
            Package.Type ptSubscriptionType, LocalDate ptEndDate, int scheduledSessionCount,
            LocalDate sessionDate, LocalDate membershipEndDate, boolean hasMembershipOnSessionDate,
            boolean frozenOnSessionDate, LocalDate freezeEndDate) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.ptSubscriptionId = ptSubscriptionId;
        this.ptSubscriptionType = ptSubscriptionType;
        this.ptEndDate = ptEndDate;
        this.scheduledSessionCount = scheduledSessionCount;

        this.sessionDate = sessionDate;
        this.membershipEndDate = membershipEndDate;
        this.hasMembershipOnSessionDate = hasMembershipOnSessionDate;
        this.frozenOnSessionDate = frozenOnSessionDate;
        this.freezeEndDate = freezeEndDate;
    }

    public int getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getPtSubscriptionId() {
        return ptSubscriptionId;
    }

    public Package.Type getPtSubscriptionType() {
        return ptSubscriptionType;
    }

    public LocalDate getPtEndDate() {
        return ptEndDate;
    }

    public int getScheduledSessionCount() {
        return scheduledSessionCount;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public LocalDate getMembershipEndDate() {
        return membershipEndDate;
    }

    public boolean isHasMembershipOnSessionDate() {
        return hasMembershipOnSessionDate;
    }

    public boolean isFrozenOnSessionDate() {
        return frozenOnSessionDate;
    }

    public LocalDate getFreezeEndDate() {
        return freezeEndDate;
    }
}
