package com.gms.servlet.customer;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.JsonResponse;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionFilter;
import com.gms.dto.MyPtSessionPage;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDashboardService;
import com.gms.service.MyPtSessionService;
import com.gms.service.PtSessionSwapService;
import com.gms.util.DateTimeUtil;
import com.gms.util.JsonUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Màn "Lịch tập của tôi" — URL: /customer/pt-sessions
 *
 *   GET  (xem trang)          → doGet: nạp dữ liệu các khối rồi forward sang JSP
 *   GET  ?action=swapOptions  → [DOI-BUOI-GET]  trả JSON slot cho hộp thoại Đổi buổi (AJAX)
 *   POST action=cancel        → [HUY-BUOI]      hủy buổi (lý do không bắt buộc)
 *   POST action=swap          → [DOI-BUOI-POST] đổi buổi
 *   POST xong luôn redirect về trang → F5 không gửi lại (Post/Redirect/Get)
 *
 * TÌM NHANH: [GOI-PT] [LOC] [SAP-TOI] [LICH-SU] [PHAN-TRANG] [HUY-BUOI] [DOI-BUOI-GET] [DOI-BUOI-POST] [CSRF] [QUYEN]
 */
@WebServlet("/customer/pt-sessions")
public class MyPtSessionServlet extends HttpServlet {

    private static final String VIEW = "customer/my-pt-sessions";
    private static final String PAGE_URL = "/customer/pt-sessions";

    /** [LOC][PHAN-TRANG] Tên các ô lọc trên form. THÊM Ô LỌC MỚI → thêm tên vào đây (link phân trang tự giữ). */
    private static final String[] FILTER_PARAMS = {"subscriptionId", "type", "timeOfDay", "dateFrom", "dateTo"};

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyPtSessionService sessionService = new MyPtSessionService();
    private final PtSessionSwapService swapService = new PtSessionSwapService();

    // =====================================================================
    // GET
    // =====================================================================

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1 [QUYEN]: phải đăng nhập
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2 [DOI-BUOI-GET]: nếu là AJAX lấy slot đổi buổi → trả JSON rồi dừng
        if ("swapOptions".equals(request.getParameter("action"))) {
            writeSwapOptions(request, response, accountId);
            return;
        }

        // B3: nạp dữ liệu cho từng khối của trang
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession()));
        loadHeader(request, accountId);
        List<MyPtPackageView> packageOptions = loadPackages(request, accountId);
        MyPtSessionFilter filter = loadFilter(request, packageOptions);
        loadSessionBlocks(request, accountId, filter);
        loadLinkQueries(request);

        // B4: chuyển sang JSP
        request.setAttribute("activeSidebar", "pt-sessions");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-pt-sessions-page");
        RequestUtil.forward(request, response, VIEW);
    }

    /** Header: tên + mã khách (dùng chung với Customer Dashboard). */
    private void loadHeader(HttpServletRequest request, int accountId) {
        ServiceResult<CustomerDashboardView> result = dashboardService.getDashboard(accountId);
        if (result.isSuccess()) {
            request.setAttribute("dashboard", result.getData());
        } else {
            request.setAttribute("dashboardError", result.getMessage());
        }
    }

    /**
     * [GOI-PT] Khối "Gói PT của bạn" (gói Active) + danh sách gói cho ô lọc (mọi gói).
     * @return danh sách gói cho ô lọc (null nếu lỗi) để kiểm tra mã gói trên URL
     */
    private List<MyPtPackageView> loadPackages(HttpServletRequest request, int accountId) {
        ServiceResult<List<MyPtPackageView>> packages = sessionService.getPtPackages(accountId);
        if (packages.isSuccess()) {
            request.setAttribute("ptPackages", packages.getData());
        } else {
            request.setAttribute("ptPackagesError", packages.getMessage());
        }

        ServiceResult<List<MyPtPackageView>> options = sessionService.getPtPackageOptions(accountId);
        if (options.isSuccess()) {
            request.setAttribute("ptPackageOptions", options.getData());
            return options.getData();
        }
        request.setAttribute("ptPackageOptionsError", options.getMessage());
        return null;
    }

    /** [LOC] Đọc bộ lọc trên URL. Sai ô nào → báo lỗi ô đó và KHÔNG lọc gì (none). */
    private MyPtSessionFilter loadFilter(HttpServletRequest request, List<MyPtPackageView> packageOptions) {
        ServiceResult<MyPtSessionFilter> result = sessionService.parseFilter(
                request.getParameter("type"),
                request.getParameter("dateFrom"),
                request.getParameter("dateTo"),
                request.getParameter("historyStatus"),
                request.getParameter("subscriptionId"),
                request.getParameter("timeOfDay"),
                packageOptions);

        MyPtSessionFilter filter;
        if (result.isSuccess()) {
            filter = result.getData();
        } else {
            filter = MyPtSessionFilter.none();
            request.setAttribute("filterErrors", result.getFieldErrors());
            request.setAttribute("filterNotApplied", true);
        }
        request.setAttribute("sessionFilter", filter);
        return filter;
    }

    /** [SAP-TOI][LICH-SU][PHAN-TRANG] Khối "Sắp tới" và "Lịch sử": mỗi khối 1 trang riêng. */
    private void loadSessionBlocks(HttpServletRequest request, int accountId, MyPtSessionFilter filter) {
        // Lấy giờ ĐÚNG 1 LẦN để 2 khối chia buổi theo cùng 1 thời điểm (không buổi nào lọt giữa 2 khối)
        LocalDateTime now = DateTimeUtil.now();
        request.setAttribute("todayDate", now.toLocalDate());

        int upcomingPageNumber = sessionService.parsePageNumber(request.getParameter("upcomingPage"));
        ServiceResult<MyPtSessionPage> upcoming =
                sessionService.getUpcomingPage(accountId, filter, now, upcomingPageNumber);
        if (upcoming.isSuccess()) {
            request.setAttribute("upcomingPage", upcoming.getData());
        } else {
            request.setAttribute("upcomingSessionsError", upcoming.getMessage());
        }

        int historyPageNumber = sessionService.parsePageNumber(request.getParameter("historyPage"));
        ServiceResult<MyPtSessionPage> history =
                sessionService.getHistoryPage(accountId, filter, now, historyPageNumber);
        if (history.isSuccess()) {
            request.setAttribute("historyPage", history.getData());
        } else {
            request.setAttribute("historySessionsError", history.getMessage());
        }
    }

    /**
     * [PHAN-TRANG][LOC] Chuỗi tham số để JSP gắn vào link phân trang / tab trạng thái, giữ nguyên bộ lọc đang chọn.
     *   filterQuery = "type=PTSingle&timeOfDay=morning&"          (chỉ bộ lọc chung)
     *   pagingQuery = filterQuery + "historyStatus=missed&"       (thêm tab trạng thái)
     * Mỗi cặp kết thúc bằng "&" để JSP nối tiếp tham số khác phía sau.
     */
    private void loadLinkQueries(HttpServletRequest request) {
        StringBuilder filterQuery = new StringBuilder();
        for (String name : FILTER_PARAMS) {
            appendParam(filterQuery, name, request.getParameter(name));
        }
        StringBuilder pagingQuery = new StringBuilder(filterQuery);
        appendParam(pagingQuery, "historyStatus", request.getParameter("historyStatus"));

        request.setAttribute("filterQuery", filterQuery.toString());
        request.setAttribute("pagingQuery", pagingQuery.toString());
    }

    private void appendParam(StringBuilder query, String name, String value) {
        if (value != null && !value.isBlank()) {
            query.append(name).append('=')
                    .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8))
                    .append('&');
        }
    }

    /** [DOI-BUOI-GET] AJAX: trả JSON { success, message, data: { slots, remainingSwaps, ptName, originalTimeSlotId } }. */
    private void writeSwapOptions(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {
        Integer sessionId = RequestUtil.getPositiveIntParam(request, "sessionId");
        if (sessionId == null) {
            JsonUtil.writeJson(response, JsonResponse.error(Messages.MSG108_INVALID_OPTION));
            return;
        }
        JsonUtil.writeJson(response, JsonResponse.from(swapService.getOptions(accountId, sessionId)));
    }

    // =====================================================================
    // POST: hủy buổi / đổi buổi
    // =====================================================================

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1 [QUYEN]: phải đăng nhập
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2 [CSRF]: chống giả mạo request: token gửi lên phải khớp token trong session
        if (!RequestUtil.isValidCsrfToken(request)) {
            redirectWithToast(request, response, false, Messages.MSG07_NO_PERMISSION, PAGE_URL);
            return;
        }

        // B3: mã buổi phải là số nguyên dương
        Integer sessionId = RequestUtil.getPositiveIntParam(request, "sessionId");
        if (sessionId == null) {
            redirectWithToast(request, response, false, Messages.MSG08_NOT_FOUND.formatted("buổi tập"), PAGE_URL);
            return;
        }

        // B4: làm theo action
        String action = request.getParameter("action");
        if ("cancel".equals(action)) {
            // [HUY-BUOI] lý do hủy KHÔNG bắt buộc — Service chỉ chặn quá 500 ký tự
            String reason = request.getParameter("cancelReason");
            ServiceResult<Void> result = sessionService.cancelPtSession(accountId, sessionId, reason);
            redirectWithToast(request, response, result.isSuccess(), result.getMessage(), PAGE_URL);

        } else if ("swap".equals(action)) {
            // [DOI-BUOI-POST]
            Integer targetSlotId = RequestUtil.getPositiveIntParam(request, "targetTimeSlotId");
            Integer originalSlotId = RequestUtil.getPositiveIntParam(request, "originalTimeSlotId");
            ServiceResult<Void> result = (targetSlotId == null || originalSlotId == null)
                    ? ServiceResult.fail(Messages.MSG108_INVALID_OPTION)
                    : swapService.swapSession(accountId, sessionId, targetSlotId, originalSlotId);
            redirectWithToast(request, response, result.isSuccess(), result.getMessage(), PAGE_URL + "?view=upcoming");

        } else {
            redirectWithToast(request, response, false, Messages.MSG108_INVALID_OPTION, PAGE_URL);
        }
    }

    // =====================================================================
    // HÀM PHỤ
    // =====================================================================

    /** accountId trong HttpSession (đặt lúc đăng nhập). null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /** Lưu toast (xanh/đỏ) rồi redirect — trang sau hiện toast. */
    private void redirectWithToast(HttpServletRequest request, HttpServletResponse response,
            boolean success, String message, String path) throws IOException {
        RequestUtil.setFlashToast(request, success ? "success" : "error", message);
        RequestUtil.redirect(request, response, path);
    }
}
