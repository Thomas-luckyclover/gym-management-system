package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MỘT NGÀY trong bộ chọn khung của "Dời buổi": tên ngày + các khung hợp lệ của ngày đó.
 *
 *   Ai tạo?   PtSessionHandleService.groupByDay (gom các PtHandleSlot cùng ngày).
 *   Ai in?    _handle-dialog.jspf: mỗi ngày 1 hàng "Thứ 5 · 29/10", các giờ là viên radio.
 *   "· IN" cuối mỗi field = JSP Lịch dạy có in field này; "· không in" = chỉ dùng để tính / dựng link.
 */
public class PtHandleDay {

    private final LocalDate date;   // · IN
    private final String label;                          // "Thứ 5 · 29/10"   · IN
    private final List<PtHandleSlot> slots = new ArrayList<>();   // · IN

    public PtHandleDay(LocalDate date, String label) {
        this.date = date;
        this.label = label;
    }

    public LocalDate getDate() { return date; }

    public String getLabel() { return label; }

    public List<PtHandleSlot> getSlots() { return slots; }
}
