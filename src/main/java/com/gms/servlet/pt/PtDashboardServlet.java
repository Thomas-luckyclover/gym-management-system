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
 *   doGet               mở trang: gọi Service 5 lần (5 khối) + lấy thời tiết → JSP pt/dashboard.jsp
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
        HttpSession session = request.getSession(false);
        Integer accountId = session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/pt/dashboard");
            return;
        }

        // B2: mã nhân viên dưới tên PT ở đầu trang (lỗi thì chỉ không hiện mã).
        ServiceResult<String> employeeCode = dashboardService.getPtEmployeeCode(accountId);
        if (employeeCode.isSuccess()) {
            request.setAttribute("ptEmployeeCode", employeeCode.getData());
        }

        // B3: khối "Lịch 7 ngày".
        putBlock(request, dashboardService.getSchedule(accountId), "scheduleDays", "scheduleError");

        // B4: cảnh báo 14 ngày. Không có khối riêng — được gắn vào thẻ khách ở B5.
        ServiceResult<List<PtAttentionView>> attention = dashboardService.getAttentionItems(accountId);
        putBlock(request, attention, "attentionItems", "attentionError");

        // B5: khối "Khách phụ trách" — cần cảnh báo ở B4 (lỗi B4 thì truyền list rỗng).
        List<PtAttentionView> attentionItems = attention.isSuccess() ? attention.getData() : List.of();
        putBlock(request, dashboardService.getAssignedPackages(accountId, attentionItems),
                "assignedPackages", "assignedPackagesError");

        // B6: khối "Kết quả tháng và hoa hồng".
        putBlock(request, dashboardService.getCommissionSummary(accountId), "commissionSummary", "commissionSummaryError");

        // B7: thời tiết cho khối chào. Dữ liệu phụ: API lỗi vẫn mở trang (nền sunny, ẩn thẻ).
        //     ?weather=rain dùng để xem trước giao diện từng kiểu thời tiết.
        String previewType = request.getParameter("weather");
        WeatherView weather = weatherService.getPreview(previewType);
        boolean isPreview = weather != null;
        if (!isPreview) {
            weather = weatherService.getTodayWeather();
        }
        request.setAttribute("weather", weather);
        request.setAttribute("weatherType", resolveWeatherType(isPreview ? previewType : null, weather));
        request.setAttribute("weatherPreview", isPreview);

        // B8: thông tin chung của trang rồi mở JSP.
        request.setAttribute("activeNav", "dashboard");
        request.setAttribute("pageTitle", "Tổng quan PT");
        request.setAttribute("today", DateTimeUtil.today());
        RequestUtil.forward(request, response, "pt/dashboard");
    }

    /**
     * HÀM PHỤ — gọi ở B3, B4, B5, B6 của doGet.
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
     * HÀM PHỤ — gọi ở B7 của doGet.
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
