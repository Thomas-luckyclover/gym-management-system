package com.gms.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Service tạo sau khi duyệt lần lượt; danh sách hiển thị số thành công và lý do các đơn còn lại. */
public class LeaveBulkApprovalResult {
    // Service đếm các approveRequest thành công; JSP hiện tổng số đơn đã duyệt.
    private final int approvedCount;
    // Service giữ mã đơn và lỗi của approveRequest; JSP hiện để Quản lý biết đơn nào cần xem lại.
    private final Map<Integer, String> failedRequests;

    public LeaveBulkApprovalResult(int approvedCount, Map<Integer, String> failedRequests) {
        this.approvedCount = approvedCount;
        this.failedRequests = Collections.unmodifiableMap(new LinkedHashMap<>(failedRequests));
    }

    public int getApprovedCount() {
        return approvedCount;
    }

    public Map<Integer, String> getFailedRequests() {
        return failedRequests;
    }
}
