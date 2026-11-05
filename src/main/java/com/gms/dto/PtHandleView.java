package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO HỘP "XỬ LÝ BUỔI" — DAO TẠO: PtSessionHandleDAO.findSession (buổi + gói + khách + PT);
 * Service ĐIỀN: PtSessionHandleService.getHandleForm (dayLabel, khung dời được, PT nhận thay được).
 * Servlet: PtScheduleServlet.loadHandle → request "handle" → _handle-dialog.jspf.
 * Hàm tính sẵn: isCouple() → chip "Gói đôi".
 */
public class PtHandleView {
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

    public PtHandleView() {
    }

    // ===== IN RA MÀN — đầu hộp =====
    // ← Sessions.SessionId → ô ẩn sessionId của 3 form (Dời / Chuyển / Hủy)
    private int sessionId;
    // ← Service dayLabel: "Thứ 3 · 05/11" → tiêu đề hộp
    private String dayLabel;
    // ← TimeSlots.StartTime → "HH:mm – …" tiêu đề, "Trống HH:mm – …" của PT nhận thay
    private LocalTime startTime;
    // ← TimeSlots.EndTime
    private LocalTime endTime;
    // ← tên khách (gói đôi: "A & B") → chữ đậm dưới tiêu đề
    private String customerNames;
    // ← Orders.PackageNameSnapshot → "· tên gói"
    private String packageName;
    // ← Subscriptions.EndDate → chip "Hạn gói đến dd/MM/yyyy"
    private LocalDate termEnd;

    // ===== IN RA MÀN — tab Dời buổi =====
    // ← findMoveDays → groupByDay: khung trống của chính PT, đã lọc whyNotAllowed → các ngày + giờ để chọn
    private List<PtHandleDay> moveDays = new ArrayList<>();
    // ← số khung dời được → "N khung" trên nút chọn, "N khung hợp lệ tới …"
    private int moveSlotCount;
    // ← min(hôm nay + 13 ngày, hạn gói) → "tới dd/MM"
    private LocalDate moveUntil;

    // ===== IN RA MÀN — tab Chuyển PT =====
    // ← PtSessionHandleDAO.findTransferSlots (PT khác trống ĐÚNG giờ này), đã lọc → "N PT trống" + danh sách PT
    private List<PtHandleSlot> transferSlots = new ArrayList<>();

    // ===== DÙNG ĐỂ KIỂM / GHI (không in) =====
    // ← Sessions.Status → whySessionLocked (chỉ Scheduled chưa bắt đầu mới xử lý)
    private String sessionStatus;
    // ← khung hiện tại → trả về Free khi dời / hủy
    private int timeSlotId;
    // ← ngày buổi → kiểm đã bắt đầu chưa, câu thông báo
    private LocalDate slotDate;
    // ← PT đang dạy → SessionMoveLogs
    private int ptEmployeeId;
    // ← tên PT → câu thông báo cho khách
    private String ptName;
    // ← Sessions.SessionValue → tính hoa hồng PT nhận thay
    private int sessionValue;
    // ← hoa hồng hiện tại → giữ nguyên khi dời
    private int commissionAmount;
    private int subscriptionId;
    // ← PTCouple → isCouple()
    private String subscriptionType;
    // ← người đứng tên → kiểm khách trùng giờ, gửi thông báo
    private int customerId;
    // ← người tập cùng (gói đôi)
    private Integer partnerCustomerId;
    // ← Subscriptions.StartDate → khung dời phải trong hạn gói
    private LocalDate termStart;

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
