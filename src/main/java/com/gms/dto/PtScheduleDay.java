package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Một ngày trong PtScheduleWeek.days của màn Lịch dạy PT.
 * PtScheduleService sẽ tạo ngày, gán giờ mở cửa, nhãn nghỉ phép và gom các PtScheduleItem cùng ngày.
 * JSP đọc ${day.date}, ${day.today}, ${day.items}; getter boolean dùng isXxx, không in true/false.
 * DTO chỉ giữ dữ liệu đã được Service chuẩn bị, không tự lọc buổi hoặc tính trạng thái.
 */
public class PtScheduleDay {

    // ---------------- Ngày và cách hiển thị — Service tính theo ngày đang xem và hôm nay. ----------------
    private LocalDate date; // Service: weekStart + chỉ số 0..6 → ngày của cột lịch, không phụ thuộc có buổi hay không.
    private String dayName; // Service: từ thứ của date → nhãn ngắn T2, T3, …, CN ở đầu cột.
    private boolean today; // Service: date trùng ngày hôm nay theo giờ Việt Nam → tô nổi bật cột hôm nay.
    private boolean past; // Service: date trước hôm nay → đánh dấu ngày đã qua trên lịch.

    // ---------------- Giờ mở cửa và nghỉ phép — Service tổng hợp cấu hình cùng dữ liệu DAO đọc. ----------------
    private boolean closedAllDay; // Service: từ HourException → đánh dấu ngày phòng tập đóng cửa cả ngày.
    private LocalTime openTime; // Service: P-19 hoặc giờ ngoại lệ của ngày → giới hạn giờ được tạo khung; có thể null khi đóng cửa cả ngày.
    private LocalTime closeTime; // Service: P-19 hoặc giờ ngoại lệ của ngày → giờ đóng cửa; có thể null khi đóng cửa cả ngày.
    private String leaveLabel; // Service: từ LeaveInfo → nhãn nghỉ phép, ví dụ Nghỉ phép ca sáng · chờ duyệt; không nghỉ thì để trống.

    // ---------------- Các khung trong ngày — Service gom theo PtScheduleItem.slotDate. ----------------
    private List<PtScheduleItem> items = List.of(); // Service: các khung có slotDate trùng date → JSP lặp khung trống, có khách, đang giữ hoặc tạm khóa.

    // ---------------- Getter / Setter: chỉ đọc hoặc gán, không xử lý nghiệp vụ ----------------

    /** Đọc date: Service: weekStart + chỉ số 0..6 → ngày của cột lịch, không phụ thuộc có buổi hay không. */
    public LocalDate getDate() {
        return date;
    }

    /** Gán date đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setDate(LocalDate date) {
        this.date = date;
    }

    /** Đọc dayName: Service: từ thứ của date → nhãn ngắn T2, T3, …, CN ở đầu cột. */
    public String getDayName() {
        return dayName;
    }

    /** Gán dayName đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setDayName(String dayName) {
        this.dayName = dayName;
    }

    /** Đọc today: Service: date trùng ngày hôm nay theo giờ Việt Nam → tô nổi bật cột hôm nay. */
    public boolean isToday() {
        return today;
    }

    /** Gán today đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setToday(boolean today) {
        this.today = today;
    }

    /** Đọc past: Service: date trước hôm nay → đánh dấu ngày đã qua trên lịch. */
    public boolean isPast() {
        return past;
    }

    /** Gán past đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setPast(boolean past) {
        this.past = past;
    }

    /** Đọc closedAllDay: Service: từ HourException → đánh dấu ngày phòng tập đóng cửa cả ngày. */
    public boolean isClosedAllDay() {
        return closedAllDay;
    }

    /** Gán closedAllDay đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setClosedAllDay(boolean closedAllDay) {
        this.closedAllDay = closedAllDay;
    }

    /** Đọc openTime: Service: P-19 hoặc giờ ngoại lệ của ngày → giới hạn giờ được tạo khung; có thể null khi đóng cửa cả ngày. */
    public LocalTime getOpenTime() {
        return openTime;
    }

    /** Gán openTime đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    /** Đọc closeTime: Service: P-19 hoặc giờ ngoại lệ của ngày → giờ đóng cửa; có thể null khi đóng cửa cả ngày. */
    public LocalTime getCloseTime() {
        return closeTime;
    }

    /** Gán closeTime đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }

    /** Đọc leaveLabel: Service: từ LeaveInfo → nhãn nghỉ phép, ví dụ Nghỉ phép ca sáng · chờ duyệt; không nghỉ thì để trống. */
    public String getLeaveLabel() {
        return leaveLabel;
    }

    /** Gán leaveLabel đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setLeaveLabel(String leaveLabel) {
        this.leaveLabel = leaveLabel;
    }

    /** Đọc items: Service: các khung có slotDate trùng date → JSP lặp khung trống, có khách, đang giữ hoặc tạm khóa. */
    public List<PtScheduleItem> getItems() {
        return items;
    }

    /** Gán items đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setItems(List<PtScheduleItem> items) {
        this.items = items;
    }
}
