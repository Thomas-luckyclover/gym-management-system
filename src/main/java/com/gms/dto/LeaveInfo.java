package com.gms.dto;

import java.time.LocalDate;

/**
 * MỘT ĐƠN NGHỈ PHÉP của PT, chỉ dùng để dựng Lịch dạy (không phải form xin nghỉ).
 *
 *   Ai tạo?   PtScheduleDAO.findLeaves (chỉ lấy đơn Pending / Approved).
 *   Ai dùng?  PtScheduleService: khóa tạm khung trống trùng ca nghỉ và ghi nhãn nghỉ dưới tên ngày.
 *
 * Ví dụ: leaveDate=28/10, shiftType="Morning", status="Pending" → khóa các khung bắt đầu trước 12:00.
 */
public class LeaveInfo {

    private LocalDate leaveDate;     // ngày nghỉ
    private String shiftType;        // Morning | Afternoon | FullDay
    private String status;           // Pending | Approved
    private String reason;


    // ===== Getter / Setter: chỉ đọc và gán, không có nghiệp vụ =====

    public LocalDate getLeaveDate() { return leaveDate; }
    public void setLeaveDate(LocalDate leaveDate) { this.leaveDate = leaveDate; }

    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
