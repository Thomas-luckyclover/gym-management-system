package com.gms.servlet.pt;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.PtDashboardView;
import com.gms.dto.ServiceResult;
import com.gms.service.PtDashboardService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * PT Dashboard (#49, UC-32) — /pt/dashboard, màn đầu tiên PT thấy sau khi đăng nhập. Chỉ xem, không sửa dữ liệu.
 *
 * PtAreaFilter đã chặn người không phải PT trước khi tới đây. Servlet chỉ đọc bộ lọc trên URL,
 * gọi PtDashboardService rồi đưa dữ liệu cho JSP.
 *
 * VD URL: /pt/dashboard                              → hoa hồng tháng này, buổi 7 ngày tới tính từ hôm nay
 *         /pt/dashboard?period=2026-Q3               → hoa hồng quý III/2026
 *         /pt/dashboard?date=2026-09-23&days=1       → chỉ các buổi của ngày 23/09/2026
 *         /pt/dashboard?weatherDemo=rain             → xem thử hiệu ứng mưa (storm / rain / hot / cloudy / sunny / night)
 *
 * @author Le Cong Minh
 */
@WebServlet("/pt/dashboard")
public class PtDashboardServlet extends HttpServlet {

    private static final String VIEW = "pt/dashboard";

    private final PtDashboardService ptDashboardService = new PtDashboardService();

    /**
     * Mở dashboard.
     *
     * @param request  có thể có "period" (yyyy-MM / yyyy-Qn), "date" (yyyy-MM-dd), "days" (1 / 3 / 7 / 30), "weatherDemo".
     * @param response trang dashboard, hoặc 403 / 500 khi Service báo lỗi.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: PT nào đang xem → lấy từ SESSION, không bao giờ lấy từ URL (GBR-10)
        HttpSession session = request.getSession(false);
        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        // B2: đọc bộ lọc. Không gửi "date" → null (Service dùng hôm nay); gửi rỗng → Service báo lỗi ô bắt buộc.
        //     Giá trị sai thì Service tự về mặc định (tháng này, hôm nay, 7 ngày)
        String period = RequestUtil.getTrimmedParam(request, "period");
        String date = RequestUtil.getTrimmedParam(request, "date");
        int days = RequestUtil.getIntParam(request, "days", 7);
        String weatherDemo = RequestUtil.getTrimmedParam(request, "weatherDemo");

        // B3: gom dữ liệu. Không phải PT đang làm → 403; lỗi DB → 500
        ServiceResult<PtDashboardView> result = ptDashboardService.getDashboard(accountId, period, date, days, weatherDemo);
        if (!result.isSuccess()) {
            boolean isNoPermission = Messages.MSG07_NO_PERMISSION.equals(result.getMessage());
            response.sendError(isNoPermission
                    ? HttpServletResponse.SC_FORBIDDEN
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // B4: đưa dữ liệu cho JSP. activeNav để menu tô đúng mục "Tổng quan"
        request.setAttribute("dashboard", result.getData());
        request.setAttribute("activeNav", "dashboard");
        RequestUtil.forward(request, response, VIEW);
    }
}
