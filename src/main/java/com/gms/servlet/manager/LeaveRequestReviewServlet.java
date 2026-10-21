package com.gms.servlet.manager;

import com.gms.dto.LeaveReviewDetail;
import com.gms.dto.EligibleReviewer;
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
import java.util.List;

/**
 * Controller duyệt / từ chối đơn nghỉ phép cho Manager (màn #27, UC-15).
 * URL: /manager/leave-requests/review
 *
 * <p>GET  — hiển thị chi tiết đơn cần duyệt, quân số trực dự kiến.
 * <p>POST — xử lý duyệt (action=approve) hoặc từ chối (action=reject).
 */
@WebServlet("/manager/leave-requests/review")
public class LeaveRequestReviewServlet extends HttpServlet {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestReviewServlet() {
        this.leaveRequestService = new LeaveRequestService();
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

        if (leaveRequestId <= 0) {
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B4: xử lý theo hành động Duyệt (approve), Từ chối (reject) hoặc Chuyển người duyệt (transfer)
        ServiceResult<Void> result;
        String successMessage;
        String rejectReason = RequestUtil.getTrimmedParam(request, "rejectReason");

        if ("approve".equalsIgnoreCase(action)) {
            result = leaveRequestService.approveRequest(managerAccountId, leaveRequestId);
            successMessage = "Đã duyệt đơn nghỉ phép thành công.";
        } else if ("reject".equalsIgnoreCase(action)) {
            result = leaveRequestService.rejectRequest(managerAccountId, leaveRequestId, rejectReason);
            successMessage = "Đã từ chối đơn nghỉ phép.";
        } else if ("transfer".equalsIgnoreCase(action)) {
            int toEmployeeId = RequestUtil.getIntParam(request, "toEmployeeId", 0);
            String note = RequestUtil.getTrimmedParam(request, "note");
            result = leaveRequestService.transferReviewer(managerAccountId, leaveRequestId, toEmployeeId, note);
            successMessage = "Đã chuyển người duyệt thành công.";
        } else {
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B5: nếu xử lý thành công, thông báo và quay về danh sách
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", successMessage);
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B6: giữ dữ liệu nhập và lỗi, sau đó dùng lại bước tải trang của GET.
        request.setAttribute("formMessage", result.getMessage());
        request.setAttribute("rejectReason", rejectReason);
        request.setAttribute("note", RequestUtil.getTrimmedParam(request, "note"));
        request.setAttribute("toEmployeeId", RequestUtil.getIntParam(request, "toEmployeeId", 0));
        request.setAttribute("errors", result.getFieldErrors());
        showReview(request, response, leaveRequestId, managerAccountId);
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
        if (managerAccountId != null) {
            ServiceResult<List<EligibleReviewer>> reviewersResult = leaveRequestService.getEligibleReviewers(managerAccountId);
            if (reviewersResult.isSuccess()) {
                request.setAttribute("eligibleReviewers", reviewersResult.getData());
            }
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
