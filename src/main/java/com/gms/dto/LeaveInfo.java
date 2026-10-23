package com.gms.dto;

import java.time.LocalDate;

/**
 * Dữ liệu một đơn nghỉ dùng khi dựng Lịch dạy PT, không phải DTO của form xin nghỉ.
 * PtScheduleDAO sẽ đọc từ LeaveRequests các đơn Pending hoặc Approved của PT trong tuần.
 * PtScheduleService dùng ngày/ca/trạng thái để tạm khóa khung trống và tạo PtScheduleDay.leaveLabel.
 * DTO chỉ giữ giá trị DB; không tự khóa khung, duyệt đơn hoặc đổi trạng thái TimeSlots.
 */
public class LeaveInfo {

    // ---------------- DAO đọc từ LeaveRequests — Service sẽ đối chiếu với từng khung giờ. ----------------
    private LocalDate leaveDate; // LeaveRequests.LeaveDate → ngày nghỉ, dùng tìm các khung trong cùng ngày.
    private String shiftType; // LeaveRequests.ShiftType → Morning | Afternoon | FullDay; Service xác định khoảng nghỉ.
    private String status; // LeaveRequests.Status → Pending | Approved theo truy vấn lịch; Service chọn nhãn chờ duyệt/đã duyệt.

    // ---------------- Getter / Setter: chỉ đọc hoặc gán, không xử lý nghiệp vụ ----------------

    /** Đọc leaveDate: LeaveRequests.LeaveDate → ngày nghỉ, dùng tìm các khung trong cùng ngày. */
    public LocalDate getLeaveDate() {
        return leaveDate;
    }

    /** Gán leaveDate đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setLeaveDate(LocalDate leaveDate) {
        this.leaveDate = leaveDate;
    }

    /** Đọc shiftType: LeaveRequests.ShiftType → Morning | Afternoon | FullDay; Service xác định khoảng nghỉ. */
    public String getShiftType() {
        return shiftType;
    }

    /** Gán shiftType đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setShiftType(String shiftType) {
        this.shiftType = shiftType;
    }

    /** Đọc status: LeaveRequests.Status → Pending | Approved theo truy vấn lịch; Service chọn nhãn chờ duyệt/đã duyệt. */
    public String getStatus() {
        return status;
    }

    /** Gán status đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setStatus(String status) {
        this.status = status;
    }
}
