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

    // Service: các điều kiện hiện tại đạt; xác nhận và quyền vẫn được kiểm lại khi POST.
    private boolean approvalReady;

    // Service đọc đầy đủ cả đợt, không bị giới hạn bởi tab/bộ lọc/trang của dòng hiện tại.
    private LeaveRequestGroupView leaveGroup;

    // DAO: LeaveRequests.LeaveRequestId.
    private final int leaveRequestId;

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
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final BigDecimal days;

    // DAO: quyền thao tác của Manager đang xem; tên người xử lý chỉ đọc ở chi tiết.
    private final boolean canAct;

    // DAO: LeaveRequests.ShiftType.
    private final LeaveRequest.ShiftType shiftType;

    // DAO: LeaveRequests.Reason.
    private final String reason;

    // DAO: LeaveRequests.Status.
    private final LeaveRequest.Status status;

    // DAO: LeaveRequests.SubmittedAt.
    private final LocalDateTime submittedAt;

    // Service: đơn Pending bắt đầu nghỉ hôm nay hoặc ngày mai.
    private boolean nearLeaveDate;

    // Service: đơn Pending bắt đầu trước hôm nay; phải xác nhận riêng, không duyệt hàng loạt.
    private boolean lateApproval;

    /** Sao chép dữ liệu đã đọc; cờ sắp nghỉ và kết quả kiểm tra do Service đặt riêng. */
    public LeaveRequestItemView(LeaveRequestRow row) {
        LeaveRequest request = row.getLeaveRequest();
        this.leaveRequestId = request.getLeaveRequestId();
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
        this.submittedAt = request.getSubmittedAt();
        this.canAct = row.isCanAct();
    }

    public LeaveRequestGroupView getLeaveGroup() {
        return leaveGroup;
    }

    public void setLeaveGroup(LeaveRequestGroupView leaveGroup) {
        this.leaveGroup = leaveGroup;
    }

    public boolean isCanApprove() {
        return approvalReady && canAct && status == LeaveRequest.Status.PENDING;
    }

    public void setApprovalReady(boolean approvalReady) {
        this.approvalReady = approvalReady;
    }

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

    public boolean isLateApproval() {
        return lateApproval;
    }

    public void setLateApproval(boolean lateApproval) {
        this.lateApproval = lateApproval;
    }

    public boolean isNearLeaveDate() {
        return nearLeaveDate;
    }

    public void setNearLeaveDate(boolean nearLeaveDate) {
        this.nearLeaveDate = nearLeaveDate;
    }

    public int getLeaveRequestId() {
        return leaveRequestId;
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

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public BigDecimal getDays() {
        return days;
    }

    public boolean isCanAct() {
        return canAct;
    }
}
