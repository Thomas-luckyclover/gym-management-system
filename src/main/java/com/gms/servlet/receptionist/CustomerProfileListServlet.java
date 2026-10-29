package com.gms.servlet.receptionist;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDeletionService;
import com.gms.util.PaginationUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.gms.dto.CustomerDeletionReviewView;
import com.gms.dto.CustomerProfileDetailView;
import com.gms.dto.CustomerProfileRowView;
import com.gms.dto.PageResult;

/** BF-07: danh sách hồ sơ, lịch sử yêu cầu và gửi/xóa hồ sơ ngay tại danh sách. */
@WebServlet("/receptionist/customers")
public class CustomerProfileListServlet extends HttpServlet {

    private final CustomerDeletionService service = new CustomerDeletionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        showPage(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // B1: bảo vệ form xóa, không gọi Service nếu request thiếu token.
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        String reason = RequestUtil.getTrimmedParam(request, "reason");
        if (!"delete".equals(request.getParameter("action")) || customerId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // B2: thành công chuyển trang; lỗi giữ lý do và mở lại đúng hộp xóa.
        ServiceResult<String> result = service.requestOrDeleteCustomer(accountId, customerId, reason);
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getData());
            RequestUtil.redirect(request, response, "/receptionist/customers?tab=profiles");
            return;
        }
        request.setAttribute("actionError", result.getMessage());
        request.setAttribute("errors", result.getFieldErrors());
        request.setAttribute("reason", reason);
        ServiceResult<CustomerProfileDetailView> detail = service.getProfileDetail(accountId, customerId, false);
        if (detail.isSuccess()) {
            request.setAttribute("deleteTarget", detail.getData());
        }
        showPage(request, response);
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        int page = PaginationUtil.getPageNumber(request);
        int pageSize = PaginationUtil.getPageSize(request);
        String tab = RequestUtil.getTrimmedParam(request, "tab");
        String keyword = RequestUtil.getTrimmedParam(request, "keyword");
        String status = service.normalizeStatus(RequestUtil.getTrimmedParam(request, "status"), false);
        if ("my-requests".equals(tab)) {
            ServiceResult<PageResult<CustomerDeletionReviewView>> result = service.getMyRequestsPage(accountId, page, pageSize);
            if (result.isSuccess()) {
                request.setAttribute("pageData", result.getData());
            } else {
                request.setAttribute("loadError", result.getMessage());
            }
        } else {
            tab = "profiles";
            ServiceResult<PageResult<CustomerProfileRowView>> result = service.getProfilesPage(accountId, keyword, status, false, page, pageSize);
            if (result.isSuccess()) {
                request.setAttribute("pageData", result.getData());
            } else {
                request.setAttribute("loadError", result.getMessage());
            }
        }
        request.setAttribute("managerView", false);
        request.setAttribute("currentTab", tab);
        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", status);
        request.setAttribute("pageSize", pageSize);
        request.setAttribute("listPath", "/receptionist/customers");
        request.setAttribute("detailPath", "/receptionist/customers/form");
        request.setAttribute("activeNav", "customers");
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        RequestUtil.forward(request, response, "receptionist/customer-list");
    }
}
