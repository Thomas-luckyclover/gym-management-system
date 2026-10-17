package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dao.LeaveRequestDAO;
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

        // B2: gọi Service lấy thông tin chi tiết đơn
        ServiceResult<LeaveRequestDAO.LeaveReviewDetail> result
                = leaveRequestService.getReviewDetail(leaveRequestId);

        // B3: nếu không tìm thấy, báo lỗi và quay về danh sách
        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "danger", result.getMessage());
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B4: sinh CSRF token và đặt attribute cho JSP hiển thị
        HttpSession session = request.getSession(true);
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));
        request.setAttribute("item", result.getData());
        request.setAttribute("activeNav", "leaves");
        RequestUtil.forward(request, response, "manager/leave-request-review");
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        // B1: lấy accountId của Manager từ session
        HttpSession session = request.getSession(false);
        Integer managerAccountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

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

        // B4: xử lý theo hành động Duyệt (approve) hoặc Từ chối (reject)
        ServiceResult<Void> result;
        String rejectReason = RequestUtil.getTrimmedParam(request, "rejectReason");

        if ("approve".equalsIgnoreCase(action)) {
            result = leaveRequestService.approveRequest(managerAccountId, leaveRequestId);
        } else if ("reject".equalsIgnoreCase(action)) {
            result = leaveRequestService.rejectRequest(managerAccountId, leaveRequestId, rejectReason);
        } else {
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B5: nếu xử lý thành công, thông báo và quay về danh sách
        if (result.isSuccess()) {
            String successMsg = "approve".equalsIgnoreCase(action)
                    ? "Đã duyệt đơn nghỉ phép thành công."
                    : "Đã từ chối đơn nghỉ phép.";
            RequestUtil.setFlashToast(request, "success", successMsg);
            RequestUtil.redirect(request, response, "/manager/leave-requests");
            return;
        }

        // B6: nếu thất bại (VD vi phạm min staffing hoặc thiếu lý do), nạp lại form kèm lỗi
        ServiceResult<LeaveRequestDAO.LeaveReviewDetail> detailResult
                = leaveRequestService.getReviewDetail(leaveRequestId);
        if (detailResult.isSuccess()) {
            request.setAttribute("item", detailResult.getData());
        }

        // Đảm bảo truyền lại csrfToken cho form hiển thị lại
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));
        request.setAttribute("formMessage", result.getMessage());
        request.setAttribute("rejectReason", rejectReason);
        request.setAttribute("errors", result.getFieldErrors());
        request.setAttribute("activeNav", "leaves");

        RequestUtil.forward(request, response, "manager/leave-request-review");
    }
}
