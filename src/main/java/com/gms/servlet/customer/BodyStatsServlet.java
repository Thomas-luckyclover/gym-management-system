package com.gms.servlet.customer;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.BodyStatsView;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.ServiceResult;
import com.gms.service.BodyStatsService;
import com.gms.service.CustomerDashboardService;
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
 * SERVLET MÀN CHỈ SỐ CƠ THỂ — URL /customer/body-stats (chỉ khách, CustomerAreaFilter kiểm tra trước).
 *
 *   GET  /customer/body-stats?metric=weight|bmi|fat|muscle|waist   mở trang, biểu đồ vẽ chỉ số đang chọn
 *   POST /customer/body-stats  action=save    ghi 1 lần đo (form "Ghi lần đo")
 *                              action=delete  xóa 1 lần đo (nút Xóa trong bảng lịch sử)
 *
 *   doGet   HÀM CHÍNH: getAccountId → loadHeader (tên + mã khách góc phải) → statsService.getPage → JSP
 *   doPost  HÀM CHÍNH: kiểm CSRF → statsService.save / delete (1 lần) → toast → redirect (PRG)
 */
@WebServlet("/customer/body-stats")
public class BodyStatsServlet extends HttpServlet {

    private static final String VIEW = "customer/body-stats";
    private static final String PAGE_URL = "/customer/body-stats";

    private final BodyStatsService statsService = new BodyStatsService();
    private final CustomerDashboardService dashboardService = new CustomerDashboardService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B1: góc phải topbar (chữ viết tắt, tên, mã khách) — lỗi thì chỉ không hiện.
        ServiceResult<CustomerDashboardView> header = dashboardService.getDashboard(accountId);
        if (header.isSuccess()) {
            request.setAttribute("dashboard", header.getData());
        }

        // B2: dữ liệu màn. ?metric= đến từ 4 ô số và 4 nút chọn biểu đồ (body-stats.jsp).
        ServiceResult<BodyStatsView> result = statsService.getPage(accountId, request.getParameter("metric"),
                DateTimeUtil.today());
        if (result.isSuccess()) {
            request.setAttribute("stats", result.getData());
        } else {
            request.setAttribute("statsError", result.getMessage());
        }

        // B3: menu trái + CSRF cho 2 loại form.
        request.setAttribute("activeSidebar", "body-stats");
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        RequestUtil.forward(request, response, VIEW);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // B1: gọi đúng 1 hàm Service theo nút đã bấm.
        ServiceResult<Void> result;
        if ("delete".equals(request.getParameter("action"))) {
            Integer id = RequestUtil.getPositiveIntParam(request, "id");
            result = id == null ? ServiceResult.fail(Messages.MSG298_MEASUREMENT_NOT_FOUND) : statsService.delete(accountId, id);
        } else {
            result = statsService.save(accountId, request.getParameter("measuredOn"), request.getParameter("weight"),
                    request.getParameter("height"), request.getParameter("fat"), request.getParameter("muscle"),
                    request.getParameter("waist"), request.getParameter("note"), DateTimeUtil.today());
        }

        // B2: toast + về trang (giữ biểu đồ đang xem).
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        String metric = request.getParameter("metric");
        String back = metric == null || metric.isBlank() ? PAGE_URL : PAGE_URL + "?metric=" + metric.replaceAll("[^a-z]", "");
        RequestUtil.redirect(request, response, back);
    }

    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
