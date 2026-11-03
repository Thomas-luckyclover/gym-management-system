package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * MỘT KHỐI GIỜ trên Lịch dạy PT: hoặc là khung trống, hoặc là buổi có khách.
 *
 *   Ai tạo?   PtScheduleDAO đọc từ DB → CONSTRUCTOR (2 kiểu: buổi có khách / khung chưa có buổi), phần 1 + 2 bên dưới.
 *   "· IN" cuối mỗi field = JSP Lịch dạy có in field này; "· không in" = chỉ dùng để tính.
 *   Ai điền?  PtScheduleService.classify điền phần 3 (loại khối, nhãn, quyền, vị trí).
 *   Ai đọc?   schedule.jsp in ra thẻ khung; Service dùng lại khi Công bố.
 *
 * Ví dụ khung trống Thứ 3 09:30–11:00:
 *   timeSlotId=773, sessionId=null, kind="free", statusLabel="Còn trống", canRemove=true.
 */
public class PtScheduleItem {

    /** DAO tạo KHUNG CHƯA CÓ BUỔI (PtScheduleDAO.readSlot): phần 1 + kind = TimeSlots.Status (Free | Held). Service.classify đổi kind sau. */
    public PtScheduleItem(int timeSlotId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, String kind) {
        this.timeSlotId = timeSlotId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.kind = kind;
    }

    /** DAO tạo BUỔI CÓ KHÁCH (PtScheduleDAO.readSessionRow): phần 1 + 2. Phần 3 Service.classify set sau. */
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

    /** Constructor rỗng: Service / test tạo object nhỏ rồi set từng field cần. */
    public PtScheduleItem() {
    }

    // ===== 1. Khung giờ (bảng TimeSlots) =====
    private int timeSlotId;          // mã khung, dùng khi gỡ khung. KHÁC mã buổi sessionId   · IN
    private LocalDate slotDate;      // ngày của khung   · IN
    private LocalTime startTime;     // giờ bắt đầu, ví dụ 09:30   · IN
    private LocalTime endTime;       // giờ kết thúc, ví dụ 11:00   · IN

    // ===== 2. Buổi có khách (bảng Sessions + gói). Khung trống thì phần này để null/0 =====
    private Integer sessionId;       // mã buổi; null nếu khung chưa có khách   · IN
    private String sessionStatus;    // Scheduled | CheckedIn | Missed | Cancelled   · IN
    private String customerNames;    // tên khách; gói đôi ghép "A & B"   · IN
    private String customerPhone;    // SĐT người đứng tên gói   · IN
    private String packageName;      // tên gói lúc mua   · IN
    private int sessionNo;           // buổi thứ mấy trong gói, ví dụ 20 trong "20/45"   · IN
    private int totalSessions;       // tổng số buổi của gói, ví dụ 45   · IN

    // ===== 3. Service tính để JSP hiển thị =====
    private String kind;             // free | held | booked | locked → chọn màu thẻ   · IN
    private boolean past;            // đã hết giờ → thẻ mờ   · IN
    private String statusLabel;      // chữ trạng thái: Còn trống, Sắp tới, Vắng...   · IN
    private int startMinute;         // số phút tính từ ĐẦU TRỤC GIỜ của tuần → vị trí thẻ trên lưới   · IN
    private int lengthMinutes;       // độ dài khung (phút) → chiều cao thẻ   · IN
    private boolean canRemove;       // true → JSP in ô "Gỡ khung" cho thẻ này   · IN
    private boolean canHandle;       // true → buổi có thể xử lý (bước 11 sẽ dùng)   · IN


    // ===== Giá trị tính sẵn cho JSP (không lưu, tính từ giờ) =====

    /** Giờ bắt đầu đổi ra phút trong ngày: 09:30 → 570. JS dùng để kiểm tra trùng giờ. */
    public int getStartOfDay() {
        return startTime.toSecondOfDay() / 60;
    }

    /** Giờ kết thúc đổi ra phút trong ngày: 11:00 → 660. */
    public int getEndOfDay() {
        return endTime.toSecondOfDay() / 60;
    }

    /** JSP viết ${item.booked}: true khi thẻ là buổi có khách. */
    public boolean isBooked() {
        return "booked".equals(kind);
    }

    /**
     * JSP viết ${item.blocking}: thẻ này có CHIẾM giờ không (khung mới không được đè lên).
     * Khung trống/đang giữ/tạm khóa và buổi Scheduled thì chiếm; buổi đã hủy, vắng, đã check-in chỉ là lịch sử.
     */
    public boolean isBlocking() {
        return sessionId == null || "Scheduled".equals(sessionStatus);
    }


    // ===== Getter / Setter: chỉ đọc và gán, không có nghiệp vụ =====

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
