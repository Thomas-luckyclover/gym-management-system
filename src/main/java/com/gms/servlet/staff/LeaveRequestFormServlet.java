package com.gms.servlet.staff;

import com.gms.service.LeaveRequestService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
        // TODO: xử lý doGet ở commit kế tiếp
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // TODO: xử lý doPost ở commit kế tiếp
    }
}
