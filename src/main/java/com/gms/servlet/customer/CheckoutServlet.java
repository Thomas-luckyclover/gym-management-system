package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.dto.CheckoutView;
import com.gms.dto.JsonResponse;
import com.gms.util.JsonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import com.gms.service.PaymentService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**  đọc đơn đúng chủ, áp/thay/gỡ voucher */
@WebServlet("/customer/checkout")
public class CheckoutServlet extends HttpServlet {

    // Service đọc đơn thật và kiểm tra chủ sở hữu trước khi trả dữ liệu.
    private final PaymentService paymentService = new PaymentService();

    /** GET chỉ đọc đơn đã tồn tại, refresh không tạo đơn mới. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: lấy tài khoản từ session; filter khu vực kiểm quyền Customer.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/customer/packages");
            return;
        }

        // B2: Service đọc đơn thuộc chính tài khoản, không nhận customerId trên URL.
        Integer orderId = RequestUtil.getPositiveIntParam(request, "order");
        ServiceResult<CheckoutView> result = paymentService.getCheckoutView(accountId, orderId);
        if (result.isSuccess()) {
            request.setAttribute("checkout", result.getData());
            request.setAttribute("order", result.getData().getOrder());
            if (request.getAttribute("voucherInput") == null) {
                request.setAttribute("voucherInput", result.getData().getVoucherCode());
            }
        } else {
            request.setAttribute("checkoutError", result.getMessage());
        }

        // B3: token bảo vệ hai thao tác áp/gỡ voucher, countdown dùng common.js.
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));
        request.setAttribute("customerName", session.getAttribute(SessionKey.DISPLAY_NAME));
        request.setAttribute("activeSidebar", "buy-package");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page checkout-page");
        // B4: mở giao dịch thanh toán.
        
            RequestUtil.forward(request, response, "customer/payment");
         
        RequestUtil.forward(request, response, "customer/checkout");
    }
    /** POST áp/gỡ voucher: lỗi giữ input và forward, thành công redirect để tránh gửi lại form. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: lấy danh tính từ session và kiểm CSRF, không tin accountId trong form.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/customer/packages");
            return;
        }
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // B2: Service kiểm đơn thuộc chủ, còn hạn và điều kiện voucher trong transaction.
        Integer orderId = RequestUtil.getPositiveIntParam(request, "order");
        String action = request.getParameter("action");  //front end truyen vao
        ServiceResult<Void> result;
        if ("apply-voucher".equals(action)) {
            result = paymentService.applyVoucher(accountId, orderId, request.getParameter("voucherCode"));
        } else if ("remove-voucher".equals(action)) {
            result = paymentService.removeVoucher(accountId, orderId);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // AJAX trả snapshot mới hoặc lỗi field, không reload toàn trang.
        if (RequestUtil.isAjaxRequest(request)) {
            writeVoucherResponse(request, response, accountId, orderId, result);
            return;
        }

        // B3: lỗi giữ nguyên mã đã nhập; giá/giảm/VAT đọc lại từ DB, không lấy từ request.
        if (!result.isSuccess()) {
            request.setAttribute("voucherInput", request.getParameter("voucherCode"));
            request.setAttribute("voucherError", result.getMessage());
            request.setAttribute("errors", result.getFieldErrors());
            doGet(request, response);
            return;
        }
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/customer/checkout?order=" + orderId);
    }

    /** Trả dữ liệu tối thiểu cho checkout.js; không xuất Entity hoặc URL ký số vào JSON voucher. */
    private void writeVoucherResponse(HttpServletRequest request, HttpServletResponse response,
            int accountId, Integer orderId, ServiceResult<Void> mutation) throws IOException {
        if (!mutation.isSuccess()) {
            JsonUtil.writeJson(response, JsonResponse.from(mutation));
            return;
        }
        ServiceResult<CheckoutView> viewResult = paymentService.getCheckoutView(accountId, orderId);
        if (!viewResult.isSuccess()) {
            JsonUtil.writeJson(response, JsonResponse.from(viewResult));
            return;
        }
        CheckoutView view = viewResult.getData();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("voucherCode", view.getVoucherCode());
        data.put("voucherMessage", view.getVoucherMessage());
        data.put("discountText", view.getDiscountText());
        data.put("totalText", view.getTotalText());
        data.put("vatText", view.getVatText());
        data.put("voucherEditable", view.isVoucherEditable());
        data.put("voucherLockReason", view.getVoucherLockReason());
        JsonUtil.writeJson(response, JsonResponse.from(ServiceResult.ok(data, mutation.getMessage())));
    }

}
