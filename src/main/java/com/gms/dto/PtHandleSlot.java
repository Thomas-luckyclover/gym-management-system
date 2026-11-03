package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO 1 KHUNG TRỐNG có thể nhận buổi — DAO TẠO: PtSessionHandleDAO (findMySlots / findTransferSlots / findSlotById, câu SLOT_SQL).
 * Service lọc bằng whyNotAllowed; JSP đọc: _handle-dialog.jspf (biến slot).
 */
public class PtHandleSlot {
    public PtHandleSlot(int timeSlotId, int ptEmployeeId, String ptName, int commissionRate, LocalDate slotDate,
            LocalTime startTime, LocalTime endTime, boolean gymClosed, boolean ptOnLeave, boolean customerBusy,
            boolean hasGymAccess) {
        this.timeSlotId = timeSlotId;
        this.ptEmployeeId = ptEmployeeId;
        this.ptName = ptName;
        this.commissionRate = commissionRate;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.gymClosed = gymClosed;
        this.ptOnLeave = ptOnLeave;
        this.customerBusy = customerBusy;
        this.hasGymAccess = hasGymAccess;
    }

    public PtHandleSlot() {
    }

    // ===== IN RA MÀN =====
    // ← TimeSlots.TimeSlotId → value của radio timeSlotId (gửi lên khi Dời / Chuyển)
    private int timeSlotId;
    // ← TimeSlots.StartTime → chữ giờ trên viên
    private LocalTime startTime;
    // ← tên PT của khung → tab Chuyển PT: tên + chữ cái avatar
    private String ptName;
    // ← khách có Membership / DayPass không bảo lưu ngày đó → false: viên vàng "khách chưa có gói thành viên ngày này"
    private boolean hasGymAccess;

    // ===== DÙNG ĐỂ LỌC / GHI (không in) =====
    // ← PT của khung → Dời: phải là chính PT · Chuyển: phải là PT khác
    private int ptEmployeeId;
    // ← Employees.CommissionRate của PT nhận → hoa hồng khi chuyển
    private int commissionRate;
    // ← ngày khung → kiểm hạn gói / đã qua giờ
    private LocalDate slotDate;
    private LocalTime endTime;
    // ← ngày phòng nghỉ → loại
    private boolean gymClosed;
    // ← PT có đơn nghỉ trùng ca → loại
    private boolean ptOnLeave;
    // ← khách có buổi khác trùng giờ → loại
    private boolean customerBusy;

    public int getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(int timeSlotId) { this.timeSlotId = timeSlotId; }

    public int getPtEmployeeId() { return ptEmployeeId; }
    public void setPtEmployeeId(int ptEmployeeId) { this.ptEmployeeId = ptEmployeeId; }

    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }

    public int getCommissionRate() { return commissionRate; }
    public void setCommissionRate(int commissionRate) { this.commissionRate = commissionRate; }

    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public boolean isGymClosed() { return gymClosed; }
    public void setGymClosed(boolean gymClosed) { this.gymClosed = gymClosed; }

    public boolean isPtOnLeave() { return ptOnLeave; }
    public void setPtOnLeave(boolean ptOnLeave) { this.ptOnLeave = ptOnLeave; }

    public boolean isCustomerBusy() { return customerBusy; }
    public void setCustomerBusy(boolean customerBusy) { this.customerBusy = customerBusy; }

    public boolean isHasGymAccess() { return hasGymAccess; }
    public void setHasGymAccess(boolean hasGymAccess) { this.hasGymAccess = hasGymAccess; }
}
