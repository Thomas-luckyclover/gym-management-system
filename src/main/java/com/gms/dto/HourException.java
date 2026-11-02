package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * GIỜ MỞ CỬA NGOẠI LỆ của một ngày (bảng OperatingHourExceptions).
 *
 *   Ai tạo?   PtScheduleDAO.findHourExceptions → readHourException → CONSTRUCTOR.
 *   Ai dùng?  PtScheduleService.buildDay: ngày có ngoại lệ thì dùng giờ này thay cho P-19.
 *
 * Ví dụ: 01/11 phòng nghỉ → closedAllDay=true, openTime=null, closeTime=null.
 */
public class HourException {

    /** DAO tạo (PtScheduleDAO.readHourException): 1 dòng OperatingHourExceptions. */
    public HourException(LocalDate applyDate, boolean closedAllDay, LocalTime openTime, LocalTime closeTime) {
        this.applyDate = applyDate;
        this.closedAllDay = closedAllDay;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    /** Constructor rỗng: Service / test tạo object nhỏ rồi set từng field cần. */
    public HourException() {
    }

    private LocalDate applyDate;     // ngày áp dụng   · không in (JSP không đọc object này, chỉ Service dùng)
    private boolean closedAllDay;    // true = phòng nghỉ cả ngày   · không in (JSP không đọc object này, chỉ Service dùng)
    private LocalTime openTime;      // giờ mở cửa riêng ngày này; null khi nghỉ cả ngày   · không in (JSP không đọc object này, chỉ Service dùng)
    private LocalTime closeTime;     // giờ đóng cửa riêng ngày này; null khi nghỉ cả ngày   · không in (JSP không đọc object này, chỉ Service dùng)


    // ===== Getter / Setter: chỉ đọc và gán, không có nghiệp vụ =====

    public LocalDate getApplyDate() { return applyDate; }
    public void setApplyDate(LocalDate applyDate) { this.applyDate = applyDate; }

    public boolean isClosedAllDay() { return closedAllDay; }
    public void setClosedAllDay(boolean closedAllDay) { this.closedAllDay = closedAllDay; }

    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }

    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
}
