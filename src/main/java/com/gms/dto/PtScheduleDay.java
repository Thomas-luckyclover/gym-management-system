package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * MỘT NGÀY (một cột) trên Lịch dạy PT.
 *
 *   Ai tạo?   PtScheduleService.buildDay tạo đủ 7 ngày, kể cả ngày không có khung nào.
 *   Ai đọc?   schedule.jsp in tiêu đề cột và các thẻ trong items.
 *
 * Ví dụ Thứ 3 27/10, mở 05:30–22:00, khung 90 phút:
 *   dayName="Thứ 3", firstStartMinute=330 (05:30), lastStartMinute=1170 (19:30).
 */
public class PtScheduleDay {

    // ===== Ngày =====
    private LocalDate date;          // ngày của cột
    private String dayName;          // "Thứ 2" ... "Chủ nhật"
    private boolean today;           // là hôm nay → tô nổi bật cột
    private boolean past;            // trước hôm nay → chỉ xem

    // ===== Giờ mở cửa (P-19 hoặc giờ ngoại lệ của riêng ngày này) =====
    private boolean closedAllDay;    // phòng nghỉ cả ngày → openTime/closeTime = null
    private LocalTime openTime;      // giờ mở cửa
    private LocalTime closeTime;     // giờ đóng cửa
    private String leaveLabel;       // nhãn nghỉ phép, ví dụ "Nghỉ phép ca sáng · chờ duyệt"; không nghỉ → ""

    // ===== Khoảng giờ được THÊM khung mới (phút trong ngày). null = ngày này không thêm được =====
    private Integer firstStartMinute; // giờ bắt đầu sớm nhất, đã làm tròn mốc 15 phút
    private Integer lastStartMinute;  // giờ bắt đầu muộn nhất để khung vẫn kết thúc trước giờ đóng cửa

    // ===== Các thẻ trong ngày =====
    private List<PtScheduleItem> items = List.of(); // khung trống + buổi có khách, đã xếp theo giờ


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

    public Integer getFirstStartMinute() { return firstStartMinute; }
    public void setFirstStartMinute(Integer firstStartMinute) { this.firstStartMinute = firstStartMinute; }

    public Integer getLastStartMinute() { return lastStartMinute; }
    public void setLastStartMinute(Integer lastStartMinute) { this.lastStartMinute = lastStartMinute; }

    public List<PtScheduleItem> getItems() { return items; }
    public void setItems(List<PtScheduleItem> items) { this.items = items; }
}
