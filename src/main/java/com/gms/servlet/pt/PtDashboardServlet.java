package com.gms.servlet.pt;

import com.gms.constant.SessionKey;
import com.gms.dto.PtAssignedPackageView;
import com.gms.dto.PtAttentionView;
import com.gms.dto.PtCommissionSummaryView;
import com.gms.dto.PtScheduleDayView;
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

/** Điều phối trang tổng quan của PT (UC-32). */
@WebServlet("/pt/dashboard")
public class PtDashboardServlet extends HttpServlet {

    private static final String VIEW = "pt/dashboard"; // JSP đích, RequestUtil tự thêm đường dẫn và đuôi .jsp.

    private final PtDashboardService dashboardService = new PtDashboardService(); // Service trả dữ liệu cho từng khối dashboard.
    private final WeatherService weatherService = new WeatherService(); // Một instance giữ cache dùng chung giữa các request.

    /**
     * Lấy lịch, cảnh báo, khách phụ trách và kết quả tháng qua Service rồi chuyển sang JSP.
     * PtAreaFilter kiểm tra vai trò PT; mỗi khối có attribute dữ liệu và lỗi riêng.
     * @param request yêu cầu mở trang của PT đang đăng nhập
     * @param response phản hồi trả về trình duyệt
     * @throws ServletException khi không chuyển được yêu cầu tới JSP
     * @throws IOException khi không gửi được phản hồi
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: lấy mã tài khoản từ session, không đọc từ URL.
        HttpSession session = request.getSession(false);  // false = nếu ko có => null, ko tạo mới
        Integer accountId = null; // Integer có thể null, int thì không
        if (session != null) {
            accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        }
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/pt/dashboard");
            return;
        }

        // EmployeeDAO đọc Employees.EmployeeCode qua Service; JSP hiện dưới tên PT ở đầu trang.
        ServiceResult<String> employeeCodeResult = dashboardService.getPtEmployeeCode(accountId);
        if (employeeCodeResult.isSuccess()) {
            request.setAttribute("ptEmployeeCode", employeeCodeResult.getData());
        }

        // B2: gọi Service lấy lịch 7 ngày của tài khoản này.
        ServiceResult<List<PtScheduleDayView>> scheduleResult = dashboardService.getSchedule(accountId);

        // B3: chuyển lịch hoặc thông báo lỗi sang JSP.
        if (scheduleResult.isSuccess()) {
            // B3.1: JSP đọc scheduleDays để lặp ngày rồi từng buổi.
            request.setAttribute("scheduleDays", scheduleResult.getData());
        } else {
            // B3.2: JSP đọc scheduleError; không coi lỗi là không có lịch.
            request.setAttribute("scheduleError", scheduleResult.getMessage());
        }

        // B4: gọi Service lấy các cảnh báo cho buổi tập trong 14 ngày của tài khoản này.
        // attentionResult chứa List<PtAttentionView> do Service tạo, hoặc thông báo lỗi.
        ServiceResult<List<PtAttentionView>> attentionResult = dashboardService.getAttentionItems(accountId);

        // B5: chuyển danh sách cảnh báo hoặc lỗi của khối Cần chú ý sang JSP.
        if (attentionResult.isSuccess()) {
            // B5.1: JSP sẽ lặp attentionItems; mỗi PtAttentionView hiện thành một dòng cảnh báo.
            // Ví dụ: Nguyễn Văn A — Cần membership — Cần membership hợp lệ cho buổi ngày 12/10/2026.
            // Danh sách rỗng nghĩa là không có cảnh báo trong phạm vi 14 ngày.
            request.setAttribute("attentionItems", attentionResult.getData());
        } else {
            // B5.2: JSP sẽ hiện attentionError; lỗi lấy dữ liệu không có nghĩa là không có cảnh báo.
            request.setAttribute("attentionError", attentionResult.getMessage());
        }

        // B6: gọi Service lấy đầy đủ gói PT Active và buổi lẻ sắp dạy của tài khoản này.
        // assignedPackagesResult chứa DTO do DAO tạo, đã được Service bổ sung nhãn loại gói.
        ServiceResult<List<PtAssignedPackageView>> assignedPackagesResult =
                dashboardService.getAssignedPackages(accountId);

        // B7: chuyển danh sách hoặc lỗi của riêng khối Khách phụ trách sang JSP.
        if (assignedPackagesResult.isSuccess()) {
            // B7.1: JSP sẽ lặp assignedPackages; mỗi DTO hiện một gói PT hoặc một buổi lẻ.
            // Ví dụ: Nguyễn Văn A — PT cá nhân — Gói PT đến 25/10/2026 — Còn 6 buổi trong lịch.
            // Một khách có hai gói phù hợp thì có hai dòng; list rỗng nghĩa là không có gói/buổi phù hợp.
            request.setAttribute("assignedPackages", assignedPackagesResult.getData());
        } else {
            // B7.2: JSP sẽ hiện assignedPackagesError; không biến lỗi lấy dữ liệu thành danh sách rỗng.
            request.setAttribute("assignedPackagesError", assignedPackagesResult.getMessage());
        }

        // B8: lấy một DTO tổng hợp tháng; DAO điền số/tiền, Service tính tỷ lệ.
        ServiceResult<PtCommissionSummaryView> commissionResult =
                dashboardService.getCommissionSummary(accountId);

        // B9: JSP dùng commissionSummary cho ba nhóm; lỗi đọc nằm ở attribute riêng.
        if (commissionResult.isSuccess()) {
            request.setAttribute("commissionSummary", commissionResult.getData());
        } else {
            request.setAttribute("commissionSummaryError", commissionResult.getMessage());
        }

        // B10: token CSRF cho nút "Nhắc khách" (form POST) trong khối Cần chú ý.
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));

        // Thời tiết là dữ liệu phụ: API lỗi vẫn mở dashboard; nền dùng sunny, thẻ ẩn.
        String previewType = request.getParameter("weather");
        WeatherView weather = weatherService.getPreview(previewType);
        boolean weatherPreview = weather != null;
        if (!weatherPreview) {
            weather = weatherService.getTodayWeather();
        }
        request.setAttribute("weather", weather);
        request.setAttribute("weatherType", resolveWeatherType(weatherPreview ? previewType : null, weather));
        request.setAttribute("weatherPreview", weatherPreview);

        // B11: chọn mục Tổng quan, đặt tiêu đề và ngày hiện tại.
        request.setAttribute("activeNav", "dashboard");
        request.setAttribute("pageTitle", "Tổng quan PT");
        request.setAttribute("today", DateTimeUtil.today());

        // B12: mở JSP sau khi đã đặt dữ liệu hoặc lỗi riêng của từng khối.
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * POST /pt/dashboard — nút "Nhắc khách" ở khối Cần chú ý: gửi thông báo tới khách rồi quay lại trang.
     * Form gửi: csrfToken, customerId, subscriptionId. Service kiểm tra lại khách có thuộc PT này không.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // B0: chặn CSRF trước khi làm bất cứ việc gì
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // B1: phải đăng nhập (mã tài khoản lấy từ session)
        HttpSession session = request.getSession(false);
        Integer accountId = session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/pt/dashboard");
            return;
        }

        // B2: gửi nhắc; mã sai/thiếu → 0 → Service báo "không còn cần nhắc"
        Integer customerId = RequestUtil.getPositiveIntParam(request, "customerId");
        Integer subscriptionId = RequestUtil.getPositiveIntParam(request, "subscriptionId");
        ServiceResult<Void> result = dashboardService.remindCustomer(accountId,
                customerId == null ? 0 : customerId, subscriptionId == null ? 0 : subscriptionId);

        // B3: báo kết quả bằng toast, quay về đúng khối Cần chú ý (POST → redirect → GET, F5 không gửi lại)
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        RequestUtil.redirect(request, response, "/pt/dashboard#alerts-title");
    }

    /** Chỉ nhận các tên CSS cố định, tránh đưa chuỗi tùy ý từ URL vào class HTML. */
    static String resolveWeatherType(String previewType, WeatherView weather) {
        if (previewType != null) {
            switch (previewType) {
                case "sunny":
                case "cloudy":
                case "rain":
                case "storm":
                case "snow":
                case "wind":
                case "night":
                case "hot":
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
