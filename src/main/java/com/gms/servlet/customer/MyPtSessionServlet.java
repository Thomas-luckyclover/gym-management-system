package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDashboardService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

/** Điều phối khung màn hình các buổi PT của Customer. */
@WebServlet("/customer/pt-sessions")
public class MyPtSessionServlet extends HttpServlet {

    private static final String VIEW = "customer/my-pt-sessions";
    private final CustomerDashboardService dashboardService = new CustomerDashboardService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: lấy account hiện tại; CustomerAreaFilter đã kiểm tra quyền truy cập.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/customer/pt-sessions");
            return;
        }

        // B2: lấy thông tin header dùng chung với Customer Dashboard.
        ServiceResult<CustomerDashboardView> dashboardResult =
                dashboardService.getDashboard(accountId);
        if (dashboardResult.isSuccess()) {
            request.setAttribute("dashboard", dashboardResult.getData());
        } else {
            request.setAttribute("dashboardError", dashboardResult.getMessage());
        }

        // B3: dữ liệu buổi thật sẽ nối qua MyPtSessionService ở bước nghiệp vụ.
        request.setAttribute("ptSessions", List.of());
        request.setAttribute("activeSidebar", "pt-sessions");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-pt-sessions-page");

        RequestUtil.forward(request, response, VIEW);
    }
}
