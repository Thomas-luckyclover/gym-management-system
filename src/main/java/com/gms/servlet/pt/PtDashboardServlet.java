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
 * TRANG TỔNG QUAN PT — GET /pt/dashboard (UC-32). Chỉ xem, không có form POST.
 *
 *   doGet               HÀM CHÍNH: kiểm tra đăng nhập → các hàm phụ đặt attribute → JSP pt/dashboard.jsp
 *   getAccountId        HÀM PHỤ: đọc tài khoản đang đăng nhập từ session
 *   loadEmployeeCode    HÀM PHỤ: mã NV-xxxx dưới tên PT
 *   loadBlocks          HÀM PHỤ: gọi Service 4 lần cho 4 khối (lịch, cảnh báo, khách, hoa hồng)
 *   loadWeather         HÀM PHỤ: thời tiết cho khối chào
 *   setPageInfo         HÀM PHỤ: menu đang chọn, tiêu đề, hôm nay
 *   putBlock            HÀM PHỤ: đặt dữ liệu HOẶC lỗi của 1 khối lên request
 *   resolveWeatherType  HÀM PHỤ: chọn tên nền thời tiết an toàn cho class CSS
 *
 * PtAreaFilter đã chặn người không phải PT trước khi vào đây.
 * Nhắc khách thiếu membership: không còn nút bấm, JOB-08 (RemindMissingGymAccessJob) tự gửi trước buổi 1 ngày.
 */
@WebServlet("/pt/dashboard")
public class PtDashboardServlet extends HttpServlet {

    private final PtDashboardService dashboardService = new PtDashboardService();
    private final WeatherService weatherService = new WeatherService(); // giữ cache thời tiết dùng chung giữa các request


    /**
     * HÀM CHÍNH — trình duyệt gọi khi PT mở /pt/dashboard (hoặc bấm "Tổng quan" ở menu).
     *
     * Mỗi khối có 2 attribute: dữ liệu (vd scheduleDays) hoặc lỗi (vd scheduleError).
     * Một khối lỗi → chỉ khối đó hiện lỗi, các khối khác vẫn hiện bình thường.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: lấy accountId từ session (không bao giờ đọc từ URL). Chưa đăng nhập → về trang login.
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/pt/dashboard");
            return;
        }

        // B2: mã nhân viên, 4 khối dữ liệu, thời tiết.
        loadEmployeeCode(request, accountId);
        loadBlocks(request, accountId);
        loadWeather(request);

        // B3: thông tin chung của trang rồi mở JSP.
        setPageInfo(request);
        RequestUtil.forward(request, response, "pt/dashboard");
    }

    /** HÀM PHỤ — gọi ở B1 của doGet. accountId lưu trong session lúc đăng nhập; null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /** HÀM PHỤ — gọi ở B2 của doGet. Mã nhân viên dưới tên PT ở đầu trang (lỗi thì chỉ không hiện mã). */
    private void loadEmployeeCode(HttpServletRequest request, int accountId) {
        ServiceResult<String> employeeCode = dashboardService.getPtEmployeeCode(accountId);
        if (employeeCode.isSuccess()) {
            // → _header.jspf dòng 69–71 "NV-0005" dưới tên PT (jspf nhúng ở dashboard.jsp dòng 60)
            request.setAttribute("ptEmployeeCode", employeeCode.getData());
        }
    }

    /**
     * HÀM PHỤ — gọi ở B2 của doGet. Gọi Service 4 lần, mỗi lần 1 khối, đúng thứ tự dưới đây.
     * getAttentionItems phải chạy TRƯỚC getAssignedPackages vì thẻ khách cần cảnh báo để gắn vào.
     */
    private void loadBlocks(HttpServletRequest request, int accountId) {
        // B1: khối "Lịch 7 ngày".
        //   "scheduleDays"  → dashboard.jsp dòng 71 (đếm "Buổi hôm nay"), 119–146 (dải 7 ngày + bảng từng ngày)
        //   "scheduleError" → dashboard.jsp dòng 88 (ô đếm hiện "—"), 116–117 (khung đỏ)
        putBlock(request, dashboardService.getSchedule(accountId), "scheduleDays", "scheduleError");

        // B2: cảnh báo 14 ngày. Không có khối riêng — được gắn vào thẻ khách ở B3.
        //   "attentionItems" → dashboard.jsp dòng 92 (ô đếm "Cần chú ý")
        //   "attentionError" → dashboard.jsp dòng 92 (ô đếm hiện "—"), 211 (khung đỏ trên khối khách)
        ServiceResult<List<PtAttentionView>> attention = dashboardService.getAttentionItems(accountId);
        putBlock(request, attention, "attentionItems", "attentionError");

        // B3: khối "Khách phụ trách" — cần cảnh báo ở B2 (lỗi B2 thì truyền list rỗng).
        //   "assignedPackages"      → dashboard.jsp dòng 215–219 (lặp thẻ khách); khung vàng client.attentionItems dòng 263
        //   "assignedPackagesError" → dashboard.jsp dòng 214 (khung đỏ)
        List<PtAttentionView> attentionItems = attention.isSuccess() ? attention.getData() : List.of();
        putBlock(request, dashboardService.getAssignedPackages(accountId, attentionItems),
                "assignedPackages", "assignedPackagesError");

        // B4: khối "Kết quả tháng và hoa hồng".
        //   "commissionSummary"      → dashboard.jsp dòng 308–312 (c:set cs = commissionSummary, rồi dùng cs.* bên dưới)
        //   "commissionSummaryError" → dashboard.jsp dòng 305–306 (khung đỏ)
        putBlock(request, dashboardService.getCommissionSummary(accountId), "commissionSummary", "commissionSummaryError");
    }

    /**
     * HÀM PHỤ — gọi ở B2 của doGet. Thời tiết cho khối chào.
     * Dữ liệu phụ: API lỗi vẫn mở trang (nền sunny, ẩn thẻ). ?weather=rain dùng để xem trước giao diện từng kiểu.
     */
    private void loadWeather(HttpServletRequest request) {
        // ?weather= KHÔNG có JSP nào gửi: chỉ gõ tay trên URL để xem trước giao diện (VD /pt/dashboard?weather=rain).
        String previewType = request.getParameter("weather");
        WeatherView weather = weatherService.getPreview(previewType);
        boolean isPreview = weather != null;
        if (!isPreview) {
            weather = weatherService.getTodayWeather();
        }
        // 3 attribute dưới đều đọc trong dashboard-weather.jspf (nhúng ở dashboard.jsp dòng 83):
        request.setAttribute("weather", weather);            // → dashboard-weather.jspf dòng 6, 22–70 (thẻ thời tiết), 89, 97
        request.setAttribute("weatherType", resolveWeatherType(isPreview ? previewType : null, weather));
                                                             // → dashboard.jsp dòng 52 (<body class="weather-…">);
                                                             //   dashboard-weather.jspf dòng 23, 45, 79–85 (icon + lời khuyên)
        request.setAttribute("weatherPreview", isPreview);   // → dashboard-weather.jspf dòng 32 nhãn "Xem trước"
    }

    /** HÀM PHỤ — gọi ở B3 của doGet. Menu trái, tiêu đề topbar, ngày hôm nay. */
    private void setPageInfo(HttpServletRequest request) {
        request.setAttribute("activeNav", "dashboard");       // → layout-pt.jspf dòng 55–56 tô mục "Tổng quan" (nhúng ở dashboard.jsp dòng 57)
        request.setAttribute("pageTitle", "Tổng quan PT");    // → dashboard.jsp dòng 42 (<title>); _header.jspf dòng 25, 31 (nhúng ở dashboard.jsp dòng 60)
        request.setAttribute("today", DateTimeUtil.today());  // → _header.jspf dòng 38 ngày hôm nay ở topbar
    }

    /**
     * HÀM PHỤ — gọi ở B1–B4 của loadBlocks.
     * Thành công → request.setAttribute(dataName, dữ liệu). Thất bại → request.setAttribute(errorName, câu báo lỗi).
     *
     * Ví dụ: putBlock(request, result, "scheduleDays", "scheduleError")
     *        → JSP kiểm tra ${not empty scheduleError} trước, không có lỗi mới lặp ${scheduleDays}.
     */
    private void putBlock(HttpServletRequest request, ServiceResult<?> result, String dataName, String errorName) {
        if (result.isSuccess()) {
            request.setAttribute(dataName, result.getData());
        } else {
            request.setAttribute(errorName, result.getMessage());
        }
    }

    /**
     * HÀM PHỤ — gọi trong loadWeather.
     * Chuỗi trên URL chỉ được nhận nếu nằm trong danh sách cố định → không đưa chữ tùy ý vào class HTML.
     * Không có / không hợp lệ → dùng thời tiết thật (thiếu luôn thì "sunny").
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
