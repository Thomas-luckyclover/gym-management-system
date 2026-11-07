package com.gms.dto;

import java.time.LocalDate;

/**
 * DTO 1 VIỆC CẦN CHÚ Ý của 1 khách — Service TẠO: PtDashboardService (buildClosedDayItem / buildAttentionItem).
 * Servlet: "attentionItems" (đếm ô "Cần chú ý") và gắn vào từng khách (PtAssignedPackageView.attentionItems).
 * JSP đọc: pt/dashboard.jsp, khung "Khách phụ trách" (biến item).
 */
public class PtAttentionView {
    // ===== IN RA MÀN =====
    // ← "closedDay" (buổi rơi lúc phòng đóng: nghỉ cả ngày / ngoài giờ) | "frozen" (khách bảo lưu) | "membership" (thiếu gói gym) → màu khung + nút "Dời buổi" (closedDay)
    private final String kind;
    // ← chỉ closedDay: buổi cần dời → link "Dời buổi" (/pt/schedule?handle=<id>)
    private final Integer sessionId;
    // ← ngày buổi gặp vấn đề → "buổi dd/MM", ngày của link Dời buổi
    private final LocalDate sessionDate;
    // ← "Ngày phòng nghỉ — cần dời" | "Ngoài giờ mở cửa — cần dời" | "Chưa rõ giờ mở cửa — cần kiểm tra" | "Membership đang bảo lưu" | "Cần membership" → chữ đậm của khung
    private final String attentionTitle;
    // ← câu giải thích (gói đôi: kèm tình trạng người kia) → dòng ghi chú (closedDay hoặc gói đôi)
    private final String attentionMessage;

    // ← tên khách của việc → "· tên" (gói đôi)
    private final String customerName;

    // ===== DÙNG ĐỂ GHÉP / SẮP XẾP =====
    // ← gói PT → mergeClientAttention gắn việc vào đúng khách; getAssignedPackages lấy thêm gói này
    private final int ptSubscriptionId;

    public PtAttentionView(
            String kind,
            Integer sessionId,
            LocalDate sessionDate,
            String attentionTitle,
            String attentionMessage,
            int ptSubscriptionId,
            String customerName) {
        this.kind = kind;
        this.sessionId = sessionId;
        this.sessionDate = sessionDate;
        this.attentionTitle = attentionTitle;
        this.attentionMessage = attentionMessage;
        this.ptSubscriptionId = ptSubscriptionId;
        this.customerName = customerName;
    }

    public String getKind() { return kind; }

    public Integer getSessionId() { return sessionId; }
    public int getPtSubscriptionId() { return ptSubscriptionId; }

    public String getCustomerName() {
        return customerName;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public String getAttentionTitle() {
        return attentionTitle;
    }

    public String getAttentionMessage() {
        return attentionMessage;
    }
}
