package com.gms.servlet.manager;

import com.gms.dao.LeaveRequestDAO;
import com.gms.dto.ServiceResult;
import com.gms.service.LeaveRequestService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

        // B4: đặt attribute cho JSP hiển thị
        request.setAttribute("item", result.getData());
        request.setAttribute("activeNav", "leaves");
        RequestUtil.forward(request, response, "manager/leave-request-review");
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // TODO: cài đặt doPost ở bước tiếp theo
    }
}
