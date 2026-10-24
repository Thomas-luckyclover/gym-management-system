package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.PaymentResultView;
import com.gms.dto.ServiceResult;
import com.gms.service.PaymentCallbackService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Đọc kết quả giao dịch đúng chủ từ database và hiển thị JSP.
 */
@WebServlet("/customer/payment-result")
public class PaymentResultServlet extends HttpServlet {

    private final PaymentCallbackService callbackService =
            new PaymentCallbackService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(
                    request, response,
                    "/login?returnUrl=/customer/packages");
            return;
        }

        Integer transactionId =
                RequestUtil.getPositiveIntParam(request, "transaction");

        ServiceResult<PaymentResultView> result =
                callbackService.getPaymentResult(
                        accountId, transactionId);

        if (result.isSuccess()) {
            request.setAttribute("result", result.getData());
        } else {
            request.setAttribute("resultError", result.getMessage());
        }

        request.setAttribute("transactionId", transactionId);

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