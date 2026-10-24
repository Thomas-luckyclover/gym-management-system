package com.gms.dto;

import com.gms.entity.LeaveRequest;
import com.gms.util.DateTimeUtil;
import java.util.ArrayList;
import java.util.List;

/** Dữ liệu BF-05: DAO đọc, Service xử lý, JSP hiển thị. */
public class LeaveReviewDetail {

    private final LeaveRequest leaveRequest;
    private final String employeeCode;
    private final String fullName;
    private final String position;
    private final String avatarUrl;
    private final String reviewerName;

    // Service: các phần cùng đợt để xem kết quả và mở từng đơn; không gộp quyết định duyệt.
    private LeaveRequestGroupView leaveGroup;

    // Service: đơn Pending bắt đầu nghỉ hôm nay hoặc ngày mai.
    private boolean nearLeaveDate;

    // Bảng dự báo định biên chi tiết từng ca (màn #27, UC-15)
    private List<ShiftStaffingRow> staffingRows;

    // DAO giữ tên người được giao trên dữ liệu cũ để giải thích quyền chỉ xem.
    private String assignedToName;
    private boolean canAct = true;
    private String banner;
    // Service lấy lý do khóa duyệt từ cùng bộ kiểm tra với POST; JSP dùng cho tooltip nút.
    private String approvalBlockMessage;
    private List<ReviewCheck> checks = new ArrayList<>();

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

    public LeaveRequestGroupView getLeaveGroup() {
        return leaveGroup;
    }

    public void setLeaveGroup(LeaveRequestGroupView leaveGroup) {
        this.leaveGroup = leaveGroup;
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

    /** Chỉ mở xác nhận khi tất cả kiểm tra hiện tại đạt; backend vẫn kiểm tra lại khi POST. */
    public boolean isCanApprove() {
        if (!canAct || leaveRequest == null || !leaveRequest.isPending() || checks.isEmpty()) {
            return false;
        }
        for (ReviewCheck check : checks) {
            if (check.isApplicable() && !check.isOk()) {
                return false;
            }
        }
        return true;
    }

    public boolean isAutoRejected() {
        return leaveRequest != null && leaveRequest.isRejected() && leaveRequest.getReviewedByEmployeeId() == null;
    }

    public List<ShiftStaffingRow> getStaffingRows() {
        return staffingRows;
    }

    public void setStaffingRows(List<ShiftStaffingRow> staffingRows) {
        this.staffingRows = staffingRows;
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

    /** Lý do khóa nút duyệt, lấy từ bộ kiểm tra của Service. */
    public String getApprovalBlockMessage() {
        return approvalBlockMessage;
    }

    /** Service gắn thông báo cho tooltip; null khi đủ điều kiện. */
    public void setApprovalBlockMessage(String approvalBlockMessage) {
        this.approvalBlockMessage = approvalBlockMessage;
    }

    public List<ReviewCheck> getChecks() {
        return checks;
    }

    public void setChecks(List<ReviewCheck> checks) {
        this.checks = checks;
    }
}
