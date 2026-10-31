package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST khách chọn ngày bắt đầu của gói Membership đã mua (trang kết quả thanh toán và Dashboard).
 *
 * <p>
 * Danh tính lấy từ session, mọi POST kiểm CSRF. Trình duyệt chỉ gửi mã gói và ngày bắt đầu; ngày hết hạn do
 * Service tính. Service trả picker mới để JavaScript cập nhật ô ngày mà không phải tải lại trang.</p>
 */
@WebServlet("/customer/subscription/start-date")
public class SubscriptionStartDateServlet extends HttpServlet {

    private final SubscriptionStartDateService service = new SubscriptionStartDateService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: đăng nhập và CSRF.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/customer/dashboard");
            return;
        }
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // B2: Service kiểm luật và ghi trong một transaction.
        ServiceResult<StartDatePicker> result = service.changeStartDate(
                accountId,
                RequestUtil.getPositiveIntParam(request, "subscriptionId"),
                DateTimeUtil.parseHtmlDate(request.getParameter("startDate")));

        if (RequestUtil.isAjaxRequest(request)) {
            writeJson(response, result);
            return;
        }

        // B3: không có JavaScript thì báo bằng toast và về Dashboard.
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        RequestUtil.redirect(request, response, "/customer/dashboard");
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
