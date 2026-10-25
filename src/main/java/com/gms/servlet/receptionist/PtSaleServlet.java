package com.gms.servlet.receptionist;

import com.gms.constant.SessionKey;
import com.gms.dto.PtSaleView;
import com.gms.dto.ServiceResult;
import com.gms.service.PtSaleService;
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
 * SERVLET MÀN LỄ TÂN BÁN GÓI PT — URL /receptionist/pt-sales (UC-21, bản đơn giản hóa).
 *
 *   GET  /receptionist/pt-sales?q=KH-0001&partner=&package=9&pt=5&start=2026-10-26&time=18:00
 *        mỗi lần bấm "Tiếp": Service đi 4 bước (Khách → Gói/PT/ngày → Giờ cố định → Lịch dự kiến) → pt-sales.jsp
 *   POST /receptionist/pt-sales   (cùng 6 tham số + method, reference, csrfToken)
 *        bấm "Xác nhận đã thu tiền": Service bán trong 1 transaction → toast + về trang trống (PRG)
 *
 *   doGet   HÀM CHÍNH: getAccountId → showPage → JSP
 *   doPost  HÀM CHÍNH: getAccountId → kiểm CSRF → saleService.sell → redirect; lỗi thì showPage + câu lỗi
 *
 * Quyền Lễ tân do ReceptionistAreaFilter (@WebFilter "/receptionist/*") kiểm tra trước.
 */
@WebServlet("/receptionist/pt-sales")
public class PtSaleServlet extends HttpServlet {

    private static final String VIEW = "receptionist/pt-sales";
    private static final String PAGE_URL = "/receptionist/pt-sales";

    private final PtSaleService saleService = new PtSaleService();

    /** HÀM CHÍNH — mở trang hoặc bấm "Tiếp" ở bất kỳ bước nào. Chỉ đọc. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }
        showPage(request, response, accountId);
    }

    /**
     * HÀM CHÍNH — form "Xác nhận đã thu tiền" (KHỐI 4 của pt-sales.jsp). Tham số:
     *   q, partner, package, pt, start, time   lựa chọn 4 bước (ô ẩn, Service kiểm tra lại TẤT CẢ)
     *   method      Cash | Transfer (radio)
     *   reference   mã giao dịch chuyển khoản (không bắt buộc)
     *   csrfToken   chống giả mạo request
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

        // B1: gọi Service 1 lần.
        ServiceResult<PtSaleView> result = saleService.sell(accountId,
                request.getParameter("q"), request.getParameter("partner"), request.getParameter("package"),
                request.getParameter("pt"), request.getParameter("start"), request.getParameter("time"),
                request.getParameter("method"), request.getParameter("reference"), DateTimeUtil.now());

        // B2: bán xong → toast + trang trống (F5 không bán lần 2).
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            RequestUtil.redirect(request, response, PAGE_URL);
            return;
        }

        // B3: bị từ chối → dựng lại trang với đúng lựa chọn cũ, câu lỗi ở khối thanh toán.
        request.setAttribute("saleError", result.getMessage());           // → pt-sales.jsp KHỐI 4
        request.setAttribute("method", request.getParameter("method"));   // → giữ radio cách thu tiền
        showPage(request, response, accountId);
    }

    /** HÀM PHỤ — gọi Service.prepare với 6 tham số trên URL / form, rồi mở JSP. */
    private void showPage(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws ServletException, IOException {
        ServiceResult<PtSaleView> result = saleService.prepare(accountId,
                request.getParameter("q"),          // ← KHỐI 1 ô tìm khách
                request.getParameter("partner"),    // ← KHỐI 2 ô người tập cùng (gói đôi)
                request.getParameter("package"),    // ← KHỐI 2 radio gói
                request.getParameter("pt"),         // ← KHỐI 2 ô chọn PT
                request.getParameter("start"),      // ← KHỐI 2 ô ngày bắt đầu
                request.getParameter("time"),       // ← KHỐI 3 radio giờ cố định
                DateTimeUtil.now());

        if (result.isSuccess()) {
            request.setAttribute("sale", result.getData());          // → pt-sales.jsp mọi khối
        } else {
            request.setAttribute("pageError", result.getMessage());  // → pt-sales.jsp khung lỗi đỏ
        }
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        request.setAttribute("activeNav", "pt-sales");               // → layout-receptionist.jspf tô mục "Bán gói PT"
        RequestUtil.forward(request, response, VIEW);
    }

    /** HÀM PHỤ — mã tài khoản trong session; chưa đăng nhập → null. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
