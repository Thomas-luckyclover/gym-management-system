package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Service tạo từ ngày/giờ nhập và P-06; DAO dùng để công bố, chưa có TimeSlotId. */
public class PtSlotDraft {
    private final LocalDate slotDate; // Ngày từ form, Service kiểm tra trước khi ghi.
    private final LocalTime startTime; // Giờ từ form, phải theo mốc 15 phút.
    private final LocalTime endTime; // Service tính startTime + P-06, không lấy từ client.

    /** Nhận ba mốc đã được Service chuẩn bị; DTO chỉ giữ dữ liệu. */
    public PtSlotDraft(LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** Ngày DAO sẽ ghi vào SlotDate. */
    public LocalDate getSlotDate() { return slotDate; }
    /** Giờ bắt đầu DAO sẽ ghi vào StartTime. */
    public LocalTime getStartTime() { return startTime; }
    /** Giờ kết thúc do Service tính, DAO ghi vào EndTime. */
    public LocalTime getEndTime() { return endTime; }
}
