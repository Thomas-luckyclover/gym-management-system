package com.gms.dto;

import com.gms.entity.LeaveRequest;

/** Dữ liệu BF-05: DAO đọc, Service xử lý, JSP hiển thị. */
public class LeaveRequestRow {

    // DAO: toàn bộ cột LeaveRequests của đơn này.
    private final LeaveRequest leaveRequest;

    // DAO: Employees.EmployeeCode, ví dụ NV-0005.
    private final String employeeCode;

    // DAO: Persons.FullName của nhân viên nộp đơn.
    private final String fullName;

    // DAO: Employees.Position (PT hoặc Receptionist).
    private final String position;

    // DAO: Persons.Avatar; NULL thì JSP hiện chữ viết tắt.
    private final String avatarUrl;

    // DAO: Persons.FullName của người duyệt; NULL khi Pending hoặc tự từ chối.
    private final String reviewerName;

    // V2: Người được giao duyệt và quyền thao tác
    private String assignedToName;
    private boolean canAct = true;

    public LeaveRequestRow(
            LeaveRequest leaveRequest,
            String employeeCode,
            String fullName,
            String position,
            String avatarUrl,
            String reviewerName
    ) {
        this.leaveRequest = leaveRequest;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.avatarUrl = avatarUrl;
        this.reviewerName = reviewerName;
    }

    public LeaveRequest getLeaveRequest() {
        return leaveRequest;
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

    public String getReviewerName() {
        return reviewerName;
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
