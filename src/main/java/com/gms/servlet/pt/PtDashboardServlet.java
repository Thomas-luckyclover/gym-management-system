package com.gms.servlet.pt;

import com.gms.constant.SessionKey;
import com.gms.dto.PtAttentionView;
import com.gms.dto.ServiceResult;
import com.gms.dto.WeatherView;
import com.gms.service.PtDashboardService;
import com.gms.service.WeatherService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

/**
 * MÀN PT DASHBOARD (Tổng quan PT) — GET /pt/dashboard[?weather=sunny|rain|… để xem thử thời tiết]
 * Chỉ có doGet. Mỗi khối gọi 1 hàm Service riêng; khối nào lỗi chỉ khối đó hiện lỗi (putBlock):
 *   "scheduleDays"      ← PtDashboardService.getSchedule          List<PtScheduleDayView>  (Lịch dạy 7 ngày)
 *   "attentionItems"    ← PtDashboardService.getAttentionItems    List<PtAttentionView>    (ô "Cần chú ý")
 *   "assignedPackages"  ← PtDashboardService.getAssignedPackages  List<PtAssignedPackageView> (Khách phụ trách)
 *   "commissionSummary" ← PtDashboardService.getCommissionSummary PtCommissionSummaryView  (Kết quả tháng)
 *   "weather"           ← WeatherService (khối chào + thời tiết)
 * → forward /WEB-INF/jsp/pt/dashboard.jsp
 */
@WebServlet("/pt/dashboard")
public class PtDashboardServlet extends HttpServlet {
    private final PtDashboardService dashboardService = new PtDashboardService();
    private final WeatherService weatherService = new WeatherService();

    // GET /pt/dashboard: dựng cả màn.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chưa đăng nhập → /login (xong quay lại dashboard)
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/pt/dashboard");
            return;
        }

        // B2: mã PT (PT-xxxx) cho thẻ tài khoản góc phải
        loadEmployeeCode(request, accountId);
        // B3: 4 khối dữ liệu (lịch, cần chú ý, khách phụ trách, hoa hồng)
        loadBlocks(request, accountId);
        // B4: thời tiết hôm nay (hoặc bản xem thử ?weather=)
        loadWeather(request);

        // B5: tô menu + tiêu đề + ngày hôm nay → hiện dashboard.jsp
        setPageInfo(request);
        RequestUtil.forward(request, response, "pt/dashboard");
    }

    /**
     * HÀM PHỤ — AccountId trong session; null = chưa đăng nhập.
     */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /**
     * HÀM PHỤ (doGet B2) — PtDashboardService.getPtEmployeeCode → "ptEmployeeCode".
     */
    private void loadEmployeeCode(HttpServletRequest request, int accountId) {
        ServiceResult<String> employeeCode = dashboardService.getPtEmployeeCode(accountId);
        if (employeeCode.isSuccess()) {
            request.setAttribute("ptEmployeeCode", employeeCode.getData());
        }
    }

    /**
     * HÀM PHỤ (doGet B3) — gọi 4 hàm Service, mỗi kết quả → putBlock (dữ liệu hoặc câu lỗi).
     */
    private void loadBlocks(HttpServletRequest request, int accountId) {
        // B1: lịch 7 ngày → "scheduleDays" | "scheduleError"
        putBlock(request, dashboardService.getSchedule(accountId), "scheduleDays", "scheduleError");

        // B2: việc cần chú ý (14 ngày tới) → "attentionItems" | "attentionError"
        ServiceResult<List<PtAttentionView>> attention = dashboardService.getAttentionItems(accountId);
        putBlock(request, attention, "attentionItems", "attentionError");

        // B3: khách phụ trách, gắn kèm các việc cần chú ý ở B2 → "assignedPackages" | "assignedPackagesError"
        List<PtAttentionView> attentionItems = attention.isSuccess() ? attention.getData() : List.of();
        putBlock(request, dashboardService.getAssignedPackages(accountId, attentionItems),
                "assignedPackages", "assignedPackagesError");

        // B4: kết quả tháng + hoa hồng → "commissionSummary" | "commissionSummaryError"
        putBlock(request, dashboardService.getCommissionSummary(accountId), "commissionSummary", "commissionSummaryError");
    }

    /**
     * HÀM PHỤ (doGet B4) — ?weather= có giá trị hợp lệ → dữ liệu xem thử; không thì thời tiết thật hôm nay.
     * "weather" = WeatherView · "weatherType" = class giao diện (sunny, rain…) · "weatherPreview" = đang xem thử?
     */
    private void loadWeather(HttpServletRequest request) {
        String previewType = request.getParameter("weather");
        WeatherView weather = weatherService.getPreview(previewType);
        boolean isPreview = weather != null;
        if (!isPreview) {
            weather = weatherService.getTodayWeather();
        }

        request.setAttribute("weather", weather);
        request.setAttribute("weatherType", resolveWeatherType(isPreview ? previewType : null, weather));

        request.setAttribute("weatherPreview", isPreview);
    }

    /**
     * HÀM PHỤ (doGet B5) — activeNav = "dashboard" · pageTitle = "Tổng quan PT" · today (ngày trên thanh tiêu đề).
     */
    private void setPageInfo(HttpServletRequest request) {
        request.setAttribute("activeNav", "dashboard");
        request.setAttribute("pageTitle", "Tổng quan PT");
        request.setAttribute("today", DateTimeUtil.today());
    }

    /**
     * HÀM PHỤ (loadBlocks) — thành công → attribute dataName = dữ liệu; lỗi → attribute errorName = câu lỗi.
     */
    private void putBlock(HttpServletRequest request, ServiceResult<?> result, String dataName, String errorName) {
        if (result.isSuccess()) {
            request.setAttribute(dataName, result.getData());
        } else {
            request.setAttribute(errorName, result.getMessage());
        }
    }

    /**
     * HÀM PHỤ (loadWeather) — kiểu thời tiết cho class <body>: giá trị xem thử hợp lệ (extreme_hot → hot) hoặc của dữ liệu thật; null → sunny.
     */
    static String resolveWeatherType(String previewType, WeatherView weather) {
        if (previewType != null) {
            switch (previewType) {
                case "sunny", "cloudy", "rain", "storm", "snow", "wind", "night", "hot":
                    return previewType;
                case "extreme_hot":
                    return "hot";
                default:
                    break;
            }
        }
        return weather == null ? "sunny" : weather.getWeatherType();
    }
}
