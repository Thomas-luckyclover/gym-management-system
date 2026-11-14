package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;

/**
 * DTO 1 NGÀY (1 cột) của màn Lịch dạy — Service TẠO: PtScheduleService.buildEmptyWeek / buildDay; fillWeek thêm khung.
 * JSP đọc: _schedule-day.jspf (biến day), _schedule-toolbar.jspf (danh sách ngày thêm được khung).
 *
 * NGUỒN DỮ LIỆU CỦA 1 CỘT (4 nguồn DB, còn lại tính từ URL ?date= và đồng hồ hệ thống):
 *   OperatingHourExceptions  → closedAllDay, openTime / closeTime (ngày có giờ riêng)
 *   SystemConfigs            → P-17 giờ mở cửa mặc định (openTime / closeTime), P-06 thời lượng buổi (firstStart / lastStart)
 *   LeaveRequests            → leaveLabel, leaveDetails, leaveBookings (chỉ đơn Pending / Approved)
 *   TimeSlots + Sessions     → items, leaveBookings (+ Subscriptions, Orders, Customers, Persons lấy tên khách)
 */
public class PtScheduleDay {
    // ===== IN RA MÀN =====

    // ← weekStart + i (i = 0…6) — KHÔNG đọc DB (từ URL ?date= → thứ Hai)
    //   → "dd/MM", data-date của cột (JS thả khung nháp vào đúng ngày)
    private LocalDate date;

    // ← tính từ date: "Thứ 2"…"Chủ nhật" (PtScheduleService.dayName) — KHÔNG đọc DB
    //   → tên cột
    private String dayName;

    // ← date = hôm nay (đồng hồ hệ thống DateTimeUtil.now) — KHÔNG đọc DB
    //   → tô cột + nhãn "Hôm nay", ngày chọn sẵn trong "Thêm khung"
    private boolean today;

    // ← date < hôm nay (đồng hồ hệ thống) — KHÔNG đọc DB
    //   → cột mờ, không thêm khung được
    private boolean past;

    // ← BẢNG OperatingHourExceptions.IsClosedAllDay (dòng có ApplyDate = date), không có dòng → false
    //   DAO: PtScheduleDAO.findHourExceptions · Service: buildDay
    //   → nhãn "Phòng nghỉ", "Phòng nghỉ cả ngày"; không thêm khung được
    private boolean closedAllDay;

    // ← BẢNG LeaveRequests.ShiftType (Morning/Afternoon/FullDay → ca sáng/ca chiều/cả ngày)
    //   + LeaveRequests.Status (Pending → "chờ duyệt", Approved → "đã duyệt"; Rejected bị loại ngay trong SQL)
    //   lọc: EmployeeId = PT, date nằm trong FromDate…ToDate (DAO tách đơn nhiều ngày thành từng ngày)
    //   DAO: PtScheduleDAO.findLeaves · Service: buildLeaveLabel
    //   → "Nghỉ phép ca sáng · chờ duyệt" ở đầu cột, nút mở chi tiết đơn nghỉ
    private String leaveLabel;

    // ← BẢNG LeaveRequests: nhãn như leaveLabel + LeaveRequests.Reason ("Lý do: …")
    //   Service: buildLeaveDetails
    //   → nội dung khi mở chi tiết nghỉ phép
    private String leaveDetails;

    // ← BẢNG Sessions.Status = 'Scheduled' + TimeSlots.StartTime/EndTime trùng ca nghỉ trong LeaveRequests
    //   + tên khách (Persons.FullName qua Subscriptions → Customers, gói đôi qua Orders.PartnerCustomerId)
    //   Service: fillWeek (isOnLeave) — đơn nghỉ không ẩn buổi đã đặt, chỉ nhắc PT dời (CBR-83)
    //   → "N buổi trùng ca nghỉ" + danh sách "giờ · tên khách · trạng thái"
    private List<String> leaveBookings = new ArrayList<>();

    // ← tính từ openTime (làm tròn LÊN mốc 15'), hôm nay thì lấy sau giờ hiện tại — gián tiếp từ
    //   OperatingHourExceptions / SystemConfigs P-17 · Service: setAddRange (BR-100)
    //   null = ngày không thêm được khung (đã qua / phòng nghỉ / hôm nay đã quá muộn)
    //   → data-first; quyết định nút "Thêm khung"
    //   VD P-17 05:30–22:00 → 330 (phút)
    private Integer firstStartMinute;

    // ← closeTime − SystemConfigs P-06 (làm tròn XUỐNG mốc 15') · Service: setAddRange (BR-100)
    //   → data-last: phút muộn nhất được bắt đầu khung
    //   VD 22:00 − 90' = 20:30 → 1230 (phút)
    private Integer lastStartMinute;

    // ← BẢNG TimeSlots (khung Free / Held: findOpenSlots)
    //   + BẢNG Sessions JOIN TimeSlots, Subscriptions, Orders, Customers, Persons (buổi: findSessions)
    //   Service: merge (bỏ ô trùng TimeSlotId) → fillWeek → classify (booked / held / locked / free)
    //   → các ô trên cột
    private List<PtScheduleItem> items = List.of();

    // ===== DÙNG ĐỂ TÍNH (không in thẳng) =====

    // ← BẢNG OperatingHourExceptions.OpenTime nếu ngày có dòng ngoại lệ,
    //   không có → BẢNG SystemConfigs ConfigKey = 'P-17' (giờ mở cửa mặc định)
    //   Service: buildDay (giờ ngoại lệ thiếu / sai thứ tự → lỗi → MSG04)
    //   → setAddRange, trục giờ của tuần
    private LocalTime openTime;

    // ← BẢNG OperatingHourExceptions.CloseTime, không có → SystemConfigs 'P-17' (như openTime)
    //   → setAddRange, trục giờ của tuần
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