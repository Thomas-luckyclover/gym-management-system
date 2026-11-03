package com.gms.dto;

import java.time.LocalDate;

/**
 * DTO 1 VIỆC CẦN CHÚ Ý của 1 khách — Service TẠO: PtDashboardService (buildClosedDayItem / buildAttentionItem).
 * Servlet: "attentionItems" (đếm ô "Cần chú ý") và gắn vào từng khách (PtAssignedPackageView.attentionItems).
 * JSP đọc: pt/dashboard.jsp, khung "Khách phụ trách" (biến item).
 */
public class PtAttentionView {
    // ===== IN RA MÀN =====
    // ← "closedDay" (buổi rơi ngày phòng nghỉ) | "frozen" (khách bảo lưu) | "membership" (thiếu gói gym) → màu khung + nút "Dời buổi" (closedDay)
    private final String kind;
    // ← chỉ closedDay: buổi cần dời → link "Dời buổi" (/pt/schedule?handle=<id>)
    private final Integer sessionId;
    // ← ngày buổi gặp vấn đề → "buổi dd/MM", ngày của link Dời buổi
    private final LocalDate sessionDate;
    // ← "Ngày phòng nghỉ — cần dời" | "Membership đang bảo lưu" | "Cần membership" → chữ đậm của khung
    private final String attentionTitle;
    // ← câu giải thích (gói đôi: kèm tình trạng người kia) → dòng ghi chú (closedDay hoặc gói đôi)
    private final String attentionMessage;

    // ===== DÙNG ĐỂ GHÉP / SẮP XẾP =====
    // ← gói PT → mergeClientAttention gắn việc vào đúng khách; getAssignedPackages lấy thêm gói này
    private final int ptSubscriptionId;

    // ===== KHÔNG IN RA MÀN (giữ để mở rộng) =====
    private final int customerId;
    private final String customerName;
    private final LocalDate ptEndDate;
    private final int scheduledSessionCount;
    private final LocalDate membershipEndDate;
    private final LocalDate freezeEndDate;
    private final String ptSubscriptionTypeLabel;
    private final String membershipStatusLabel;

    public PtAttentionView(int customerId, int ptSubscriptionId, String customerName, LocalDate ptEndDate, int scheduledSessionCount,
            LocalDate sessionDate, LocalDate membershipEndDate, LocalDate freezeEndDate,
            String ptSubscriptionTypeLabel, String membershipStatusLabel,
            String attentionTitle, String attentionMessage) {
        this(customerId, ptSubscriptionId, customerName, ptEndDate, scheduledSessionCount,
                sessionDate, membershipEndDate, freezeEndDate, ptSubscriptionTypeLabel,
                membershipStatusLabel, attentionTitle, attentionMessage,
                freezeEndDate == null ? "membership" : "frozen", null);
    }

    public PtAttentionView(int customerId, int ptSubscriptionId, String customerName, LocalDate ptEndDate,
            int scheduledSessionCount, LocalDate sessionDate, LocalDate membershipEndDate,
            LocalDate freezeEndDate, String ptSubscriptionTypeLabel, String membershipStatusLabel,
            String attentionTitle, String attentionMessage, String kind, Integer sessionId) {
        this.kind = kind;
        this.sessionId = sessionId;

        this.customerId = customerId;
        this.ptSubscriptionId = ptSubscriptionId;
        this.customerName = customerName;
        this.ptEndDate = ptEndDate;
        this.scheduledSessionCount = scheduledSessionCount;
        this.sessionDate = sessionDate;
        this.membershipEndDate = membershipEndDate;
        this.freezeEndDate = freezeEndDate;

        this.ptSubscriptionTypeLabel = ptSubscriptionTypeLabel;
        this.membershipStatusLabel = membershipStatusLabel;

        this.attentionTitle = attentionTitle;
        this.attentionMessage = attentionMessage;
    }

    public int getCustomerId() { return customerId; }

    public String getKind() { return kind; }

    public Integer getSessionId() { return sessionId; }
    public int getPtSubscriptionId() { return ptSubscriptionId; }

    public String getCustomerName() {
        return customerName;
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

    public LocalDate getFreezeEndDate() {
        return freezeEndDate;
    }

    public String getPtSubscriptionTypeLabel() {
        return ptSubscriptionTypeLabel;
    }

    public String getMembershipStatusLabel() {
        return membershipStatusLabel;
    }

    public String getAttentionTitle() {
        return attentionTitle;
    }

    public String getAttentionMessage() {
        return attentionMessage;
    }
}
