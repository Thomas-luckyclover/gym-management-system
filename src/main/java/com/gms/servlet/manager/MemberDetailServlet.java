package com.gms.servlet.manager;

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
@WebServlet("/manager/members/detail")
public class MemberDetailServlet extends HttpServlet {

    private final CustomerDeletionService service = new CustomerDeletionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        showPage(request, response, RequestUtil.getIntParam(request, "id", 0));
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response, int customerId) throws ServletException, IOException {
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        ServiceResult<CustomerProfileDetailView> result = service.getProfileDetail(accountId, customerId, true);
        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, "/manager/members");
            return;
        }
        request.setAttribute("detail", result.getData());
        request.setAttribute("managerView", true);
        request.setAttribute("listPath", "/manager/members");
        request.setAttribute("activeNav", "members");
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        RequestUtil.forward(request, response, "manager/member-detail");
    }
}
