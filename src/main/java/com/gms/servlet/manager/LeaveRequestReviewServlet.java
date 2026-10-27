package com.gms.servlet.manager;

import com.gms.dto.LeaveReviewDetail;
import com.gms.dto.LeaveBulkApprovalResult;
import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.service.LeaveRequestService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller duyệt / từ chối đơn nghỉ phép cho Manager (màn #27, UC-15).
 * URL: /manager/leave-requests/review
 *
 * <p>GET  — hiển thị chi tiết đơn cần duyệt, quân số trực dự kiến.
 * <p>POST — duyệt một/nhiều đơn hoặc từ chối kèm lý do.
 */
@WebServlet("/manager/leave-requests/review")
public class LeaveRequestReviewServlet extends HttpServlet {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestReviewServlet() {
        this(new LeaveRequestService());
    }

    /** Truyền Service giả khi kiểm tra hợp đồng POST, không ghi DB thật. */
    LeaveRequestReviewServlet(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // B1: đọc mã đơn cần duyệt từ query param ?id=
        int leaveRequestId = RequestUtil.getIntParam(request, "id", 0);
        if (leaveRequestId <= 0) {
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B2: tải dữ liệu và hiển thị; POST lỗi cũng dùng cùng bước này.
        showReview(request, response, leaveRequestId, getManagerAccountId(request));
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        // B1: lấy accountId của Manager từ session
        Integer managerAccountId = getManagerAccountId(request);

        if (managerAccountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B2: kiểm tra tính hợp lệ của CSRF token chống tấn công giả mạo yêu cầu
        if (!RequestUtil.isValidCsrfToken(request)) {
            RequestUtil.setFlashToast(request, "danger", "Phiên làm việc không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.");
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B3: đọc tham số form
        int leaveRequestId = RequestUtil.getIntParam(request, "leaveRequestId", 0);
        String action = RequestUtil.getTrimmedParam(request, "action");

        if ("approveMany".equals(action)) {
            approveMany(request, response, managerAccountId);
            return;
        }

        if (leaveRequestId <= 0) {
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B4: duyệt hoặc từ chối; hành động khác không được gọi Service ghi dữ liệu.
        ServiceResult<Void> result;
        String successMessage;
        String rejectReason = RequestUtil.getTrimmedParam(request, "rejectReason");

        if ("approve".equalsIgnoreCase(action)) {
            String lateApprovalReason = RequestUtil.getTrimmedParam(request, "lateApprovalReason");
            // Giữ đường gọi thường cho duyệt nhanh; Service tự xác định muộn từ ngày lưu trong DB.
            if (lateApprovalReason == null || lateApprovalReason.isEmpty()) {
                result = leaveRequestService.approveRequest(managerAccountId, leaveRequestId);
            } else {
                result = leaveRequestService.approveRequest(managerAccountId, leaveRequestId, lateApprovalReason);
            }
            successMessage = "Đã duyệt đơn nghỉ phép thành công.";
        } else if ("reject".equalsIgnoreCase(action)) {
            result = leaveRequestService.rejectRequest(managerAccountId, leaveRequestId, rejectReason);
            successMessage = "Đã từ chối đơn nghỉ phép.";
        } else {
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B5: nếu xử lý thành công, thông báo và quay về danh sách
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", successMessage);
            // Form trong danh sách giữ tab/bộ lọc/trang; chỉ nhận đường dẫn danh sách nội bộ.
            returnToList(request, response);
            return;
        }

        // B6: giữ dữ liệu nhập và lỗi, sau đó dùng lại bước tải trang của GET.
        request.setAttribute("formMessage", result.getMessage());
        request.setAttribute("rejectReason", rejectReason);
        request.setAttribute("lateApprovalReason", RequestUtil.getTrimmedParam(request, "lateApprovalReason"));
        request.setAttribute("errors", result.getFieldErrors());
        showReview(request, response, leaveRequestId, managerAccountId);
    }

    /** Đọc các mã checkbox; toàn bộ danh sách phải hợp lệ trước khi gọi Service ghi dữ liệu. */
    private void approveMany(HttpServletRequest request, HttpServletResponse response, int managerAccountId)
            throws IOException {
        String[] values = request.getParameterValues("leaveRequestIds");
        List<Integer> requestIds = new ArrayList<>();
        if (values != null && values.length <= 50) {
            try {
                for (String value : values) {
                    requestIds.add(Integer.valueOf(value));
                }
            } catch (NumberFormatException e) {
                RequestUtil.setFlashToast(request, "danger", "Danh sách đơn không hợp lệ. Vui lòng tải lại trang.");
                returnToList(request, response);
                return;
            }
        }
        ServiceResult<LeaveBulkApprovalResult> result = leaveRequestService.approveRequests(managerAccountId, requestIds);
        if (result.isSuccess()) {
            request.getSession().setAttribute(SessionKey.LEAVE_BULK_RESULT, result.getData());
        } else {
            RequestUtil.setFlashToast(request, "danger", result.getMessage());
        }
        returnToList(request, response);
    }

    /** Chỉ trở lại danh sách nội bộ, giữ tab/bộ lọc/phân trang của form. */
    private void returnToList(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String returnUrl = RequestUtil.getTrimmedParam(request, "returnUrl");
        String listPath = "/manager/leave-requests";
        if (returnUrl == null || !(returnUrl.equals(listPath) || returnUrl.startsWith(listPath + "?"))) {
            returnUrl = listPath;
        }
        RequestUtil.redirect(request, response, returnUrl);
    }

    /** GET và POST lỗi đều đi qua đây, tránh quên nạp dữ liệu hoặc CSRF token. */
    private void showReview(HttpServletRequest request, HttpServletResponse response, int leaveRequestId,
            Integer managerAccountId) throws ServletException, IOException {
        ServiceResult<LeaveReviewDetail> detailResult = leaveRequestService.getReviewDetail(leaveRequestId, managerAccountId);
        if (!detailResult.isSuccess()) {
            RequestUtil.setFlashToast(request, "danger", detailResult.getMessage());
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(true)));
        request.setAttribute("item", detailResult.getData());
        request.setAttribute("activeNav", "leaves");
        RequestUtil.forward(request, response, "manager/leave-request-review");
    }

    private Integer getManagerAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
