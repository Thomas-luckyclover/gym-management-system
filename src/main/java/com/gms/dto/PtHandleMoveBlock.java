package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * KHỐI "DỜI BUỔI" của hộp Xử lý buổi: khung trống của chính PT để dời sang + khoảng ngày cho "Giờ khác".
 * Service điền: PtSessionHandleService.findMoveDays (getHandleForm B4).
 * JSP đọc: _schedule-handle-reschedule.jspf (handle.move.xxx) và _schedule-handle.jspf (chữ "N khung" trên nút chọn).
 */
public class PtHandleMoveBlock {

    // ← findMySlots → lọc whyNotAllowed → groupByDay: các ngày + viên giờ để chọn (radio timeSlotId)
    private List<PtHandleDay> days = new ArrayList<>();

    // ← số khung dời được → "N khung" trên nút chọn, "N khung trống tới …"
    private int slotCount;

    // ← max(hôm nay, ngày bắt đầu gói) → min của ô ngày "Giờ khác" (PT tự chọn giờ, hệ thống tạo khung mới)
    private LocalDate from;

    // ← min(hôm nay + 13 ngày, hạn gói) → "tới dd/MM" · max của ô ngày "Giờ khác"
    private LocalDate until;


    public List<PtHandleDay> getDays() { return days; }
    public void setDays(List<PtHandleDay> days) { this.days = days; }

    public int getSlotCount() { return slotCount; }
    public void setSlotCount(int slotCount) { this.slotCount = slotCount; }

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getUntil() { return until; }
    public void setUntil(LocalDate until) { this.until = until; }
}
