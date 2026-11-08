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
 * SERVLET MÀN CHỈ SỐ CƠ THỂ (khách) — /customer/body-stats.
 *
 * VIỆC CỦA SERVLET: đọc tham số → gọi BodyStatsService (mỗi lần 1 hàm) → đặt attribute / toast → forward JSP hoặc redirect (PRG).
 *   Servlet KHÔNG kiểm số đo (khoảng hợp lệ, ngày tương lai…): Service làm hết.
 * TRƯỚC SERVLET: CustomerAreaFilter chặn vai trò khác.
 *
 *   URL / nút                                                     Servlet gọi                       Kết quả
 *   GET  /customer/body-stats?metric=weight|bmi|fat|muscle|waist   doGet → statsService.getPage       forward body-stats.jsp ("stats")
 *   POST action=save    form "Ghi lần đo"                          doPost → statsService.save         toast + redirect (giữ ?metric=)
 *   POST action=delete  nút Xóa trong bảng lịch sử                 doPost → statsService.delete       toast + redirect (giữ ?metric=)
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  doGet    mở trang (5 ô số, biểu đồ 1 chỉ số, lịch sử)
 *   HÀM CHÍNH 2  doPost   Ghi lần đo / Xóa lần đo
 *   DÙNG CHUNG            getAccountId
 */
@WebServlet("/customer/body-stats")
public class BodyStatsServlet extends HttpServlet {
    private static final String VIEW = "customer/body-stats";
    private static final String PAGE_URL = "/customer/body-stats";

    private final BodyStatsService statsService = new BodyStatsService();
    private final CustomerDashboardService dashboardService = new CustomerDashboardService();



    // ============================================================================================================
    // HÀM CHÍNH 1. doGet — mở màn Chỉ số cơ thể (đổi biểu đồ bằng ?metric=)
    //   Ai gọi: menu "Chỉ số cơ thể", 4 ô số / 4 nút chọn biểu đồ trong body-stats.jsp  ·  Gọi xuống: BodyStatsService.getPage
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — mở màn Chỉ số cơ thể (?metric= chọn biểu đồ).
     *   Ai gọi: menu "Chỉ số cơ thể", ô số / nút chọn biểu đồ
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B1: tên + mã khách ở topbar (lỗi thì chỉ không hiện)
        ServiceResult<CustomerDashboardView> header = dashboardService.getDashboard(accountId);
        if (header.isSuccess()) {
            request.setAttribute("dashboard", header.getData());
        }

        // B2: dữ liệu màn: ô số, biểu đồ, lịch sử
        ServiceResult<BodyStatsView> result = statsService.getPage(accountId, request.getParameter("metric"),
                DateTimeUtil.today());
        if (result.isSuccess()) {
            request.setAttribute("stats", result.getData());
        } else {
            request.setAttribute("statsError", result.getMessage());
        }

        // B3: tô menu + CSRF → hiện trang
        request.setAttribute("activeSidebar", "body-stats");
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        RequestUtil.forward(request, response, VIEW);
    }



    // ============================================================================================================
    // HÀM CHÍNH 2. doPost — 2 nút: "Lưu" lần đo (action=save) · "Xóa" lần đo (action=delete)
    //   Ai gọi: form trong body-stats.jsp  ·  Gọi xuống: BodyStatsService.save, delete
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — Lưu hoặc Xóa 1 lần đo.
     *   Ai gọi: form "Ghi lần đo" (action=save) · nút Xóa trong bảng lịch sử (action=delete)
     */
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

        // B1: gọi Lưu hoặc Xóa theo nút đã bấm
        ServiceResult<Void> result;
        if ("delete".equals(request.getParameter("action"))) {
            Integer id = RequestUtil.getPositiveIntParam(request, "id");
            result = id == null ? ServiceResult.fail(Messages.MSG298_MEASUREMENT_NOT_FOUND) : statsService.delete(accountId, id);
        } else {
            result = statsService.save(
                    accountId,
                    request.getParameter("measuredOn"),
                    request.getParameter("weight"),
                    request.getParameter("height"),
                    request.getParameter("fat"),
                    request.getParameter("muscle"),
                    request.getParameter("waist"),
                    request.getParameter("note"),
                    DateTimeUtil.today());
        }

        // B2: báo kết quả, về trang (giữ biểu đồ đang xem)
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        String metric = request.getParameter("metric");
        String back = metric == null || metric.isBlank() ? PAGE_URL : PAGE_URL + "?metric=" + metric.replaceAll("[^a-z]", "");
        RequestUtil.redirect(request, response, back);
    }



    // ============================================================================================================
    // DÙNG CHUNG — đọc AccountId trong session (doGet, doPost)
    // ============================================================================================================

    /** HÀM PHỤ (dùng chung: doGet, doPost) — AccountId trong session; null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
