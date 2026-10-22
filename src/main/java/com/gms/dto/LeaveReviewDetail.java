package com.gms.dto;

import com.gms.entity.LeaveRequest;
import com.gms.util.DateTimeUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dữ liệu BF-05: DAO đọc, Service xử lý, JSP hiển thị. */
public class LeaveReviewDetail {

    private final LeaveRequest leaveRequest;
    private final String employeeCode;
    private final String fullName;
    private final String position;
    private final String avatarUrl;
    private final String reviewerName;

    // Quân số trực dự kiến nếu duyệt và định biên tối thiểu theo cấu hình
    private int remainingStaff;
    private int minStaffRequired;

    // Service: đơn Pending bắt đầu nghỉ hôm nay hoặc ngày mai.
    private boolean nearLeaveDate;

    public LeaveReviewDetail(
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

    public int getRemainingStaff() {
        return remainingStaff;
    }

    public void setRemainingStaff(int remainingStaff) {
        this.remainingStaff = remainingStaff;
    }

    public int getMinStaffRequired() {
        return minStaffRequired;
    }

    public void setMinStaffRequired(int minStaffRequired) {
        this.minStaffRequired = minStaffRequired;
    }

    public boolean isNearLeaveDate() {
        return nearLeaveDate;
    }

    public void setNearLeaveDate(boolean nearLeaveDate) {
        this.nearLeaveDate = nearLeaveDate;
    }

    public boolean isLeaveDatePassed() {
        return leaveRequest != null && leaveRequest.getFromDate() != null
                && leaveRequest.getFromDate().isBefore(DateTimeUtil.today());
    }

    public boolean getLeaveDatePassed() {
        return isLeaveDatePassed();
    }

    /** Chỉ mở xác nhận khi tất cả kiểm tra hiện tại đạt; backend vẫn kiểm tra lại khi POST. */
    public boolean isCanApprove() {
        if (!canAct || leaveRequest == null || !leaveRequest.isPending() || isLeaveDatePassed() || checks.isEmpty()) {
            return false;
        }
        for (ReviewCheck check : checks) {
            if (check.isApplicable() && !check.isOk()) {
                return false;
            }
        }
        return true;
    }

    // DAO đọc Sessions/TimeSlots; Service gắn danh sách buổi chặn duyệt cho JSP.
    private List<SessionConflict> sessionConflicts = new ArrayList<>();
    // DAO đếm đơn cùng khoảng ngày/ca; Pending chỉ là thông tin, chưa tính là nghỉ chính thức.
    private Map<String, Integer> overlappingCounts = new LinkedHashMap<>();

    public List<SessionConflict> getSessionConflicts() {
        return sessionConflicts;
    }

    public void setSessionConflicts(List<SessionConflict> conflicts) {
        this.sessionConflicts = conflicts;
    }

    public Map<String, Integer> getOverlappingCounts() {
        return overlappingCounts;
    }

    public void setOverlappingCounts(Map<String, Integer> counts) {
        this.overlappingCounts = counts;
    }

    public boolean isAutoRejected() {
        return leaveRequest != null && leaveRequest.isRejected() && leaveRequest.getReviewedByEmployeeId() == null;
    }

    // Bảng dự báo định biên chi tiết từng ca (màn #27, UC-15)
    private List<ShiftStaffingRow> staffingRows;

    public List<ShiftStaffingRow> getStaffingRows() {
        return staffingRows;
    }

    public void setStaffingRows(List<ShiftStaffingRow> staffingRows) {
        this.staffingRows = staffingRows;
    }

    // BF-05: Người được giao duyệt, quyền thao tác, banner, timeline, checks, balance
    private Integer assignedToEmployeeId;
    private String assignedToName;
    private boolean canAct = true;
    private String banner;
    private List<TimelineNode> timeline = new ArrayList<>();
    private List<ReviewCheck> checks = new ArrayList<>();
    private LeaveBalance leaveBalance;

    public Integer getAssignedToEmployeeId() {
        return assignedToEmployeeId;
    }

    public void setAssignedToEmployeeId(Integer assignedToEmployeeId) {
        this.assignedToEmployeeId = assignedToEmployeeId;
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

    public String getBanner() {
        return banner;
    }

    public void setBanner(String banner) {
        this.banner = banner;
    }

    public List<TimelineNode> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelineNode> timeline) {
        this.timeline = timeline;
    }

    public List<ReviewCheck> getChecks() {
        return checks;
    }

    public void setChecks(List<ReviewCheck> checks) {
        this.checks = checks;
    }

    public LeaveBalance getLeaveBalance() {
        return leaveBalance;
    }

    public void setLeaveBalance(LeaveBalance leaveBalance) {
        this.leaveBalance = leaveBalance;
    }

}
