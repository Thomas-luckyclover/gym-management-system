package com.gms.servlet.payment;

import com.gms.dto.IpnResult;
import com.gms.integration.VnPayClient;
import com.gms.service.PaymentCallbackService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Điểm nhận kết quả server-to-server từ VNPay.
 * Không dùng session hoặc CSRF; xác thực bằng chữ ký VNPay.
 */
@WebServlet("/api/v1/commerce/payment/webhook/vnpay")
public class VnPayIpnServlet extends HttpServlet {

    private final VnPayClient vnPayClient = new VnPayClient();

    private final PaymentCallbackService callbackService =
            new PaymentCallbackService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // B1: đọc các tham số vnp_* từ request.
        Map<String, String> params =
                vnPayClient.extractVnpParams(request);

        // B2: Service xác minh và xử lý dữ liệu.
        IpnResult result = callbackService.handleIpn(params);

        // B3: trả đúng cấu trúc VNPay, không forward JSP.
        response.setContentType("application/json;charset=UTF-8");

        response.getWriter().write(
                vnPayClient.buildIpnResponse(
                        result.getCode(),
                        result.getMessage()));
    }

    /**
     * Chấp nhận POST dạng tham số query/form, xử lý giống GET.
     */
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        doGet(request, response);
    }
}