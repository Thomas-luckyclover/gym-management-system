package com.gms.servlet.payment;

import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.integration.VnPayClient;
import com.gms.service.PaymentCallbackService;
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
import com.gms.dto.PaymentResultView;

/**
 * Nhận trình duyệt quay về từ VNPay. Kiểm chữ ký và chủ đơn rồi chuyển sang URL
 * kết quả gọn.
 */
@WebServlet("/payment/vnpay-return")
public class VnPayReturnServlet extends HttpServlet {

    private final VnPayClient vnPayClient = new VnPayClient();

    private final PaymentCallbackService callbackService
            = new PaymentCallbackService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        // B1: dữ liệu đơn chỉ hiện cho khách đăng nhập.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            String returnPath = request.getServletPath();

            if (request.getQueryString() != null) {
                returnPath += "?" + request.getQueryString();
            }

            RequestUtil.redirect(
                    request,
                    response,
                    "/login?returnUrl="
                    + URLEncoder.encode(
                            returnPath, StandardCharsets.UTF_8));
            return;
        }

        String role
                = (String) session.getAttribute(SessionKey.ROLE);

        if (!Role.CUSTOMER.getDbValue().equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // B2: xác minh Return nhưng không cập nhật Paid.
        ServiceResult<Integer> result
                = callbackService.resolveReturnTransaction(
                        accountId,
                        vnPayClient.extractVnpParams(request));

        if (result.isSuccess()) {
            // result đã qua kiểm tra chữ ký và chủ đơn.
            int transactionId = result.getData();

            ServiceResult<PaymentResultView> dbResult
                    = callbackService.getPaymentResult(accountId, transactionId);

            if (dbResult.isSuccess()) {
                PaymentResultView view = dbResult.getData();

                // DB chưa nhận IPN: hiển thị riêng kết quả VNPay đã báo.
                if (view.isWaiting()) {
                    String responseCode
                            = request.getParameter("vnp_ResponseCode");

                    String transactionStatus
                            = request.getParameter("vnp_TransactionStatus");

                    boolean gatewaySuccess = "00".equals(responseCode)
                            && "00".equals(transactionStatus);

                    String headline = gatewaySuccess
                            ? "VNPay báo thanh toán thành công"
                            : "VNPay chưa xác nhận thanh toán thành công";

                    String message = gatewaySuccess
                            ? "GMS đang chờ xác nhận IPN để hoàn tất đơn và cấp gói."
                            : "GMS đang chờ cập nhật kết quả giao dịch qua IPN.";

                    view = new PaymentResultView(
                            view.getOrder(),
                            headline,
                            message,
                            false, // Chưa coi GMS đã xác nhận thành công.
                            true,
                            false);
                }

                request.setAttribute("result", view);
            } else {
                request.setAttribute("resultError", dbResult.getMessage());
            }

            request.setAttribute("transactionId", transactionId);
            request.setAttribute("cleanResultUrl", true);

            request.setAttribute(
                    "customerName",
                    session.getAttribute(SessionKey.DISPLAY_NAME));

            request.setAttribute("activeSidebar", "buy-package");

            request.setAttribute(
                    "themeClass",
                    "theme-pt customer-dashboard-page payment-result-page");

            RequestUtil.forward(
                    request, response, "payment/payment-result");
            return;
        }

        // B3: dữ liệu không hợp lệ thì hiện lỗi xác minh.
        request.setAttribute("resultError", result.getMessage());

        request.setAttribute(
                "customerName",
                session.getAttribute(SessionKey.DISPLAY_NAME));

        request.setAttribute("activeSidebar", "buy-package");

        request.setAttribute(
                "themeClass",
                "theme-pt customer-dashboard-page payment-result-page");

        RequestUtil.forward(
                request, response, "payment/payment-result");
    }
}
