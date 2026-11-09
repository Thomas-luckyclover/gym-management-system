package com.gms.dto;

import java.time.LocalDate;

/**
 * DTO 1 NGÀY NGHỈ của PT — DAO TẠO: PtScheduleDAO.findLeaves (LeaveRequests Pending / Approved, tách từng ngày).
 * Không in thẳng: PtScheduleService đổi thành nhãn "Nghỉ phép ca sáng · chờ duyệt", khóa khung (locked), chặn công bố;
 * PtSaleService / PtSessionHandle dùng để cảnh báo "PT có đơn nghỉ".
 */
public class LeaveInfo {
    public LeaveInfo(LocalDate leaveDate, String shiftType, String status, String reason) {
        this.leaveDate = leaveDate;
        this.shiftType = shiftType;
        this.status = status;
        this.reason = reason;
    }

    public LeaveInfo() {
    }

    // ===== DÙNG ĐỂ TÍNH =====
    // ← 1 ngày trong [FromDate, ToDate] của đơn
    private LocalDate leaveDate;
    // ← LeaveRequests.ShiftType: Morning (trước 13:00) | Afternoon | FullDay
    private String shiftType;
    // ← Pending | Approved → "chờ duyệt" / "đã duyệt"
    private String status;
    // ← LeaveRequests.Reason → chi tiết đơn nghỉ
    private String reason;

    public LocalDate getLeaveDate() { return leaveDate; }
    public void setLeaveDate(LocalDate leaveDate) { this.leaveDate = leaveDate; }

    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
