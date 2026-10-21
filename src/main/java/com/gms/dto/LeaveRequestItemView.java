package com.gms.dto;

import com.gms.entity.LeaveRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dòng đơn nghỉ phép hiển thị trên danh sách Manager (màn #26, UC-15).
 * Service tạo từ LeaveRequestDAO.LeaveRequestRow; JSP không làm tính toán.
 */
public class LeaveRequestItemView {

    // Service kiểm tra snapshot của đơn Pending; JSP hiển thị trong cột Kiểm tra, không dùng làm quyền duyệt.
    private String checkSummary;
    private String checkMessage;

    public String getCheckSummary() { return checkSummary; }
    public void setCheckSummary(String summary) { this.checkSummary = summary; }
    public String getCheckMessage() { return checkMessage; }
    public void setCheckMessage(String message) { this.checkMessage = message; }

    // DAO: LeaveRequests.LeaveRequestId.
    private final int leaveRequestId;

    // DAO: LeaveRequests.EmployeeId.
    private final int employeeId;

    // DAO: Employees.EmployeeCode (VD NV-0005).
    private final String employeeCode;

    // DAO: Persons.FullName của nhân viên nộp đơn.
    private final String fullName;

    // DAO: Employees.Position (PT hoặc Receptionist).
    private final String position;

    // DAO: Persons.Avatar; NULL thì JSP hiện chữ viết tắt.
    private final String avatarUrl;

    // DAO: LeaveRequests.LeaveType.
    private final LeaveRequest.LeaveType leaveType;

    // DAO: LeaveRequests.LeaveDate.
    private final LocalDate leaveDate;

    // V2: FromDate, ToDate, Days
    private LocalDate fromDate;
    private LocalDate toDate;
    private java.math.BigDecimal days;

    // V2: AssignedToName, canAct
    private String assignedToName;
    private boolean canAct = true;

    // DAO: LeaveRequests.ShiftType.
    private final LeaveRequest.ShiftType shiftType;

    // DAO: LeaveRequests.Reason.
    private final String reason;

    // DAO: LeaveRequests.Status.
    private final LeaveRequest.Status status;

    // DAO: LeaveRequests.RejectReason; NULL nếu không bị từ chối.
    private final String rejectReason;

    // Service: true khi Rejected mà ReviewedByEmployeeId IS NULL (quá hạn duyệt – CBR-85).
    private final boolean autoRejected;

    // DAO: LeaveRequests.SubmittedAt.
    private final LocalDateTime submittedAt;

    // DAO: LeaveRequests.ReviewedAt; NULL nếu Pending.
    private final LocalDateTime reviewedAt;

    // DAO: Persons.FullName của người duyệt; NULL khi Pending hoặc tự từ chối.
    private final String reviewerName;

    // Service: giờ P-20 vào ngày liền trước ngày nghỉ — hạn Manager phải duyệt.
    private final LocalDateTime approvalDeadline;

    // Service: chuỗi đếm ngược dễ đọc (VD "Còn 3 giờ 20 phút"); rỗng nếu đơn đã xử lý.
    private final String deadlineCountdownText;

    // Service: true khi đơn Pending và còn dưới ngưỡng URGENT_WITHIN_MINUTES đến hạn duyệt.
    private final boolean urgent;

    // Service: true khi đơn Pending đã quá hạn duyệt P-20 (JSP khóa nút Xử lý, CBR-85).
    private final boolean overdue;

    public LeaveRequestItemView(
            int leaveRequestId,
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            String avatarUrl,
            LeaveRequest.LeaveType leaveType,
            LocalDate leaveDate,
            LeaveRequest.ShiftType shiftType,
            String reason,
            LeaveRequest.Status status,
            String rejectReason,
            boolean autoRejected,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            String reviewerName,
            LocalDateTime approvalDeadline,
            String deadlineCountdownText,
            boolean urgent,
            boolean overdue
    ) {
        this.leaveRequestId = leaveRequestId;
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.avatarUrl = avatarUrl;
        this.leaveType = leaveType;
        this.leaveDate = leaveDate;
        this.shiftType = shiftType;
        this.reason = reason;
        this.status = status;
        this.rejectReason = rejectReason;
        this.autoRejected = autoRejected;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.reviewerName = reviewerName;
        this.approvalDeadline = approvalDeadline;
        this.deadlineCountdownText = deadlineCountdownText;
        this.urgent = urgent;
        this.overdue = overdue;
    }

    public int getLeaveRequestId() {
        return leaveRequestId;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPosition() {
        return position;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public LeaveRequest.LeaveType getLeaveType() {
        return leaveType;
    }

    public LocalDate getLeaveDate() {
        return leaveDate;
    }

    public LeaveRequest.ShiftType getShiftType() {
        return shiftType;
    }

    public String getReason() {
        return reason;
    }

    public LeaveRequest.Status getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public boolean isAutoRejected() {
        return autoRejected;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public LocalDateTime getApprovalDeadline() {
        return approvalDeadline;
    }

    public String getDeadlineCountdownText() {
        return deadlineCountdownText;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public boolean isOverdue() {
        return overdue;
    }

    public LocalDate getFromDate() {
        return fromDate != null ? fromDate : leaveDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate != null ? toDate : (fromDate != null ? fromDate : leaveDate);
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public java.math.BigDecimal getDays() {
        return days;
    }

    public void setDays(java.math.BigDecimal days) {
        this.days = days;
    }

    public String getAssignedToName() {
        return assignedToName;
    }

    public void setAssignedToName(String assignedToName) {
        this.assignedToName = assignedToName;
    }

    public boolean isCanAct() {
        return canAct;
    }

    public void setCanAct(boolean canAct) {
        this.canAct = canAct;
    }
}
