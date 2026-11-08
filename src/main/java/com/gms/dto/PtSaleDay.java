package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * KẾT QUẢ 1 NGÀY + 1 GIỜ: có xếp buổi được không — Service tạo: PtSaleService.checkDay (luật duy nhất, dùng cả lúc xem lẫn lúc giữ).
 * Hiện ở: lưới "N buổi tập · Đang giữ" (_pt-sale-held.jspf, biến d). isSession() = có buổi (action ≠ skip).
 */
public class PtSaleDay {
    // ===== IN RA MÀN =====
    // ← ngày → "dd/MM" trên ô, cột thứ
    private final LocalDate date;
    // ← "Thứ 3 · 05/11" → dòng ghi chú dưới lưới
    private final String dayLabel;
    // ← giờ bắt đầu → giờ trên ô
    private final LocalTime startTime;
    // ← whySkip: lý do không xếp được → ô xám + dòng ghi chú
    private final String reason;
    // ← warningFor: có buổi nhưng PT cần dời sau (phòng nghỉ / PT nghỉ) → ô vàng + ghi chú
    private final String warning;

    // ===== DÙNG KHI GIỮ LỊCH (không in) =====
    // ← book (PT có khung Free đúng giờ → giữ khung đó) | create (PT chưa mở → tạo khung mới) | skip
    private final String action;
    // ← TimeSlotId của khung Free (chỉ action = book) → hold() UPDATE khung này
    private final Integer slotId;
    // ← giờ kết thúc (start + P-06) → INSERT khung mới
    private final LocalTime endTime;

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

    // GETTER TỰ TÍNH — không có field/setter "session"; = action khác "skip"; JSP: receptionist/_pt-sale-held.jspf:22 …
    public boolean isSession() { return !"skip".equals(action); }
}
