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
 * MÀN "GÓI CỦA TÔI" (#50) — GET /customer/bookings?id=<mã gói | single>. CHỈ XEM, không có form.
 *
 *   doGet         HÀM CHÍNH: gọi 2 Service → đặt attribute → JSP customer/my-bookings.jsp
 *   getAccountId  HÀM PHỤ: đọc tài khoản đang đăng nhập từ session
 *
 * ?id= chỉ là "đang chọn gói nào"; Service tự kiểm tra gói đó có thuộc khách này không.
 */
@WebServlet("/customer/bookings")
public class MyBookingServlet extends HttpServlet {

    private static final String VIEW = "customer/my-bookings";
    private static final String PAGE_URL = "/customer/bookings";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyBookingService bookingService = new MyBookingService();


    /**
     * HÀM CHÍNH — trình duyệt gọi khi khách mở "Gói của tôi" hoặc bấm 1 gói ở cột trái (?id=...).
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: phải đăng nhập.
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: dashboard = tên + mã khách (góc phải topbar) + canUseGym (thẻ "Vào phòng tập" ở cột trái).
        //     Lỗi thì 2 chỗ đó tự ẩn, phần còn lại của màn vẫn hiện.
        ServiceResult<CustomerDashboardView> header = dashboardService.getDashboard(accountId);
        if (header.isSuccess()) {
            request.setAttribute("dashboard", header.getData());
        }

        // B3: dữ liệu chính của màn (cột trái + gói đang chọn).
        ServiceResult<MyBookingView> result =
                bookingService.getMyBookings(accountId, request.getParameter("id"), DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("booking", result.getData());
        } else {
            request.setAttribute("bookingError", result.getMessage());
        }

        // B4: thông tin chung rồi mở JSP.
        request.setAttribute("activeSidebar", "bookings");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-bookings-page");
        RequestUtil.forward(request, response, VIEW);
    }

    /** HÀM PHỤ — gọi ở B1 của doGet. accountId lưu trong session lúc đăng nhập; null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

}
