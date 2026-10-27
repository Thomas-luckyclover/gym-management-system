package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDashboardService;
import com.gms.dto.WeatherView;
import com.gms.service.WeatherService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Controller Customer Dashboard (#48, UC-41, SRS 9.1.1) — URL /customer/dashboard.
 *
 * Chỉ điều phối: lấy accountId từ session, gọi Service, forward JSP.
 * Phân quyền vai trò Customer do CustomerAreaFilter đảm nhiệm (GBR-10); ở đây
 * chỉ kiểm tra lại session để không phụ thuộc hoàn toàn vào filter (GBR-13).
 */
@WebServlet(urlPatterns = {"/customer/dashboard", "/customer/trang-ca-nhan"})
public class CustomerDashboardServlet extends HttpServlet {

    private static final String VIEW_NAME = "customer/dashboard";

    /** Cờ trong session: màn chuỗi đã hiện trong lần đăng nhập này. */
    private static final String STREAK_INTRO_SHOWN = "streakIntroShown";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final WeatherService weatherService = new WeatherService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        Integer accountId = session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            // GBR-31: hết phiên → đăng nhập lại rồi quay về đúng dashboard
            RequestUtil.redirect(request, response, "/login?returnUrl=/customer/dashboard");
            return;
        }

        ServiceResult<CustomerDashboardView> result = dashboardService.getDashboard(accountId);

        if (!result.isSuccess()) {
            request.setAttribute("dashboardError", result.getMessage());
        } else {
            request.setAttribute("dashboard", result.getData());

            // Service quyết định chuỗi có đủ dài không; Servlet chỉ giữ việc "một lần mỗi lần đăng nhập"
            // (đăng xuất thì session mất, cờ mất theo).
            boolean eligible = result.getData().getAttendance().isStreakIntroEligible();
            boolean notShownYet = session.getAttribute(STREAK_INTRO_SHOWN) == null;
            if (eligible && notShownYet) {
                request.setAttribute("showStreakIntro", true);
                session.setAttribute(STREAK_INTRO_SHOWN, Boolean.TRUE);
            }
        }

        // Token cho nút "Đổi ngày" bắt đầu gói trên thẻ Gói tập (POST kiểm CSRF).
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));
        request.setAttribute("activeSidebar", "dashboard");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page customer-weather-page");

        // Thời tiết là khối phụ; lỗi API không được làm hỏng dashboard khách hàng.
        String previewType = request.getParameter("weather");
        WeatherView weather = weatherService.getPreview(previewType);
        boolean weatherPreview = weather != null;
        if (!weatherPreview) {
            weather = weatherService.getTodayWeather();
        }
        request.setAttribute("weather", weather);
        request.setAttribute("weatherPreview", weatherPreview);
        request.setAttribute("weatherType", weather == null ? "sunny" : weather.getWeatherType());
        RequestUtil.forward(request, response, VIEW_NAME);
    }
}
