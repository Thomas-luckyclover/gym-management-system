package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * MỘT KHUNG TRỐNG có thể nhận buổi khi PT xử lý buổi (Dời buổi hoặc Chuyển PT).
 *
 *   Ai tạo?   PtSessionHandleDAO đọc khung Free (của chính PT khi Dời, của PT khác khi Chuyển).
 *   Ai xét?   PtSessionHandleService.whyNotAllowed đọc 4 cờ bên dưới + hạn gói + giờ hiện tại.
 *   Ai in?    _handle-dialog.jspf: mỗi khung hợp lệ là 1 nút chọn (radio).
 *
 * Ví dụ khung Thứ 5 29/10 06:00–07:30 của PT Sơn: timeSlotId=812, ptName="Đinh Công Sơn",
 * gymClosed=false, ptOnLeave=false, customerBusy=false, hasGymAccess=true → hợp lệ.
 */
public class PtHandleSlot {

    // ===== 1. Khung giờ (bảng TimeSlots) + PT của khung (Employees, Persons) =====
    private int timeSlotId;          // mã khung đích, form gửi lên khi bấm Lưu
    private int ptEmployeeId;        // PT sở hữu khung (Chuyển PT: PT nhận buổi)
    private String ptName;           // tên PT, chỉ in khi Chuyển PT
    private int commissionRate;      // % hoa hồng của PT này (Employees.CommissionRate)
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;

    // ===== 2. Bốn cờ SQL tính sẵn (1 = có) =====
    private boolean gymClosed;       // ngày đó phòng nghỉ cả ngày (OperatingHourExceptions)
    private boolean ptOnLeave;       // PT của khung có đơn nghỉ Pending/Approved trùng ca
    private boolean customerBusy;    // khách (hoặc người tập cùng) có buổi Scheduled khác chồng giờ
    private boolean hasGymAccess;    // ít nhất 1 người trong gói có Membership/DayPass ngày đó và không bảo lưu
                                     // (false KHÔNG chặn, chỉ tô vàng trong hộp)

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
