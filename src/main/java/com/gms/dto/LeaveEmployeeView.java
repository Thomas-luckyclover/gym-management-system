package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Một dòng nhân viên; bấm mở để xem các đơn phù hợp bộ lọc bên dưới. */
public class LeaveEmployeeView {

    private final int employeeId;
    private final String employeeCode;
    private final String fullName;
    private final String position;
    private final String avatarUrl;
    private final String employmentStatus;
    // DAO đếm trên toàn bộ đơn của nhân viên, không thay đổi khi chọn tab trạng thái.
    private int totalRequestCount;
    private int pendingCount;
    private LocalDate nextPendingDate;
    // Service đánh dấu khi có đơn chờ nghỉ hôm nay/ngày mai.
    private boolean nearLeaveDate;
    private List<LeaveRequestItemView> requests = new ArrayList<>();

    public LeaveEmployeeView(int employeeId, String employeeCode, String fullName, String position,
            String avatarUrl, String employmentStatus) {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.avatarUrl = avatarUrl;
        this.employmentStatus = employmentStatus;
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

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public int getTotalRequestCount() {
        return totalRequestCount;
    }

    public void setTotalRequestCount(int totalRequestCount) {
        this.totalRequestCount = totalRequestCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public LocalDate getNextPendingDate() {
        return nextPendingDate;
    }

    public void setNextPendingDate(LocalDate nextPendingDate) {
        this.nextPendingDate = nextPendingDate;
    }

    public boolean isNearLeaveDate() {
        return nearLeaveDate;
    }

    public void setNearLeaveDate(boolean nearLeaveDate) {
        this.nearLeaveDate = nearLeaveDate;
    }

    public List<LeaveRequestItemView> getRequests() {
        return requests;
    }

    public void setRequests(List<LeaveRequestItemView> requests) {
        this.requests = requests;
    }

}
