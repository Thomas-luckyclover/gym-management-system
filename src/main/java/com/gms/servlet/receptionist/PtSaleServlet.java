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
 * SERVLET MÀN LỄ TÂN BÁN GÓI PT — /receptionist/pt-sales (UC-21 bản đơn giản hóa).
 *
 * VIỆC CỦA SERVLET: đọc tham số → gọi PtSaleService → đặt attribute / toast → forward JSP hoặc redirect.
 *   Servlet KHÔNG kiểm luật nghiệp vụ (tuổi, gói thành viên, trùng giờ, ca…): Service làm hết.
 * TRƯỚC SERVLET: ReceptionistAreaFilter chặn vai trò khác (403) và đặt "shiftNotice" (thanh cảnh báo ca).
 *
 *   URL / nút                                               Servlet gọi                          Kết quả
 *   GET  /receptionist/pt-sales?package=&pt=&start=&t1..t5   showPage → saleService.prepare       forward pt-sales.jsp (attribute "sale")
 *   GET  /receptionist/pt-sales?order=1001                   showPage → saleService.prepare       trang đang giữ: đồng hồ + thu tiền
 *   GET  /receptionist/pt-sales/customers?q=mai              writeSuggestions → saleService.search JSON mọi khách khớp (pt-sales.js)
 *   POST action=hold     "Giữ lịch 15 phút"                  saleService.hold                     redirect ?order=… / chữ đỏ "saleError"
 *   POST action=pay      "Xác nhận đã thu tiền"              saleService.pay                      toast + redirect trang trống / ?order=
 *   POST action=release  "Hủy giữ chỗ"                       saleService.release                  toast + redirect trang trống
 */
@WebServlet(urlPatterns = {"/receptionist/pt-sales", "/receptionist/pt-sales/customers"})
public class PtSaleServlet extends HttpServlet {

    private static final String VIEW = "receptionist/pt-sales";
    private static final String PAGE_URL = "/receptionist/pt-sales";

    private final PtSaleService saleService = new PtSaleService();


    // ============================================================================================================
    // HÀM CHÍNH 1. doGet — mở màn (GET /receptionist/pt-sales) hoặc ô gợi ý khách (GET …/customers?q=)
    //   Hàm phụ ngay dưới: writeSuggestions (JSON gợi ý) · showPage (dựng trang) · readInput (đọc tham số)
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — mọi GET của màn.
     *   1. accountId = SessionKey.ACCOUNT_ID trong session; null (hết phiên) → /login?returnUrl=…
     *   2. đường dẫn kết thúc "/customers" → writeSuggestions (JSON, không forward JSP)
     *   3. còn lại → showPage (gọi prepare 1 lần → pt-sales.jsp)
     */
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

    /**
     * HÀM PHỤ — JSON cho ô gợi ý. Chỉ trả đủ để hiện 1 dòng gợi ý, không trả ngày sinh / mã Person.
     * [{id, name, code, phone, note, ok}] — note = "Gói tập tới 21/01/2027" hoặc lý do không bán được.
     */
    private void writeSuggestions(HttpServletResponse response, int accountId, String query) throws IOException {
        ServiceResult<List<PtSaleCustomer>> result = saleService.search(accountId, query, DateTimeUtil.now());
        // Nếu ko trả về kết quả thành công => Báo lỗi dạng JSON
        if (!result.isSuccess()) {
            JsonUtil.writeJson(response, JsonResponse.error(result.getMessage()));
            return;
        }
        
        
        // Nếu thành công, mapping giá trị trả về gắn vào Object rows, chuyển về dạng JSON
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

    /**
     * HÀM PHỤ (doGet; doPost khi Giữ lịch bị từ chối) — dựng trang.
     *   Gọi saleService.prepare ĐÚNG 1 lần, rồi đặt attribute cho JSP:
     *     "sale"       PtSaleView — mọi khối của pt-sales.jsp đọc từ đây
     *     "pageError"  prepare lỗi (MSG07 / MSG04) → khung đỏ cả trang
     *     "csrfToken"  ô ẩn của 3 form POST · "activeNav" tô menu
     *   Mở ?order= mà đơn hết giữ → không vẽ trang, toast vàng + redirect trang trống.
     */
    private void showPage(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws ServletException, IOException {
        // URL có orderID trong 3 trường hợp: Lễ tân bấm giữ lịch 15', lễ tân bấm Tiếp tục thu tiền, Lễ tân bấm Xác nhận thu tiền
        Integer orderId = RequestUtil.getPositiveIntParam(request, "order");
        
        
        ServiceResult<PtSaleView> result = saleService.prepare(
                accountId,
                readInput(request),
                orderId,
                DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("sale", result.getData());          // → pt-sales.jsp mọi khối
        } else if (orderId != null) {
            // Mở ?order=<id> mà đơn không còn giữ (đã thu tiền / hủy / quá hạn) → về thẳng trang bán gói + toast,
            // không hiện khung lỗi (lễ tân bán lại từ đầu).
            RequestUtil.setFlashToast(request, "warning", result.getMessage());
            RequestUtil.redirect(request, response, PAGE_URL);
            return;
        } else {
            request.setAttribute("pageError", result.getMessage());  // → pt-sales.jsp khung báo lỗi
        }
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        request.setAttribute("activeNav", "pt-sales");
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * HÀM PHỤ (showPage, doPost hold) — gom tham số URL / form thành PtSaleService.Input (CHỮ THÔ, chưa kiểm).
     *   customer, partner, package, pt, start, q · t1..t5 = giờ lịch mẫu Thứ 2..Thứ 6 (trống = Nghỉ)
     *   sessions = "2026-11-03@09:30,…" (ô ẩn form Giữ lịch) · confirmPt = có tích "Đã xác nhận với PT"
     */
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


    // ============================================================================================================
    // HÀM CHÍNH 2. doPost — 3 nút: "Giữ lịch 15 phút" (hold) · "Xác nhận đã thu tiền" (pay) · "Hủy giữ chỗ" (release)
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — 3 nút POST của màn (ô ẩn "action" trong form JSP quyết định nút nào).
     *   1. hết phiên → /login · sai csrfToken → 403
     *   2. action=hold    → saleService.hold(accountId, readInput, now)
     *        được → toast xanh MSG307 + redirect ?order=<mã đơn>&start=…   (trang đang giữ)
     *        bị từ chối → attribute "saleError" + showPage (vẽ lại trang, giữ nguyên lựa chọn, chữ đỏ ở cột phải)
     *   3. action=pay / release cần ô ẩn "order" (thiếu → 400)
     *        pay     → saleService.pay(accountId, orderId, start, method, reference, receivedInFull, now)
     *        release → saleService.release(accountId, orderId, now)
     *        → toast; pay thất bại thì quay lại ?order= để thử lại, còn lại về trang trống
     */
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
        
        
        // B3: nếu action = release => release, còn ko thì pay
        ServiceResult<Integer> result = "release".equals(action)
                ? saleService.release(accountId, orderId, DateTimeUtil.now())
                : saleService.pay(accountId, orderId, start, request.getParameter("method"),
                        request.getParameter("reference"), "1".equals(request.getParameter("receivedInFull")),
                        DateTimeUtil.now());
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        
        
        boolean backToHold = !result.isSuccess() && !"release".equals(action);
        RequestUtil.redirect(request, response, backToHold
                ? PAGE_URL + "?order=" + orderId + "&start=" + encode(start)
                : PAGE_URL);
    }


    // ============================================================================================================
    // DÙNG CHUNG
    // ============================================================================================================

    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
