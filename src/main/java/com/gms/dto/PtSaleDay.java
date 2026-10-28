package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * MỘT NGÀY T2–T6 trong kỳ gói, trên bảng "Lịch dự kiến".
 *
 *   Ai tạo?   PtSaleService.checkDay (không đọc DB, test được) — 1 ngày + 1 giờ.
 *   Ai dùng?  pt-sales.jsp in lịch dự kiến; PtSaleService.hold giữ khung theo action.
 *
 *   action = "book"    PT đã công bố khung Free đúng giờ → giữ khung đó (slotId có giá trị)
 *            "create"  PT chưa có khung giờ này ngày đó → tạo khung mới (Held)
 *            "skip"    không có buổi ngày đó (reason nói vì sao) — không bù, không gia hạn (BR-41)
 *   warning  có buổi nhưng PT cần để ý (ngày phòng nghỉ, PT có đơn nghỉ) → PT tự dời sau
 */
public class PtSaleDay {

    private final LocalDate date;
    private final String dayLabel;     // "Thứ 3 · 27/10"
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String action;
    private final Integer slotId;
    private final String reason;       // chỉ khi action = skip
    private final String warning;      // có thể null

    public PtSaleDay(LocalDate date, String dayLabel, LocalTime startTime, LocalTime endTime,
            String action, Integer slotId, String reason, String warning) {
        this.date = date;
        this.dayLabel = dayLabel;
        this.startTime = startTime;
        this.endTime = endTime;
        this.action = action;
        this.slotId = slotId;
        this.reason = reason;
        this.warning = warning;
    }

    public LocalDate getDate() { return date; }
    public String getDayLabel() { return dayLabel; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getAction() { return action; }
    public Integer getSlotId() { return slotId; }
    public String getReason() { return reason; }
    public String getWarning() { return warning; }

    /** JSP viết ${d.session}: ngày này có buổi (book hoặc create). */
    public boolean isSession() { return !"skip".equals(action); }
}
