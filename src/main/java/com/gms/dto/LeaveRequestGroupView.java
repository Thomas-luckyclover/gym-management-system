package com.gms.dto;

import com.gms.entity.LeaveRequest;
import java.math.BigDecimal;
import java.util.List;

/** Service gom các đơn cùng đợt; JSP nhân viên/quản lý dùng chung ngày và kết quả từng phần. */
public class LeaveRequestGroupView {

    // DAO: các dòng cùng LeaveGroupId/EmployeeId; đơn độc lập có một phần.
    private final List<LeaveRequest> requests;
    // Service: mã ngắn từ UUID để nhận biết cùng đợt trên các màn.
    private final String code;

    public LeaveRequestGroupView(List<LeaveRequest> requests) {
        // Sao chép danh sách chỉ đọc; các màn cùng dùng một nhóm mà không thêm/xóa phần đơn.
        this.requests = List.copyOf(requests);
        String groupId = requests.get(0).getLeaveGroupId();
        this.code = groupId == null ? null : "NP-" + groupId.substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    public List<LeaveRequest> getRequests() {
        return requests;
    }
    public String getCode() {
        return code;
    }
    public boolean isLinked() {
        return code != null;
    }

    /** Tổng các phần còn tồn tại; rút Pending không làm thay đổi phần đã được duyệt. */
    public BigDecimal getTotalDays() {
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveRequest request : requests) {
            total = total.add(request.getDays());
        }
        return total;
    }

    public BigDecimal getApprovedDays() {
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveRequest request : requests) {
            if (request.isApproved()) {
                total = total.add(request.getDays());
            }
        }
        return total;
    }
}
