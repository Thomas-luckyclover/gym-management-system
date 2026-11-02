package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * MỘT BUỔI (1 ngày T2–T6 + 1 giờ) của gói đang bán.
 *
 *   AI TẠO?   SERVICE, bằng constructor (không có setter):
 *               PtSaleService.checkDay  — khi Giữ lịch: kiểm lại từng buổi gửi lên (không đọc DB, test được)
 *               PtSaleService.loadHeld  — trang ?order=: mỗi khung đang Held thành 1 buổi "book"
 *   AI DÙNG?  PtSaleService.hold: "book" → holdFreeSlot, "create" → insertHeldSlot.
 *             _pt-sale-held.jspf: lưới "N buổi tập · Đang giữ" (ô ngày + giờ, ghi chú dưới lưới).
 *
 *   action = "book"    PT đã công bố khung Free đúng giờ → giữ khung đó (slotId có giá trị)
 *            "create"  PT chưa có khung giờ này ngày đó → tạo khung mới (Held)
 *            "skip"    không có buổi ngày đó (reason nói vì sao) — không bù, không gia hạn (BR-41)
 *   warning  có buổi nhưng PT cần để ý (ngày phòng nghỉ, PT có đơn nghỉ) → PT tự dời sau
 */
public class PtSaleDay {

    //                                                                           In ra màn?
    private final LocalDate date;      // ngày của buổi → "27/10" trên ô lưới, cột theo thứ                CÓ
    private final String dayLabel;     // "Thứ 3 · 27/10" → đầu dòng ghi chú, tooltip ô                     CÓ
    private final LocalTime startTime; // giờ bắt đầu → "18:00" trên ô                                      CÓ
    private final LocalTime endTime;   // giờ kết thúc = start + P-06 phút                                   KHÔNG
    private final String action;       // book | create | skip (xem trên)                                    KHÔNG (qua isSession)
    private final Integer slotId;      // TimeSlots.TimeSlotId khi "book"; null khi "create"                 KHÔNG
    private final String reason;       // chỉ khi skip: vì sao không có buổi                                 CÓ (ghi chú đỏ)
    private final String warning;      // có buổi nhưng PT cần dời (phòng nghỉ / PT nghỉ); có thể null       CÓ (ghi chú vàng)

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
