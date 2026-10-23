package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Dữ liệu giờ mở cửa ngoại lệ của một ngày trong OperatingHourExceptions.
 * PtScheduleDAO sẽ đọc các ngày ngoại lệ trong tuần; PtScheduleService áp dụng thay giờ P-19 của ngày đó.
 * Service chuyển kết quả sang PtScheduleDay; DTO này không tự tính giờ hoặc kiểm tra khung hợp lệ.
 */
public class HourException {

    // ---------------- DAO đọc từ OperatingHourExceptions — không có bản ghi thì Service dùng giờ P-19. ----------------
    private LocalDate applyDate; // OperatingHourExceptions.ApplyDate → ngày áp dụng giờ ngoại lệ.
    private boolean closedAllDay; // OperatingHourExceptions.IsClosedAllDay → true nghĩa là phòng nghỉ cả ngày.
    private LocalTime openTime; // OperatingHourExceptions.OpenTime → giờ mở cửa ngoại lệ; null nếu closedAllDay=true.
    private LocalTime closeTime; // OperatingHourExceptions.CloseTime → giờ đóng cửa ngoại lệ; null nếu closedAllDay=true.

    // ---------------- Getter / Setter: chỉ đọc hoặc gán, không xử lý nghiệp vụ ----------------

    /** Đọc applyDate: OperatingHourExceptions.ApplyDate → ngày áp dụng giờ ngoại lệ. */
    public LocalDate getApplyDate() {
        return applyDate;
    }

    /** Gán applyDate đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setApplyDate(LocalDate applyDate) {
        this.applyDate = applyDate;
    }

    /** Đọc closedAllDay: OperatingHourExceptions.IsClosedAllDay → true nghĩa là phòng nghỉ cả ngày. */
    public boolean isClosedAllDay() {
        return closedAllDay;
    }

    /** Gán closedAllDay đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setClosedAllDay(boolean closedAllDay) {
        this.closedAllDay = closedAllDay;
    }

    /** Đọc openTime: OperatingHourExceptions.OpenTime → giờ mở cửa ngoại lệ; null nếu closedAllDay=true. */
    public LocalTime getOpenTime() {
        return openTime;
    }

    /** Gán openTime đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    /** Đọc closeTime: OperatingHourExceptions.CloseTime → giờ đóng cửa ngoại lệ; null nếu closedAllDay=true. */
    public LocalTime getCloseTime() {
        return closeTime;
    }

    /** Gán closeTime đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }
}
