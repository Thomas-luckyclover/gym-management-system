package com.gms.service;

import com.gms.dao.LeaveRequestDAO;
import com.gms.entity.LeaveRequest;

/**
 * Nghiệp vụ đơn xin nghỉ phép của nhân viên (BF-05, UC-14, UC-15, JOB-07).
 */
public class LeaveRequestService {

    // DAO truy vấn bảng LeaveRequests; Service không tự viết SQL
    private final LeaveRequestDAO leaveRequestDAO;

    public LeaveRequestService() {
        leaveRequestDAO = new LeaveRequestDAO();
    }

    /**
     * Đổi tham số tab trạng thái trên URL thành Status để lọc danh sách của Manager.
     *
     * @param statusParam giá trị param "status" (Pending, Approved, Rejected, All) hoặc null/rỗng
     * @return Status tương ứng; null nghĩa là tab "Tất cả"; URL trống thì mặc định PENDING
     */
    LeaveRequest.Status parseStatusFilter(String statusParam) {
        // B1: URL không có status thì màn mặc định mở tab "Chờ duyệt"
        if (statusParam == null || statusParam.isBlank()) {
            return LeaveRequest.Status.PENDING;
        }

        // B2: tab "Tất cả" không lọc theo trạng thái
        if ("All".equalsIgnoreCase(statusParam.trim())) {
            return null;
        }

        // B3: giá trị lạ (fromDbValue trả null) cũng rơi về "Tất cả", không báo lỗi
        return LeaveRequest.Status.fromDbValue(statusParam.trim());
    }
}
