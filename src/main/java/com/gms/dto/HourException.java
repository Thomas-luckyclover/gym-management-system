package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO 1 NGÀY GIỜ MỞ CỬA ĐẶC BIỆT — DAO TẠO: PtScheduleDAO.findHourExceptions (bảng OperatingHourExceptions).
 * Không in thẳng: PtScheduleService.buildDay dùng để đổi giờ mở / đóng của ngày hoặc đánh dấu "Phòng nghỉ";
 * PtSaleService dùng để cảnh báo "Phòng nghỉ ngày này — PT cần dời buổi".
 */
public class HourException {
    public HourException(LocalDate applyDate, boolean closedAllDay, LocalTime openTime, LocalTime closeTime) {
        this.applyDate = applyDate;
        this.closedAllDay = closedAllDay;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public HourException() {
    }

    // ===== DÙNG ĐỂ TÍNH =====
    // ← ngày áp dụng
    private LocalDate applyDate;
    // ← IsClosedAllDay = 1 → nghỉ cả ngày
    private boolean closedAllDay;
    // ← giờ mở cửa riêng ngày đó (null khi nghỉ cả ngày)
    private LocalTime openTime;
    // ← giờ đóng cửa riêng ngày đó
    private LocalTime closeTime;

    public LocalDate getApplyDate() { return applyDate; }
    public void setApplyDate(LocalDate applyDate) { this.applyDate = applyDate; }

    public boolean isClosedAllDay() { return closedAllDay; }
    public void setClosedAllDay(boolean closedAllDay) { this.closedAllDay = closedAllDay; }

    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }

    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
}
