package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveRequestItemView;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.LeaveRequest;
import com.gms.util.DateTimeUtil;
import com.gms.util.PaginationUtil;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Nghiệp vụ đơn xin nghỉ phép của nhân viên (BF-05, UC-14, UC-15, JOB-07).
 */
public class LeaveRequestService {

    // Đơn còn dưới số phút này đến hạn duyệt thì đánh dấu khẩn cấp (tô nổi trên danh sách)
    private static final long URGENT_WITHIN_MINUTES = 180;

    // DAO truy vấn bảng LeaveRequests; Service không tự viết SQL
    private final LeaveRequestDAO leaveRequestDAO;

    // Đọc tham số P-20 (giờ chốt duyệt đơn)
    private final SystemConfigService systemConfigService;

    public LeaveRequestService() {
        leaveRequestDAO = new LeaveRequestDAO();
        systemConfigService = new SystemConfigService();
    }

    /**
     * Lấy một trang đơn nghỉ phép cho màn danh sách của Manager (màn #26, UC-15).
     *
     * @param keyword  từ khóa tên nhân viên từ URL, có thể null/rỗng
     * @param status   tab trạng thái từ URL (Pending, Approved, Rejected, All), có thể null
     * @param page     số trang, bắt đầu từ 1
     * @param pageSize số dòng mỗi trang
     * @return trang các dòng đã tính hạn duyệt và đếm ngược, hoặc thông báo lỗi hệ thống
     */
    public ServiceResult<PageResult<LeaveRequestItemView>> getManagerPage(
            String keyword, String status, int page, int pageSize) {

        try {
            // B1: chuẩn hóa tham số URL thành bộ lọc hợp lệ
            String trimmedKeyword = keyword == null ? "" : keyword.trim();
            LeaveRequestFilter filter = new LeaveRequestFilter(
                    parseStatusFilter(status), trimmedKeyword);

            // B2: đếm tổng và lấy đúng một trang (DAO cắt bằng OFFSET/FETCH)
            int totalItems = leaveRequestDAO.countByFilter(filter);
            int offset = PaginationUtil.calculateOffset(page, pageSize);
            List<LeaveRequestDAO.LeaveRequestRow> rows
                    = leaveRequestDAO.findManagerPage(filter, offset, pageSize);

            // B3: đổi từng dòng DAO sang view, thêm hạn duyệt P-20 và đếm ngược
            LocalTime approvalTime = LocalTime.parse(
                    systemConfigService.getString(
                            SystemConfigService.LEAVE_APPROVED_DEADLINE));
            LocalDateTime now = DateTimeUtil.now();

            List<LeaveRequestItemView> items = new ArrayList<>();
            for (LeaveRequestDAO.LeaveRequestRow row : rows) {
                items.add(toItemView(row, approvalTime, now));
            }

            return ServiceResult.ok(
                    new PageResult<>(items, page, pageSize, totalItems));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
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

    /**
     * Đổi một dòng DAO sang LeaveRequestItemView: tính hạn duyệt (P-20 của ngày
     * liền trước ngày nghỉ, CBR-85), chữ đếm ngược và cờ khẩn cấp cho đơn Pending.
     */
    private LeaveRequestItemView toItemView(
            LeaveRequestDAO.LeaveRequestRow row,
            LocalTime approvalTime,
            LocalDateTime now) {

        LeaveRequest request = row.getLeaveRequest();

        // B1: hạn duyệt = giờ P-20 vào ngày liền trước ngày nghỉ
        LocalDateTime approvalDeadline
                = request.getLeaveDate().minusDays(1).atTime(approvalTime);

        // B2: đơn đã xử lý không cần đếm ngược; đơn Pending thì tính số phút còn lại
        String countdownText = "";
        boolean isUrgent = false;
        if (request.isPending()) {
            long minutesLeft = Duration.between(now, approvalDeadline).toMinutes();
            countdownText = formatCountdown(minutesLeft);
            isUrgent = minutesLeft < URGENT_WITHIN_MINUTES;
        }

        // B3: tự từ chối = Rejected mà không có người duyệt (CBR-85)
        boolean isAutoRejected = request.isRejected()
                && request.getReviewedByEmployeeId() == null;

        return new LeaveRequestItemView(
                request.getLeaveRequestId(),
                request.getEmployeeId(),
                row.getEmployeeCode(),
                row.getFullName(),
                row.getPosition(),
                row.getAvatarUrl(),
                request.getLeaveType(),
                request.getLeaveDate(),
                request.getShiftType(),
                request.getReason(),
                request.getStatus(),
                request.getRejectReason(),
                isAutoRejected,
                request.getSubmittedAt(),
                request.getReviewedAt(),
                row.getReviewerName(),
                approvalDeadline,
                countdownText,
                isUrgent);
    }

    /** Đổi số phút còn lại thành chữ hiển thị; số âm nghĩa là đã quá hạn duyệt. */
    private String formatCountdown(long minutesLeft) {
        if (minutesLeft < 0) {
            return "Đã quá hạn duyệt";
        }
        if (minutesLeft < 60) {
            return "Còn " + minutesLeft + " phút";
        }
        return "Còn " + (minutesLeft / 60) + " giờ " + (minutesLeft % 60) + " phút";
    }
}
