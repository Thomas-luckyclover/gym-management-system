package com.gms.servlet.staff;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.dto.StaffLeaveFormView;
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
 * Controller nhân viên (PT và Lễ tân) nộp đơn xin nghỉ phép (màn #25, UC-14).
 * Một Servlet khai báo 2 URL theo tài liệu docs/project-structure.md.
 *
 * <p>GET  /pt/leave-requests + /receptionist/leave-requests — hiển thị form nộp đơn.
 * <p>POST /pt/leave-requests + /receptionist/leave-requests — tiếp nhận và xử lý đơn nộp.
 */
@WebServlet({"/pt/leave-requests", "/receptionist/leave-requests"})
public class LeaveRequestFormServlet extends HttpServlet {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestFormServlet() {
        this.leaveRequestService = new LeaveRequestService();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // B1: lấy accountId từ session đăng nhập
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B2: gọi Service lấy dữ liệu khởi tạo cho form nộp đơn
        ServiceResult<StaffLeaveFormView> result = leaveRequestService.getStaffLeaveForm(accountId);

        // B3: chuyển dữ liệu sang JSP
        if (!result.isSuccess()) {
            request.setAttribute("formError", result.getMessage());
        } else {
            request.setAttribute("formView", result.getData());
        }

        RequestUtil.forward(request, response, "staff/leave-request-form");
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // TODO: xử lý doPost ở commit kế tiếp
    }
}
