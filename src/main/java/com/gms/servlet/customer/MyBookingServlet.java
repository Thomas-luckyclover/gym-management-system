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
 * MÀN GÓI CỦA TÔI (khách) — GET /customer/bookings[?package=<SubscriptionId>|single]
 * Chỉ có doGet (màn chỉ xem, không có form POST).
 *   header:   CustomerDashboardService.getDashboard → CustomerDashboardView → attribute "dashboard" (mã khách, quyền vào gym hôm nay)
 *   nội dung: MyBookingService.getMyBookings → ServiceResult<MyBookingView> → attribute "booking" (lỗi → "bookingError")
 *   → forward /WEB-INF/jsp/customer/my-bookings.jsp
 */
@WebServlet("/customer/bookings")
public class MyBookingServlet extends HttpServlet {
    private static final String VIEW = "customer/my-bookings";
    private static final String PAGE_URL = "/customer/bookings";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyBookingService bookingService = new MyBookingService();

    // GET /customer/bookings: dựng cả màn.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chưa đăng nhập → /login (đăng nhập xong quay lại đúng trang này)
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: dữ liệu khung "Vào phòng tập" + mã khách → "dashboard"
        loadHeader(request, accountId);
        // B3: dữ liệu gói / buổi → "booking" (hoặc "bookingError")
        loadBookings(request, accountId);
        // B4: tô menu "Gói của tôi" + class giao diện
        setPageInfo(request);

        // B5: hiện my-bookings.jsp
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * HÀM PHỤ — AccountId trong session; null = chưa đăng nhập.
     */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /**
     * HÀM PHỤ (doGet B2) — CustomerDashboardService.getDashboard → "dashboard" (dashboard.canUseGym, dashboard.customerCode).
     */
    private void loadHeader(HttpServletRequest request, int accountId) {
        ServiceResult<CustomerDashboardView> header = dashboardService.getDashboard(accountId);
        if (header.isSuccess()) {
            request.setAttribute("dashboard", header.getData());
        }
    }

    /**
     * HÀM PHỤ (doGet B3) — đọc ?package= rồi gọi MyBookingService.getMyBookings.
     * Thành công → request "booking" = MyBookingView · lỗi → "bookingError" = câu lỗi (JSP hiện khung "Chưa tải được gói tập").
     */
    private void loadBookings(HttpServletRequest request, int accountId) {
        String packageKey = request.getParameter("package");
        ServiceResult<MyBookingView> result = bookingService.getMyBookings(accountId, packageKey, DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("booking", result.getData());
        } else {
            request.setAttribute("bookingError", result.getMessage());
        }
    }

    /**
     * HÀM PHỤ (doGet B4) — activeSidebar = "bookings" (tô menu) · themeClass = class của <body>.
     */
    private void setPageInfo(HttpServletRequest request) {
        request.setAttribute("activeSidebar", "bookings");

        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-bookings-page");
    }
}
