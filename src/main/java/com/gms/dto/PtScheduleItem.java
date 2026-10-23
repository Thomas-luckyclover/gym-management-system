package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một khối giờ trong ngày của màn Lịch dạy PT: khung trống hoặc buổi có khách.
 * PtScheduleDAO sẽ tạo dữ liệu ngày/giờ, buổi, khách và gói từ SQL; PtScheduleService bổ sung phần hiển thị.
 * Khung chưa có buổi: sessionId và sessionStatus có thể null; không có dữ liệu khách/gói tương ứng.
 * JSP dùng ${item.kind}, ${item.past}, ${item.canRemove}; DTO không tự kiểm tra quyền hoặc sửa DB.
 */
public class PtScheduleItem {

    // ---------------- Phần 1: thông tin khung — DAO đọc từ TimeSlots. ----------------
    private int timeSlotId; // TimeSlots.TimeSlotId → mã khung cho thao tác gỡ; không phải mã buổi Sessions.
    private LocalDate slotDate; // TimeSlots.SlotDate → ngày tập; Service dùng để gom khung vào đúng PtScheduleDay.
    private LocalTime startTime; // TimeSlots.StartTime → giờ bắt đầu, JSP định dạng HH:mm.
    private LocalTime endTime; // TimeSlots.EndTime → giờ kết thúc, Service dùng để xác định khung đã qua.

    // ---------------- Phần 2: buổi và khách — DAO đọc qua JOIN, chỉ có khi khung gắn với buổi. ----------------
    private Integer sessionId; // Sessions.SessionId → mở chi tiết/xử lý buổi; null khi khung chưa có buổi.
    private String sessionStatus; // Sessions.Status → Scheduled | CheckedIn | Missed | Cancelled; null khi chưa có buổi.
    private String customerNames; // DAO: JOIN Persons của người đứng tên và người đi kèm → tên khách, gói đôi hiện A & B.
    private String customerPhone; // DAO: Persons.Phone của người đứng tên gói → SĐT dùng cho thao tác Nhắn Zalo.
    private String packageName; // Orders.PackageNameSnapshot → tên gói lúc mua, không lấy tên hiện tại trong Packages.
    private int sessionNo; // SQL: ROW_NUMBER theo gói trước khi lọc tuần/PT → số thứ tự buổi, ví dụ 20 trong 20/45.
    private int totalSessions; // Subscriptions.GeneratedSessionCount → tổng buổi của gói, ví dụ 45 trong 20/45.

    // ---------------- Phần 3: Service tính để hiển thị — không phải các cột riêng trong DB. ----------------
    private String kind; // Service: trạng thái khung/buổi và đơn nghỉ → free | held | booked | locked; JSP chọn kiểu khối.
    private boolean past; // Service: thời điểm hiện tại đã tới/vượt slotDate + endTime → làm mờ và không cho gỡ.
    private String statusLabel; // Service: trạng thái buổi và thời gian → nhãn Sắp tới, Hôm nay, Đã check-in, Vắng…
    private int startMinute; // Service: số phút từ giờ đầu trục đến startTime → vị trí top trên lưới; không phải phút trong giờ.
    private int lengthMinutes; // Service: số phút từ startTime đến endTime → chiều cao khối trên lưới.
    private boolean canRemove; // Service: khung free và chưa qua giờ, kèm điều kiện nghiệp vụ → JSP hiện nút Gỡ.
    private boolean canHandle; // Service: buổi Scheduled chưa bắt đầu, kèm điều kiện nghiệp vụ → JSP hiện nút Xử lý buổi.

    // ---------------- Getter / Setter: chỉ đọc hoặc gán, không xử lý nghiệp vụ ----------------

    /** Đọc timeSlotId: TimeSlots.TimeSlotId → mã khung cho thao tác gỡ; không phải mã buổi Sessions. */
    public int getTimeSlotId() {
        return timeSlotId;
    }

    /** Gán timeSlotId đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setTimeSlotId(int timeSlotId) {
        this.timeSlotId = timeSlotId;
    }

    /** Đọc slotDate: TimeSlots.SlotDate → ngày tập; Service dùng để gom khung vào đúng PtScheduleDay. */
    public LocalDate getSlotDate() {
        return slotDate;
    }

    /** Gán slotDate đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    /** Đọc startTime: TimeSlots.StartTime → giờ bắt đầu, JSP định dạng HH:mm. */
    public LocalTime getStartTime() {
        return startTime;
    }

    /** Gán startTime đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    /** Đọc endTime: TimeSlots.EndTime → giờ kết thúc, Service dùng để xác định khung đã qua. */
    public LocalTime getEndTime() {
        return endTime;
    }

    /** Gán endTime đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    /** Đọc sessionId: Sessions.SessionId → mở chi tiết/xử lý buổi; null khi khung chưa có buổi. */
    public Integer getSessionId() {
        return sessionId;
    }

    /** Gán sessionId đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
    }

    /** Đọc sessionStatus: Sessions.Status → Scheduled | CheckedIn | Missed | Cancelled; null khi chưa có buổi. */
    public String getSessionStatus() {
        return sessionStatus;
    }

    /** Gán sessionStatus đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    /** Đọc customerNames: DAO: JOIN Persons của người đứng tên và người đi kèm → tên khách, gói đôi hiện A & B. */
    public String getCustomerNames() {
        return customerNames;
    }

    /** Gán customerNames đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setCustomerNames(String customerNames) {
        this.customerNames = customerNames;
    }

    /** Đọc customerPhone: DAO: Persons.Phone của người đứng tên gói → SĐT dùng cho thao tác Nhắn Zalo. */
    public String getCustomerPhone() {
        return customerPhone;
    }

    /** Gán customerPhone đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    /** Đọc packageName: Orders.PackageNameSnapshot → tên gói lúc mua, không lấy tên hiện tại trong Packages. */
    public String getPackageName() {
        return packageName;
    }

    /** Gán packageName đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    /** Đọc sessionNo: SQL: ROW_NUMBER theo gói trước khi lọc tuần/PT → số thứ tự buổi, ví dụ 20 trong 20/45. */
    public int getSessionNo() {
        return sessionNo;
    }

    /** Gán sessionNo đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setSessionNo(int sessionNo) {
        this.sessionNo = sessionNo;
    }

    /** Đọc totalSessions: Subscriptions.GeneratedSessionCount → tổng buổi của gói, ví dụ 45 trong 20/45. */
    public int getTotalSessions() {
        return totalSessions;
    }

    /** Gán totalSessions đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    /** Đọc kind: Service: trạng thái khung/buổi và đơn nghỉ → free | held | booked | locked; JSP chọn kiểu khối. */
    public String getKind() {
        return kind;
    }

    /** Gán kind đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setKind(String kind) {
        this.kind = kind;
    }

    /** Đọc past: Service: thời điểm hiện tại đã tới/vượt slotDate + endTime → làm mờ và không cho gỡ. */
    public boolean isPast() {
        return past;
    }

    /** Gán past đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setPast(boolean past) {
        this.past = past;
    }

    /** Đọc statusLabel: Service: trạng thái buổi và thời gian → nhãn Sắp tới, Hôm nay, Đã check-in, Vắng… */
    public String getStatusLabel() {
        return statusLabel;
    }

    /** Gán statusLabel đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    /** Đọc startMinute: Service: số phút từ giờ đầu trục đến startTime → vị trí top trên lưới; không phải phút trong giờ. */
    public int getStartMinute() {
        return startMinute;
    }

    /** Gán startMinute đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setStartMinute(int startMinute) {
        this.startMinute = startMinute;
    }

    /** Đọc lengthMinutes: Service: số phút từ startTime đến endTime → chiều cao khối trên lưới. */
    public int getLengthMinutes() {
        return lengthMinutes;
    }

    /** Gán lengthMinutes đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setLengthMinutes(int lengthMinutes) {
        this.lengthMinutes = lengthMinutes;
    }

    /** Đọc canRemove: Service: khung free và chưa qua giờ, kèm điều kiện nghiệp vụ → JSP hiện nút Gỡ. */
    public boolean isCanRemove() {
        return canRemove;
    }

    /** Gán canRemove đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setCanRemove(boolean canRemove) {
        this.canRemove = canRemove;
    }

    /** Đọc canHandle: Service: buổi Scheduled chưa bắt đầu, kèm điều kiện nghiệp vụ → JSP hiện nút Xử lý buổi. */
    public boolean isCanHandle() {
        return canHandle;
    }

    /** Gán canHandle đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setCanHandle(boolean canHandle) {
        this.canHandle = canHandle;
    }

    /** JSP đọc ${item.booked}: true nếu Service đã phân loại kind là booked; null trả false. */
    public boolean isBooked() {
        return "booked".equals(kind);
    }
}
