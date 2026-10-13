package com.gms.servlet.manager;

import com.gms.service.LeaveRequestService;
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
        // TODO: cài đặt doGet ở bước tiếp theo
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // TODO: cài đặt doPost ở bước tiếp theo
    }
}
