package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CheckoutView;
import com.gms.dto.JsonResponse;
import com.gms.dto.ServiceResult;
import com.gms.service.PaymentService;
import com.gms.util.JsonUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET hiển thị checkout.
 * POST áp/gỡ voucher hoặc tạo lần thanh toán để mở VNPay.
 */
@WebServlet("/customer/checkout")
public class CheckoutServlet extends HttpServlet {

    private final PaymentService paymentService = new PaymentService();

    /**
     * Đọc đơn đúng chủ và hiển thị checkout.
     * Refresh GET không tạo Order hoặc Transaction mới.
     */
    @Override
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        // B1: lấy danh tính từ session.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response,
                    "/login?returnUrl=/customer/packages");
            return;
        }

        // B2: Service đọc đơn thuộc tài khoản hiện tại.
        Integer orderId = RequestUtil.getPositiveIntParam(request, "order");

        ServiceResult<CheckoutView> result =
                paymentService.getCheckoutView(accountId, orderId);

        if (result.isSuccess()) {
            CheckoutView view = result.getData();

            request.setAttribute("checkout", view);
            request.setAttribute("order", view.getOrder());

            if (request.getAttribute("voucherInput") == null) {
                request.setAttribute("voucherInput", view.getVoucherCode());
            }
        } else {
            request.setAttribute("checkoutError", result.getMessage());
        }

        // B3: dữ liệu chung của giao diện và token cho POST.
        request.setAttribute(
                "csrfToken", RequestUtil.getOrCreateCsrfToken(session));

        request.setAttribute(
                "customerName",
                session.getAttribute(SessionKey.DISPLAY_NAME));

        request.setAttribute("activeSidebar", "buy-package");

        request.setAttribute(
                "themeClass",
                "theme-pt customer-dashboard-page checkout-page");

        RequestUtil.forward(request, response, "customer/checkout");
    }

    /**
     * Nhận thao tác áp/gỡ voucher hoặc thanh toán.
     * Danh tính lấy từ session; mọi POST đều kiểm CSRF.
     */
    @Override
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        // B1: kiểm tra đăng nhập.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response,
                    "/login?returnUrl=/customer/packages");
            return;
        }

        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Integer orderId = RequestUtil.getPositiveIntParam(request, "order");
        String action = request.getParameter("action");

        // B2: tạo giao dịch và chuyển trình duyệt sang VNPay.
        if ("pay".equals(action)) {
            ServiceResult<String> paymentResult =
                    paymentService.startPayment(
                            accountId,
                            orderId,
                            RequestUtil.getClientIp(request));

            if (paymentResult.isSuccess()) {
                response.sendRedirect(paymentResult.getData());
            } else {
                request.setAttribute(
                        "paymentError", paymentResult.getMessage());
                doGet(request, response);
            }
            return;
        }

        // B3: xử lý hai thao tác voucher.
        ServiceResult<Void> result;

        if ("apply-voucher".equals(action)) {
            result = paymentService.applyVoucher(
                    accountId,
                    orderId,
                    request.getParameter("voucherCode"));

        } else if ("remove-voucher".equals(action)) {
            result = paymentService.removeVoucher(accountId, orderId);

        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // B4: AJAX trả JSON, không tải lại toàn bộ trang.
        if (RequestUtil.isAjaxRequest(request)) {
            writeVoucherResponse(
                    response, accountId, orderId, result);
            return;
        }

        // B5: hỗ trợ form thường khi không dùng AJAX.
        if (!result.isSuccess()) {
            request.setAttribute(
                    "voucherInput", request.getParameter("voucherCode"));
            request.setAttribute("voucherError", result.getMessage());
            request.setAttribute("errors", result.getFieldErrors());

            doGet(request, response);
            return;
        }

        RequestUtil.setFlashToast(
                request, "success", result.getMessage());

        RequestUtil.redirect(
                request, response, "/customer/checkout?order=" + orderId);
    }

    /**
     * Đọc lại snapshot từ DB sau khi đổi voucher.
     * Chỉ trả các trường cần để JavaScript cập nhật giao diện.
     */
    private void writeVoucherResponse(
            HttpServletResponse response,
            int accountId,
            Integer orderId,
            ServiceResult<Void> mutation) throws IOException {

        if (!mutation.isSuccess()) {
            JsonUtil.writeJson(response, JsonResponse.from(mutation));
            return;
        }

        ServiceResult<CheckoutView> viewResult =
                paymentService.getCheckoutView(accountId, orderId);

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
        data.put("preTaxText", view.getPreTaxText());
        data.put("voucherEditable", view.isVoucherEditable());
        data.put("voucherLockReason", view.getVoucherLockReason());

        JsonUtil.writeJson(
                response,
                JsonResponse.from(
                        ServiceResult.ok(data, mutation.getMessage())));
    }
}