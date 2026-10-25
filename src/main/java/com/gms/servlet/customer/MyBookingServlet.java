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
 * MÀN "GÓI CỦA TÔI" (#50) — GET /customer/bookings?package=<mã gói | single>. CHỈ XEM, không có form.
 *
 *   doGet         HÀM CHÍNH: kiểm tra đăng nhập → 3 hàm phụ đặt attribute → JSP customer/my-bookings.jsp
 *   getAccountId  HÀM PHỤ: đọc tài khoản đang đăng nhập từ session
 *   loadHeader    HÀM PHỤ: tên + mã khách ở topbar (CustomerDashboardService)
 *   loadBookings  HÀM PHỤ: dữ liệu chính của màn (MyBookingService)
 *   setPageInfo   HÀM PHỤ: menu đang chọn + class giao diện
 *
 * ?package= chỉ là "đang chọn gói nào"; Service tự kiểm tra gói đó có thuộc khách này không.
 */
@WebServlet("/customer/bookings")
public class MyBookingServlet extends HttpServlet {

    private static final String VIEW = "customer/my-bookings";
    private static final String PAGE_URL = "/customer/bookings";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyBookingService bookingService = new MyBookingService();


    /**
     * HÀM CHÍNH — trình duyệt gọi khi khách mở "Gói của tôi" hoặc bấm 1 gói ở cột trái (?package=...).
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

        // B2: header + dữ liệu chính + thông tin trang.
        loadHeader(request, accountId);
        loadBookings(request, accountId);
        setPageInfo(request);

        // B3: mở JSP.
        RequestUtil.forward(request, response, VIEW);
    }

    /** HÀM PHỤ — gọi ở B1 của doGet. accountId lưu trong session lúc đăng nhập; null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /**
     * HÀM PHỤ — gọi ở B2 của doGet.
     * dashboard = tên + mã khách (góc phải topbar) + canUseGym (thẻ "Vào phòng tập" ở cột trái).
     * Lỗi thì 2 chỗ đó tự ẩn, phần còn lại của màn vẫn hiện.
     *
     * JSP ĐỌC "dashboard" Ở ĐÂU:
     *   _schedule-header.jspf dòng 34–44   tên, chữ cái đầu, mã khách ở topbar
     *                                      (jspf này được nhúng ở my-bookings.jsp dòng 76)
     *   my-bookings.jsp dòng 197–206       thẻ "Vào phòng tập": dashboard.canUseGym
     *   my-bookings.jsp dòng 305           customerCode = dashboard.customerCode (câu "báo mã KH-…" ở CTA)
     */
    private void loadHeader(HttpServletRequest request, int accountId) {
        ServiceResult<CustomerDashboardView> header = dashboardService.getDashboard(accountId);
        if (header.isSuccess()) {
            request.setAttribute("dashboard", header.getData());   // → xem danh sách dòng JSP ở trên
        }
    }

    /**
     * HÀM PHỤ — gọi ở B2 của doGet.
     * Dữ liệu chính của màn (cột trái + gói đang chọn). Thành công → "booking"; lỗi → "bookingError".
     *
     * ?package= ĐẾN TỪ ĐÂU (4 link gửi lên, giá trị = Subscriptions.SubscriptionId hoặc "single"):
     *   _booking-package.jspf dòng 11   bấm thẻ gói ở cột trái → ?package=82
     *                                   (jspf nhúng ở my-bookings.jsp dòng 134 tab "Đang tập", dòng 142 tab "Đã kết thúc")
     *   my-bookings.jsp dòng 151        bấm thẻ "Buổi lẻ" → ?package=single
     *   _session-detail.jspf dòng 56    "Xem toàn bộ gói" trong hộp chi tiết buổi ở màn Lịch tập
     *                                   (nhúng ở _session-card.jspf dòng 23, card nhúng ở my-pt-sessions.jsp dòng 197)
     *   my-pt-sessions.jsp dòng 78      nút "‹ Quay lại Gói của tôi" ở màn Lịch tập
     *   Không có ?package= (bấm menu "Gói của tôi") → Service tự chọn gói đầu tiên.
     *
     * JSP ĐỌC Ở ĐÂU:
     *   "booking"       my-bookings.jsp dòng 46–56 (c:set sp, f, cta, backPackageKey, openTab) rồi toàn bộ dòng 90–504;
     *                   _booking-package.jspf dòng 11 (gói đang chọn có aria-current)
     *   "bookingError"  my-bookings.jsp dòng 81–84 hộp "Chưa tải được gói tập" + nút Tải lại
     */
    private void loadBookings(HttpServletRequest request, int accountId) {
        String packageKey = request.getParameter("package");   // "82" | "single" | null — xem 4 link ở trên
        ServiceResult<MyBookingView> result = bookingService.getMyBookings(accountId, packageKey, DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("booking", result.getData());              // → my-bookings.jsp dòng 46 trở đi
        } else {
            request.setAttribute("bookingError", result.getMessage());      // → my-bookings.jsp dòng 81–84
        }
    }

    /** HÀM PHỤ — gọi ở B2 của doGet. Menu trái đang chọn "Gói của tôi" + class giao diện cho body. */
    private void setPageInfo(HttpServletRequest request) {
        // → layout-customer.jspf dòng 28–29 tô mục "Gói của tôi" (jspf nhúng ở my-bookings.jsp dòng 72);
        //   _schedule-header.jspf dòng 18, 28 chọn chữ nút (i) + ẩn nút "Đặt buổi lẻ" (nhúng ở my-bookings.jsp dòng 76)
        request.setAttribute("activeSidebar", "bookings");
        // → my-bookings.jsp dòng 68 <body class="dock ${themeClass}">
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-bookings-page");
    }

}
