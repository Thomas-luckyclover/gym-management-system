package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CounterSaleReceipt;
import com.gms.dto.JsonResponse;
import com.gms.dto.ServiceResult;
import com.gms.dto.StartDatePicker;
import com.gms.service.SubscriptionStartDateService;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST chọn/đổi ngày bắt đầu của gói Membership đã mua (trang kết quả thanh toán, Dashboard và màn bán quầy).
 *
 * <p>
 * Hỗ trợ cả khách hàng tự đổi (/customer/subscription/start-date)
 * và lễ tân quầy đổi cho khách (/receptionist/subscription/start-date).
 * Trình duyệt gửi mã gói và ngày bắt đầu; ngày hết hạn do Service tính toán lại tự động.
 * Service trả picker mới để JavaScript cập nhật ô ngày mà không phải tải lại trang.</p>
 */
@WebServlet(urlPatterns = {"/customer/subscription/start-date", "/receptionist/subscription/start-date"})
public class SubscriptionStartDateServlet extends HttpServlet {

    private final SubscriptionStartDateService service = new SubscriptionStartDateService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: đăng nhập và CSRF.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        if (accountId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = request.getServletPath();
        boolean isReceptionist = path != null && path.startsWith("/receptionist");

        Integer subscriptionId = RequestUtil.getPositiveIntParam(request, "subscriptionId");
        LocalDate startDate = DateTimeUtil.parseHtmlDate(request.getParameter("startDate"));

        ServiceResult<StartDatePicker> result;
        if (isReceptionist) {
            result = service.changeStartDateByStaff(accountId, subscriptionId, startDate);
            if (result.isSuccess() && result.getData() != null && session != null) {
                Object lastReceiptObj = session.getAttribute("lastReceipt");
                if (lastReceiptObj instanceof CounterSaleReceipt) {
                    CounterSaleReceipt receipt = (CounterSaleReceipt) lastReceiptObj;
                    StartDatePicker p = result.getData();
                    session.setAttribute("lastReceipt", receipt.withUpdatedDates(p.getStartText(), p.getEndText()));
                }
            }
        } else {
            result = service.changeStartDate(accountId, subscriptionId, startDate);
        }

        if (RequestUtil.isAjaxRequest(request)) {
            writeJson(response, result);
            return;
        }

        // B3: không có JavaScript thì báo bằng toast và chuyển trang.
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        if (isReceptionist) {
            RequestUtil.redirect(request, response, "/receptionist/sales");
        } else {
            RequestUtil.redirect(request, response, "/customer/dashboard");
        }
    }

    /** Chỉ trả các trường JavaScript cần để cập nhật ô ngày, không trả entity. */
    private void writeJson(HttpServletResponse response, ServiceResult<StartDatePicker> result) throws IOException {
        StartDatePicker picker = result.getData();
        if (!result.isSuccess()) {
            JsonUtil.writeJson(response, JsonResponse.from(result));
            return;
        }
        if (picker == null) {
            JsonUtil.writeJson(response, JsonResponse.error("Không có dữ liệu."));
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("startText", picker.getStartText());
        data.put("endText", picker.getEndText());
        data.put("startIso", picker.getStartIso());
        data.put("editable", picker.isEditable());
        data.put("lockReason", picker.getLockReason());
        JsonUtil.writeJson(response, JsonResponse.from(ServiceResult.ok(data, result.getMessage())));
    }
}
