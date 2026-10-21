package com.gms.dto;

import com.gms.entity.LeaveRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dòng đơn nghỉ phép hiển thị trên danh sách Manager (màn #26, UC-15).
 * Service tạo từ LeaveRequestRow; JSP không làm tính toán.
 */
public class LeaveRequestItemView {

    // Service kiểm tra snapshot của đơn Pending; JSP hiển thị trong cột Kiểm tra, không dùng làm quyền duyệt.
    private String checkSummary;
    private String checkMessage;

    public String getCheckSummary() {
        return checkSummary;
    }

    public void setCheckSummary(String summary) {
        this.checkSummary = summary;
    }

    public String getCheckMessage() {
        return checkMessage;
    }

    public void setCheckMessage(String message) {
        this.checkMessage = message;
    }

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

    // Khoảng ngày và số ngày nghỉ theo schema hiện tại.
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal days;

    // Người được giao xử lý và quyền của Manager đang xem.
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

    // Service: mốc sớm hơn giữa ngày nộp + 3 và ngày nghỉ - 1, lúc P-20.
    private LocalDateTime approvalDeadline;

    // Service: chuỗi đếm ngược dễ đọc (VD "Còn 3 giờ 20 phút"); rỗng nếu đơn đã xử lý.
    private String deadlineCountdownText;

    // Service: true khi đơn Pending và còn dưới ngưỡng URGENT_WITHIN_MINUTES đến hạn duyệt.
    private boolean urgent;

    // Service: true khi đơn Pending đã quá hạn để màn hình cảnh báo.
    private boolean overdue;

    /** Sao chép dữ liệu đã đọc; các thuộc tính hạn duyệt/kiểm tra do Service đặt riêng. */
    public LeaveRequestItemView(LeaveRequestRow row) {
        LeaveRequest request = row.getLeaveRequest();
        this.leaveRequestId = request.getLeaveRequestId();
        this.employeeId = request.getEmployeeId();
        this.employeeCode = row.getEmployeeCode();
        this.fullName = row.getFullName();
        this.position = row.getPosition();
        this.avatarUrl = row.getAvatarUrl();
        this.leaveType = request.getLeaveType();
        this.fromDate = request.getFromDate();
        this.toDate = request.getToDate();
        this.days = request.getDays();
        this.shiftType = request.getShiftType();
        this.reason = request.getReason();
        this.status = request.getStatus();
        this.rejectReason = request.getRejectReason();
        this.autoRejected = request.isRejected() && request.getReviewedByEmployeeId() == null;
        this.submittedAt = request.getSubmittedAt();
        this.reviewedAt = request.getReviewedAt();
        this.reviewerName = row.getReviewerName();
        this.assignedToName = row.getAssignedToName();
        this.canAct = row.isCanAct();
    }

    public void setApprovalDeadline(LocalDateTime approvalDeadline) {
        this.approvalDeadline = approvalDeadline;
    }

    public void setDeadlineCountdownText(String deadlineCountdownText) {
        this.deadlineCountdownText = deadlineCountdownText;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public void setOverdue(boolean overdue) {
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
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public BigDecimal getDays() {
        return days;
    }

    public void setDays(BigDecimal days) {
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
