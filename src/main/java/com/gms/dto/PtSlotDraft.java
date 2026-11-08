package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO 1 KHUNG NHÁP PT muốn công bố — Service TẠO: PtScheduleService.buildDrafts (từ 2 mảng slotDate[] + startTime[] JS gửi lên).
 * Không in ra màn: validateDrafts kiểm, PtScheduleDAO.insertOrReopenSlot ghi vào TimeSlots.
 */
public class PtSlotDraft {
    // ===== DÙNG ĐỂ KIỂM + GHI =====
    // ← ô ẩn slotDate của khung nháp
    private final LocalDate slotDate;
    // ← ô ẩn startTime (mốc 15 phút)
    private final LocalTime startTime;
    // ← startTime + P-06 phút
    private final LocalTime endTime;

    public PtSlotDraft(LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalDate getSlotDate() { return slotDate; }

    public LocalTime getStartTime() { return startTime; }

    public LocalTime getEndTime() { return endTime; }
}
