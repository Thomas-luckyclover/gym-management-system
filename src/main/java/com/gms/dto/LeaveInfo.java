package com.gms.dto;

import java.time.LocalDate;

/**
 * MỘT ĐƠN NGHỈ PHÉP của PT, chỉ dùng để dựng Lịch dạy (không phải form xin nghỉ).
 *
 *   Ai tạo?   PtScheduleDAO.findLeaves → readLeave → CONSTRUCTOR (chỉ lấy đơn Pending / Approved).
 *   Ai dùng?  PtScheduleService: khóa tạm khung trống trùng ca nghỉ và ghi nhãn nghỉ dưới tên ngày.
 *
 * Ví dụ: leaveDate=28/10, shiftType="Morning", status="Pending" → khóa các khung bắt đầu trước 12:00.
 */
public class LeaveInfo {

    /** DAO tạo (PtScheduleDAO.readLeave): 1 ngày của 1 đơn nghỉ LeaveRequests. */
    public LeaveInfo(LocalDate leaveDate, String shiftType, String status, String reason) {
        this.leaveDate = leaveDate;
        this.shiftType = shiftType;
        this.status = status;
        this.reason = reason;
    }

    /** Constructor rỗng: Service / test tạo object nhỏ rồi set từng field cần. */
    public LeaveInfo() {
    }

    private LocalDate leaveDate;     // ngày nghỉ   · không in (JSP không đọc object này, chỉ Service dùng)
    private String shiftType;        // Morning | Afternoon | FullDay   · không in (JSP không đọc object này, chỉ Service dùng)
    private String status;           // Pending | Approved   · không in (JSP không đọc object này, chỉ Service dùng)
    private String reason;           // lý do nghỉ   · không in (JSP không đọc object này, chỉ Service dùng)


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
