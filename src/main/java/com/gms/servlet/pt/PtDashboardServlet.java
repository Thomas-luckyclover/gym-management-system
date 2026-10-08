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
 * Servlet Dashboard PT (URL /pt/dashboard) — màn đầu sau đăng nhập.
 *
 * <p>Hỗ trợ bộ lọc: period (yyyy-MM / yyyy-Qn), date, days, weatherDemo; chỉ xem, không sửa dữ liệu.</p>
 */
@WebServlet("/pt/dashboard")
public class PtDashboardServlet extends HttpServlet {

    private static final String VIEW = "pt/dashboard";

    private final PtDashboardService ptDashboardService = new PtDashboardService();

    /**
     * GET /pt/dashboard — nạp dữ liệu dashboard của PT đang đăng nhập rồi trả về JSP.
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
