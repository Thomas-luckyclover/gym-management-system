
package com.gms.servlet.staff;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Controller nhân viên (PT/Lễ tân) nộp đơn xin nghỉ phép (UC-14, màn #25).
 *
 * <p>GET  /staff/leave-requests — hiện form nộp đơn.
 * <p>POST /staff/leave-requests — xử lý submit đơn.
 */
@WebServlet("/staff/leave-requests")
public class LeaveRequestSubmitServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // TODO: render form nộp đơn nghỉ phép
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // TODO: xử lý submit đơn nghỉ phép
    }
}
