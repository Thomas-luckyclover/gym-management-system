package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;

/**
 * DTO 1 NGÀY (1 cột) của màn Lịch dạy — Service TẠO: PtScheduleService.buildEmptyWeek / buildDay; fillWeek thêm khung.
 * JSP đọc: _schedule-day.jspf (biến day), _schedule-toolbar.jspf (danh sách ngày thêm được khung).
 */
public class PtScheduleDay {
    // ===== IN RA MÀN =====
    // ← thứ Hai + i → "dd/MM", data-date của cột (JS thả khung nháp vào đúng ngày)
    private LocalDate date;
    // ← "Thứ 2"…"Chủ nhật" → tên cột
    private String dayName;
    // ← date = hôm nay → tô cột + nhãn "Hôm nay", ngày chọn sẵn trong "Thêm khung"
    private boolean today;
    // ← date < hôm nay → cột mờ
    private boolean past;
    // ← OperatingHourExceptions.IsClosedAllDay → nhãn "Phòng nghỉ", "Phòng nghỉ cả ngày"
    private boolean closedAllDay;
    // ← buildLeaveLabel: "Nghỉ phép ca sáng · chờ duyệt" → nút mở chi tiết đơn nghỉ
    private String leaveLabel;
    // ← buildLeaveDetails: nhãn + lý do từng đơn → nội dung khi mở
    private String leaveDetails;
    // ← fillWeek: buổi có khách trùng ca nghỉ → "N buổi trùng ca nghỉ" + danh sách
    private List<String> leaveBookings = new ArrayList<>();
    // ← setAddRange: phút sớm nhất được thêm khung (null = ngày không thêm được) → data-first; quyết định nút "Thêm khung"
    private Integer firstStartMinute;
    // ← setAddRange: phút muộn nhất được bắt đầu khung → data-last
    private Integer lastStartMinute;
    // ← fillWeek: các khung / buổi (PtScheduleItem) trong ngày → các ô trên cột
    private List<PtScheduleItem> items = List.of();

    // ===== DÙNG ĐỂ TÍNH (không in thẳng) =====
    // ← P-19 hoặc giờ ngoại lệ của ngày → setAddRange, trục giờ
    private LocalTime openTime;
    // ← như trên
    private LocalTime closeTime;

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
