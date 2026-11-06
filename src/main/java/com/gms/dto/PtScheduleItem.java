package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO 1 Ô trên lịch dạy (1 khung giờ, có thể kèm buổi) — DAO TẠO: PtScheduleDAO.findSessions (khung có buổi)
 * và findOpenSlots (khung Free / Held); Service ĐIỀN: PtScheduleService.classify (loại ô, nhãn, được gỡ / được xử lý).
 * Dùng thêm ở: PtScheduleDAO.findSlotsForPublish (kiểm trùng khi Công bố), PtSaleDAO.findPtSlots (màn Bán gói PT).
 * Hàm tính sẵn: isBooked() (ô có khách) · isBlocking() (chiếm giờ: khung trống hoặc buổi Scheduled → data-start/end cho JS chặn kéo trùng)
 * · getStartOfDay() / getEndOfDay() (phút trong ngày).
 */
public class PtScheduleItem {
    public PtScheduleItem(int timeSlotId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, String kind) {
        this.timeSlotId = timeSlotId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.kind = kind;
    }

    public PtScheduleItem(Integer sessionId, String sessionStatus, int timeSlotId, LocalDate slotDate, LocalTime startTime,
            LocalTime endTime, String packageName, int sessionNo, int totalSessions, String customerNames,
            String customerPhone) {
        this.sessionId = sessionId;
        this.sessionStatus = sessionStatus;
        this.timeSlotId = timeSlotId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.packageName = packageName;
        this.sessionNo = sessionNo;
        this.totalSessions = totalSessions;
        this.customerNames = customerNames;
        this.customerPhone = customerPhone;
    }

    public PtScheduleItem() {
    }

    // ===== IN RA MÀN — ngày giờ của ô =====
    // ← TimeSlots.TimeSlotId → value của ô tích "Gỡ khung" (removeSlotId)
    private int timeSlotId;
    // ← TimeSlots.SlotDate → ngày của ô
    private LocalDate slotDate;
    // ← TimeSlots.StartTime → "HH:mm – …", chia Ca sáng / Ca chiều
    private LocalTime startTime;
    // ← TimeSlots.EndTime → "… – HH:mm"
    private LocalTime endTime;
    // ← classify / fillWeek: phút từ đầu trục → vị trí dọc của ô (--start)
    private int startMinute;
    // ← classify: độ dài khung → chiều cao ô (--length)
    private int lengthMinutes;

    // ===== IN RA MÀN — loại ô và nhãn (classify) =====
    // ← booked (có buổi) | held (đơn tại quầy đang giữ) | locked (trùng ca nghỉ) | free → màu ô (is-<kind>)
    private String kind;
    // ← "Sắp tới" / "Đang diễn ra" / "Chưa chốt" / "Đã check-in"… / "Còn trống" / "Chờ duyệt nghỉ" → chữ trạng thái
    private String statusLabel;
    // ← khung đã kết thúc → ô mờ
    private boolean past;
    // ← khung Free chưa bắt đầu → ô có ô tích "Gỡ khung"
    private boolean canRemove;
    // ← buổi Scheduled chưa bắt đầu → ô là link "Bấm để xử lý" (?handle=<sessionId>)
    private boolean canHandle;

    // ===== IN RA MÀN — buổi có khách (DAO, chỉ khi có sessionId) =====
    // ← Sessions.SessionId → id="session-<id>" (cuộn tới sau khi xử lý), link xử lý
    private Integer sessionId;
    // ← Sessions.Status → data-status, nhãn trạng thái
    private String sessionStatus;
    // ← "Tên khách" hoặc "A & B" (gói đôi) → tên in đậm trên ô
    private String customerNames;
    // ← Orders.PackageNameSnapshot → "Tên gói · Buổi n/N"
    private String packageName;
    // ← ROW_NUMBER theo gói → "Buổi n"
    private int sessionNo;
    // ← Subscriptions.GeneratedSessionCount → "/N"
    private int totalSessions;

    // ===== KHÔNG IN RA MÀN =====
    // ← SĐT khách (dùng ở màn khác)
    private String customerPhone;

    // GETTER TỰ TÍNH — không có field/setter "startOfDay"; = startTime đổi ra số phút từ 0 giờ; JSP: pt/_schedule-day.jspf:71
    public int getStartOfDay() {
        return startTime.toSecondOfDay() / 60;
    }

    // GETTER TỰ TÍNH — không có field/setter "endOfDay"; = endTime đổi ra số phút từ 0 giờ; JSP: pt/_schedule-day.jspf:71
    public int getEndOfDay() {
        return endTime.toSecondOfDay() / 60;
    }

    // GETTER TỰ TÍNH — không có field/setter "booked"; = kind == "booked"; JSP: pt/_schedule-day.jspf:67 …
    public boolean isBooked() {
        return "booked".equals(kind);
    }

    // GETTER TỰ TÍNH — không có field/setter "blocking"; = sessionId null hoặc sessionStatus == "Scheduled"; JSP: pt/_schedule-day.jspf:71
    public boolean isBlocking() {
        return sessionId == null || "Scheduled".equals(sessionStatus);
    }

    public int getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(int timeSlotId) { this.timeSlotId = timeSlotId; }

    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer sessionId) { this.sessionId = sessionId; }

    public String getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; }

    public String getCustomerNames() { return customerNames; }
    public void setCustomerNames(String customerNames) { this.customerNames = customerNames; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public int getSessionNo() { return sessionNo; }
    public void setSessionNo(int sessionNo) { this.sessionNo = sessionNo; }

    public int getTotalSessions() { return totalSessions; }
    public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public boolean isPast() { return past; }
    public void setPast(boolean past) { this.past = past; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }

    public int getLengthMinutes() { return lengthMinutes; }
    public void setLengthMinutes(int lengthMinutes) { this.lengthMinutes = lengthMinutes; }

    public boolean isCanRemove() { return canRemove; }
    public void setCanRemove(boolean canRemove) { this.canRemove = canRemove; }

    public boolean isCanHandle() { return canHandle; }
    public void setCanHandle(boolean canHandle) { this.canHandle = canHandle; }
}
