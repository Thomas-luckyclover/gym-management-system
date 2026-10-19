package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.MyBookingView;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDashboardService;
import com.gms.service.MyBookingService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Màn "Gói của tôi" (#50) — GET /customer/bookings?id=<mã gói | single> (CHỈ XEM).
 *   id: gói đang chọn ở cột trái; "single" = các buổi lẻ; sai/thiếu → gói đầu tiên.
 */
@WebServlet("/customer/bookings")
public class MyBookingServlet extends HttpServlet {

    private static final String VIEW = "customer/my-bookings";
    private static final String PAGE_URL = "/customer/bookings";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyBookingService bookingService = new MyBookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: phải đăng nhập
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: header (tên + mã khách)
        ServiceResult<CustomerDashboardView> header = dashboardService.getDashboard(accountId);
        if (header.isSuccess()) {
            request.setAttribute("dashboard", header.getData());
        }

        // B3: dữ liệu màn (cột trái + gói đang chọn)
        ServiceResult<MyBookingView> result =
                bookingService.getMyBookings(accountId, request.getParameter("id"), DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("booking", result.getData());
        } else {
            request.setAttribute("bookingError", result.getMessage());
        }

        // B4: sang JSP
        request.setAttribute("activeSidebar", "bookings");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-bookings-page");
        RequestUtil.forward(request, response, VIEW);
    }

    /** accountId trong HttpSession (đặt lúc đăng nhập). Null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
