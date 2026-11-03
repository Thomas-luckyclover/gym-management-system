package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO 1 NGÀY trong tab "Dời buổi" — Service TẠO: PtSessionHandleService.groupByDay.
 * JSP đọc: _handle-dialog.jspf (biến moveDay).
 */
public class PtHandleDay {
    // ===== IN RA MÀN =====
    // ← dayLabel: "Thứ 3 · 05/11" → tên hàng ngày
    private final String label;
    // ← các PtHandleSlot cùng ngày → các viên giờ để chọn
    private final List<PtHandleSlot> slots = new ArrayList<>();

    // ===== KHÔNG IN RA MÀN =====
    // ← ngày (dùng để gom)
    private final LocalDate date;

    public PtHandleDay(LocalDate date, String label) {
        this.date = date;
        this.label = label;
    }

    public LocalDate getDate() { return date; }

    public String getLabel() { return label; }

    public List<PtHandleSlot> getSlots() { return slots; }
}
