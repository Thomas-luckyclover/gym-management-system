package com.gms.servlet.receptionist;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDeletionService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.gms.dto.CustomerProfileDetailView;

/** BF-07: chi tiết hồ sơ; dữ liệu và quyền thao tác được Service chuẩn bị. */
@WebServlet("/receptionist/customers/form")
public class CustomerProfileFormServlet extends HttpServlet {

    private final CustomerDeletionService service = new CustomerDeletionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        showPage(request, response, RequestUtil.getIntParam(request, "id", 0));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        if (!"delete".equals(request.getParameter("action")) || customerId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String reason = RequestUtil.getTrimmedParam(request, "reason");
        ServiceResult<String> result = service.requestOrDeleteCustomer(accountId, customerId, reason);
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getData());
            RequestUtil.redirect(request, response, "/receptionist/customers");
            return;
        }
        request.setAttribute("actionError", result.getMessage());
        request.setAttribute("errors", result.getFieldErrors());
        request.setAttribute("reason", reason);
        showPage(request, response, customerId);
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response, int customerId) throws ServletException, IOException {
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        ServiceResult<CustomerProfileDetailView> result = service.getProfileDetail(accountId, customerId, false);
        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, "/receptionist/customers");
            return;
        }
        request.setAttribute("detail", result.getData());
        request.setAttribute("managerView", false);
        request.setAttribute("listPath", "/receptionist/customers");
        request.setAttribute("activeNav", "customers");
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        RequestUtil.forward(request, response, "receptionist/customer-form");
    }
}
