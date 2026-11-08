package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO 1 NHÓM buổi cùng lý do trong khung "Cần để ý" — Service TẠO: MyBookingService.groupByReason.
 * JSP đọc: _booking-groups.jspf (biến "g").
 */
public class SessionGroup {
    // ===== IN RA MÀN =====
    // ← trạng thái chung của nhóm → màu nhãn (data-status)
    private final String statusKey;
    // ← chữ trạng thái → nhãn nhóm
    private final String statusLabel;
    // ← lý do chung (VD "Chưa có gói thành viên ngày này") → chú thích nhóm
    private final String explanation;
    // ← các buổi trong nhóm → "N buổi" + danh sách ngày (bấm mở buổi)
    private final List<MyPtSessionRow> sessions = new ArrayList<>();

    public SessionGroup(String statusKey, String statusLabel, String explanation) {
        this.statusKey = statusKey;
        this.statusLabel = statusLabel;
        this.explanation = explanation;
    }

    public String getStatusKey() { return statusKey; }
    public String getStatusLabel() { return statusLabel; }
    public String getExplanation() { return explanation; }
    public List<MyPtSessionRow> getSessions() { return sessions; }
}
