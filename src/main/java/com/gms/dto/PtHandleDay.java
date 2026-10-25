package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MỘT NGÀY trong bộ chọn khung của "Dời buổi": tên ngày + các khung hợp lệ của ngày đó.
 *
 *   Ai tạo?   PtSessionHandleService.groupByDay (gom các PtHandleSlot cùng ngày).
 *   Ai in?    _handle-dialog.jspf: mỗi ngày 1 hàng "Thứ 5 · 29/10", các giờ là viên radio.
 */
public class PtHandleDay {

    private final LocalDate date;
    private final String label;                          // "Thứ 5 · 29/10"
    private final List<PtHandleSlot> slots = new ArrayList<>();

    public PtHandleDay(LocalDate date, String label) {
        this.date = date;
        this.label = label;
    }

    public LocalDate getDate() { return date; }

    public String getLabel() { return label; }

    public List<PtHandleSlot> getSlots() { return slots; }
}
