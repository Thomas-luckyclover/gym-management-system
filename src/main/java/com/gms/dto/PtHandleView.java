package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU HỘP "XỬ LÝ BUỔI" trên Lịch dạy PT (/pt/schedule?handle=120).
 *
 *   Ai tạo?   PtSessionHandleDAO.findSession đọc phần 1 + 2 → CONSTRUCTOR (1 buổi của PT đang đăng nhập).
 *   "· IN" cuối mỗi field = _handle-dialog.jspf có in field này; "· không in" = chỉ dùng để tính / ô ẩn.
 *   Ai điền?  PtSessionHandleService.getHandleForm điền phần 3 (các lựa chọn hợp lệ).
 *   Ai in?    _handle-dialog.jspf (biến JSP "handle").
 *   Ai dùng lại?  3 hàm ghi của Service (dời / chuyển / hủy) đọc phần 1 + 2 để kiểm tra lại khi bấm Lưu.
 */
public class PtHandleView {

    /** DAO tạo (PtSessionHandleDAO.readSession): phần 1 (buổi) + phần 2 (gói, khách). Phần 3 Service set sau. */
    public PtHandleView(int sessionId, String sessionStatus, int timeSlotId, int ptEmployeeId, String ptName,
            int sessionValue, int commissionAmount, LocalDate slotDate, LocalTime startTime, LocalTime endTime,
            int subscriptionId, String subscriptionType, int customerId, Integer partnerCustomerId,
            LocalDate termStart, LocalDate termEnd, String packageName, String customerNames) {
        this.sessionId = sessionId;
        this.sessionStatus = sessionStatus;
        this.timeSlotId = timeSlotId;
        this.ptEmployeeId = ptEmployeeId;
        this.ptName = ptName;
        this.sessionValue = sessionValue;
        this.commissionAmount = commissionAmount;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subscriptionId = subscriptionId;
        this.subscriptionType = subscriptionType;
        this.customerId = customerId;
        this.partnerCustomerId = partnerCustomerId;
        this.termStart = termStart;
        this.termEnd = termEnd;
        this.packageName = packageName;
        this.customerNames = customerNames;
    }

    /** Constructor rỗng: Service / test tạo object nhỏ rồi set từng field cần. */
    public PtHandleView() {
    }

    // ===== 1. Buổi (Sessions + TimeSlots + PT dạy) =====
    private int sessionId;             // mã buổi (Sessions.SessionId), KHÁC mã khung timeSlotId   · IN
    private String sessionStatus;      // phải còn Scheduled mới xử lý được   · IN
    private int timeSlotId;            // khung hiện tại của buổi   · IN
    private LocalDate slotDate;   // · IN
    private LocalTime startTime;   // · IN
    private LocalTime endTime;   // · IN
    private int ptEmployeeId;          // PT đang dạy   · không in
    private String ptName;             // tên PT đang dạy (ghi vào thông báo)   · IN
    private int sessionValue;          // giá trị buổi (Sessions.SessionValue) — tính lại hoa hồng khi chuyển PT   · không in
    private int commissionAmount;      // hoa hồng hiện tại — giữ nguyên khi dời buổi   · không in

    // ===== 2. Gói + khách (Subscriptions + Orders + Persons) =====
    private int subscriptionId;   // · không in
    private String subscriptionType;   // PTSingle | PTCouple | SingleSession   · không in
    private int customerId;            // người đứng tên gói   · không in
    private Integer partnerCustomerId; // người tập cùng (chỉ gói đôi), không có → null   · không in
    private LocalDate termStart;       // hạn gói: buổi chỉ được dời trong khoảng này   · không in
    private LocalDate termEnd;   // · IN
    private String packageName;        // Orders.PackageNameSnapshot   · IN
    private String customerNames;      // "Đỗ Thị Mai" hoặc gói đôi "A & B"   · IN

    // ===== 3. Service điền cho hộp =====
    private String dayLabel;                                     // "Thứ 3 · 27/10"   · IN
    private List<PtHandleDay> moveDays = new ArrayList<>();      // Dời buổi: khung trống hợp lệ của PT, theo ngày   · IN
    private int moveSlotCount;                                   // tổng số khung trong moveDays   · IN
    private LocalDate moveUntil;                                 // ngày cuối đã tìm khung (hạn gói hoặc 14 ngày tới)   · IN
    private List<PtHandleSlot> transferSlots = new ArrayList<>();// Chuyển PT: PT khác trống đúng giờ này   · IN

    /** JSP viết ${handle.couple}: gói đôi thì nhắc "báo cả 2 người". */
    public boolean isCouple() {
        return partnerCustomerId != null;
    }

    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }

    public String getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; }

    public int getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(int timeSlotId) { this.timeSlotId = timeSlotId; }

    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public int getPtEmployeeId() { return ptEmployeeId; }
    public void setPtEmployeeId(int ptEmployeeId) { this.ptEmployeeId = ptEmployeeId; }

    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }

    public int getSessionValue() { return sessionValue; }
    public void setSessionValue(int sessionValue) { this.sessionValue = sessionValue; }

    public int getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(int commissionAmount) { this.commissionAmount = commissionAmount; }

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public Integer getPartnerCustomerId() { return partnerCustomerId; }
    public void setPartnerCustomerId(Integer partnerCustomerId) { this.partnerCustomerId = partnerCustomerId; }

    public LocalDate getTermStart() { return termStart; }
    public void setTermStart(LocalDate termStart) { this.termStart = termStart; }

    public LocalDate getTermEnd() { return termEnd; }
    public void setTermEnd(LocalDate termEnd) { this.termEnd = termEnd; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getCustomerNames() { return customerNames; }
    public void setCustomerNames(String customerNames) { this.customerNames = customerNames; }

    public String getDayLabel() { return dayLabel; }
    public void setDayLabel(String dayLabel) { this.dayLabel = dayLabel; }

    public List<PtHandleDay> getMoveDays() { return moveDays; }
    public void setMoveDays(List<PtHandleDay> moveDays) { this.moveDays = moveDays; }

    public int getMoveSlotCount() { return moveSlotCount; }
    public void setMoveSlotCount(int moveSlotCount) { this.moveSlotCount = moveSlotCount; }

    public LocalDate getMoveUntil() { return moveUntil; }
    public void setMoveUntil(LocalDate moveUntil) { this.moveUntil = moveUntil; }

    public List<PtHandleSlot> getTransferSlots() { return transferSlots; }
    public void setTransferSlots(List<PtHandleSlot> transferSlots) { this.transferSlots = transferSlots; }
}
