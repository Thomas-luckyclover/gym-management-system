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
 * SERVLET MÀN GÓI CỦA TÔI (khách) — /customer/bookings[?package=<SubscriptionId>|single] (UC-35 xem gói PT / buổi lẻ).
 *
 * VIỆC CỦA SERVLET: đọc tham số → gọi Service → đặt attribute → forward JSP. Màn chỉ xem, không có form POST.
 * TRƯỚC SERVLET: CustomerAreaFilter chặn vai trò khác.
 *
 *   URL                                          Servlet gọi                                         Kết quả
 *   GET  /customer/bookings                       doGet → myBookingService.getMyBookings              forward my-bookings.jsp ("booking")
 *   GET  /customer/bookings?package=<Id>|single   như trên, chọn gói đang xem / buổi lẻ                như trên
 *   (cùng lần GET)                                loadHeader → CustomerDashboardService.getDashboard  "dashboard": mã khách, quyền vào gym hôm nay
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  doGet   dựng cả màn   → getAccountId, loadHeader, loadBookings, setPageInfo
 */
@WebServlet("/customer/bookings")
public class MyBookingServlet extends HttpServlet {
    private static final String VIEW = "customer/my-bookings";
    private static final String PAGE_URL = "/customer/bookings";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyBookingService bookingService = new MyBookingService();



    // ============================================================================================================
    // HÀM CHÍNH 1. doGet — dựng cả màn Gói của tôi
    //   Ai gọi: menu "Gói của tôi", link từ Dashboard khách  ·  Ra: forward my-bookings.jsp
    //   Hàm phụ ngay dưới, theo thứ tự doGet gọi: getAccountId → loadHeader → loadBookings → setPageInfo
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — dựng cả màn Gói của tôi.
     *   Ai gọi: menu "Gói của tôi", link từ Dashboard khách
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chưa đăng nhập → về trang đăng nhập
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: khung "Vào phòng tập" + mã khách
        loadHeader(request, accountId);

        // B3: gói và buổi tập
        loadBookings(request, accountId);

        // B4: tô menu
        setPageInfo(request);

        // B5: hiện trang
        RequestUtil.forward(request, response, VIEW);
    }

    /** HÀM PHỤ (doGet B1) — AccountId trong session; null = chưa đăng nhập. */
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
