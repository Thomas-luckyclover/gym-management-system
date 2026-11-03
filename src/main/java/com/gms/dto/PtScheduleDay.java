package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;

/**
 * MỘT NGÀY (một cột) trên Lịch dạy PT.
 *
 *   Ai tạo?   PtScheduleService.buildDay tạo đủ 7 ngày, kể cả ngày không có khung nào.
 *   Ai đọc?   schedule.jsp in tiêu đề cột và các thẻ trong items.
 *
 * Ví dụ Thứ 3 27/10, mở 05:30–22:00, khung 90 phút:
 *   dayName="Thứ 3", firstStartMinute=330 (05:30), lastStartMinute=1170 (19:30).
 *   "· IN" cuối mỗi field = JSP Lịch dạy có in field này; "· không in" = chỉ dùng để tính / dựng link.
 */
public class PtScheduleDay {

    // ===== Ngày =====
    private LocalDate date;          // ngày của cột   · IN
    private String dayName;          // "Thứ 2" ... "Chủ nhật"   · IN
    private boolean today;           // là hôm nay → tô nổi bật cột   · IN
    private boolean past;            // trước hôm nay → chỉ xem   · IN

    // ===== Giờ mở cửa (P-19 hoặc giờ ngoại lệ của riêng ngày này) =====
    private boolean closedAllDay;    // phòng nghỉ cả ngày → openTime/closeTime = null   · IN
    private LocalTime openTime;      // giờ mở cửa   · không in
    private LocalTime closeTime;     // giờ đóng cửa   · không in
    private String leaveLabel;       // nhãn nghỉ phép, ví dụ "Nghỉ phép ca sáng · chờ duyệt"; không nghỉ → ""   · IN
    private String leaveDetails;   // · IN
    private List<String> leaveBookings = new ArrayList<>(); // buổi đã có khách trùng ca nghỉ, không tự hủy   · IN

    // ===== Khoảng giờ được THÊM khung mới (phút trong ngày). null = ngày này không thêm được =====
    private Integer firstStartMinute; // giờ bắt đầu sớm nhất, đã làm tròn mốc 15 phút   · IN
    private Integer lastStartMinute;  // giờ bắt đầu muộn nhất để khung vẫn kết thúc trước giờ đóng cửa   · IN

    // ===== Các thẻ trong ngày =====
    private List<PtScheduleItem> items = List.of(); // khung trống + buổi có khách, đã xếp theo giờ   · IN


    // ===== Getter / Setter: chỉ đọc và gán, không có nghiệp vụ =====

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getDayName() { return dayName; }
    public void setDayName(String dayName) { this.dayName = dayName; }

    public boolean isToday() { return today; }
    public void setToday(boolean today) { this.today = today; }

    public boolean isPast() { return past; }
    public void setPast(boolean past) { this.past = past; }

    public boolean isClosedAllDay() { return closedAllDay; }
    public void setClosedAllDay(boolean closedAllDay) { this.closedAllDay = closedAllDay; }

    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }

    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }

    public String getLeaveLabel() { return leaveLabel; }
    public void setLeaveLabel(String leaveLabel) { this.leaveLabel = leaveLabel; }

    public String getLeaveDetails() { return leaveDetails; }
    public void setLeaveDetails(String leaveDetails) { this.leaveDetails = leaveDetails; }
    public List<String> getLeaveBookings() { return leaveBookings; }

    public Integer getFirstStartMinute() { return firstStartMinute; }
    public void setFirstStartMinute(Integer firstStartMinute) { this.firstStartMinute = firstStartMinute; }

    public Integer getLastStartMinute() { return lastStartMinute; }
    public void setLastStartMinute(Integer lastStartMinute) { this.lastStartMinute = lastStartMinute; }

    public List<PtScheduleItem> getItems() { return items; }
    public void setItems(List<PtScheduleItem> items) { this.items = items; }
}
