package com.gms.servlet.receptionist;

import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.dto.PtSaleCustomer;
import com.gms.dto.PtSaleView;
import com.gms.dto.ServiceResult;
import com.gms.service.PtSaleService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SERVLET MÀN LỄ TÂN BÁN GÓI PT — /receptionist/pt-sales (UC-21, bản đơn giản hóa). ReceptionistAreaFilter kiểm quyền.
 *
 *   GET  /receptionist/pt-sales?customer=12&partner=&package=9&pt=5&start=2026-10-26&t1=18:00…&t5=
 *        mỗi lần đổi khách / gói / PT / ngày: Service đi các bước → pt-sales.jsp (t1..t5 = lịch mẫu, trống = Nghỉ)
 *   GET  /receptionist/pt-sales?order=1001&start=…    đang giữ chỗ: đồng hồ 15 phút + thu tiền
 *   GET  /receptionist/pt-sales/customers?q=mai       ô tìm kiếm gợi ý (pt-sales.js gọi) → JSON tối đa 8 khách
 *   POST action=hold     "Giữ lịch 15 phút" + sessions=2026-10-27@09:30,…   → redirect ?order=…
 *   POST action=pay      "Xác nhận đã thu tiền" → toast + trang trống
 *   POST action=release  "Hủy giữ chỗ"        → toast + trang trống
 */
@WebServlet(urlPatterns = {"/receptionist/pt-sales", "/receptionist/pt-sales/customers"})
public class PtSaleServlet extends HttpServlet {

    private static final String VIEW = "receptionist/pt-sales";
    private static final String PAGE_URL = "/receptionist/pt-sales";

    private final PtSaleService saleService = new PtSaleService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }
        if (request.getServletPath().endsWith("/customers")) {
            writeSuggestions(response, accountId, request.getParameter("q"));
            return;
        }
        showPage(request, response, accountId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String action = request.getParameter("action");
        Integer orderId = RequestUtil.getPositiveIntParam(request, "order");
        String start = request.getParameter("start");

        // B1: "Giữ lịch 15 phút" — Service làm lại 4 bước có khóa rồi giữ khung.
        if ("hold".equals(action)) {
            ServiceResult<Integer> result = saleService.hold(accountId, readInput(request), DateTimeUtil.now());
            if (result.isSuccess()) {
                RequestUtil.setFlashToast(request, "success", result.getMessage());
                RequestUtil.redirect(request, response, PAGE_URL + "?order=" + result.getData() + "&start=" + encode(start));
            } else {
                request.setAttribute("saleError", result.getMessage());   // → pt-sales.jsp cột phải
                showPage(request, response, accountId);
            }
            return;
        }

        // B2: thu tiền / hủy giữ — cần mã đơn.
        if (orderId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        ServiceResult<Integer> result = "release".equals(action)
                ? saleService.release(accountId, orderId, DateTimeUtil.now())
                : saleService.pay(accountId, orderId, start, request.getParameter("method"),
                        request.getParameter("reference"), DateTimeUtil.now());
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        boolean backToHold = !result.isSuccess() && !"release".equals(action);
        RequestUtil.redirect(request, response, backToHold
                ? PAGE_URL + "?order=" + orderId + "&start=" + encode(start)
                : PAGE_URL);
    }

    /** HÀM PHỤ — đọc lựa chọn trên URL / form rồi gọi Service.prepare 1 lần → JSP. */
    private void showPage(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws ServletException, IOException {
        Integer orderId = RequestUtil.getPositiveIntParam(request, "order");
        ServiceResult<PtSaleView> result = saleService.prepare(accountId, readInput(request), orderId, DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("sale", result.getData());          // → pt-sales.jsp mọi khối
        } else {
            request.setAttribute("pageError", result.getMessage());  // → pt-sales.jsp khung báo lỗi
        }
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        request.setAttribute("activeNav", "pt-sales");
        RequestUtil.forward(request, response, VIEW);
    }

    /** HÀM PHỤ — tham số chung của GET và POST action=hold. t1..t5 = giờ tập Thứ 2..Thứ 6. */
    private PtSaleService.Input readInput(HttpServletRequest request) {
        PtSaleService.Input input = new PtSaleService.Input();
        input.customer = request.getParameter("customer");
        input.partner = request.getParameter("partner");
        input.packageId = request.getParameter("package");
        input.pt = request.getParameter("pt");
        input.start = request.getParameter("start");
        input.query = request.getParameter("q");
        input.sessions = request.getParameter("sessions");
        input.confirmPt = request.getParameter("confirmPt") != null;   // ô tích "Đã xác nhận với PT"
        for (int i = 0; i < 5; i++) {
            input.times[i] = request.getParameter("t" + (i + 1));
        }
        return input;
    }

    /**
     * HÀM PHỤ — JSON cho ô gợi ý. Chỉ trả đủ để hiện 1 dòng gợi ý, không trả ngày sinh / mã Person.
     * [{id, name, code, phone, note, ok}] — note = "Gói tập tới 21/01/2027" hoặc lý do không bán được.
     */
    private void writeSuggestions(HttpServletResponse response, int accountId, String query) throws IOException {
        ServiceResult<List<PtSaleCustomer>> result = saleService.search(accountId, query, DateTimeUtil.now());
        if (!result.isSuccess()) {
            JsonUtil.writeJson(response, JsonResponse.error(result.getMessage()));
            return;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PtSaleCustomer c : result.getData()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getCustomerId());
            row.put("name", c.getFullName());
            row.put("code", c.getCustomerCode());
            row.put("phone", c.getPhone() == null ? "Hồ sơ phụ thuộc" : c.getPhone());
            row.put("ok", c.getBlockReason() == null);
            row.put("note", c.getBlockReason() == null
                    ? "Gói tập tới " + DateTimeUtil.formatDate(c.getMembershipEnd()) : c.getBlockReason());
            rows.add(row);
        }
        JsonUtil.writeJson(response, JsonResponse.success(rows));
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
