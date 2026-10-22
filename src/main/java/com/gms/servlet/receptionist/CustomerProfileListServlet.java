package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDeletionReviewView;
import com.gms.dto.CustomerProfileRowView;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDeletionService;
import com.gms.util.PaginationUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Controller hiển thị danh sách hồ sơ khách hàng tại quầy Lễ tân (Màn #28, UC-16, BF-07).
 * Cho phép tìm kiếm hồ sơ Active, gửi yêu cầu xóa hoặc xóa ngay nếu không còn gói/buổi tập.
 * Hỗ trợ tab xem lại các yêu cầu xóa do chính Lễ tân này gửi (Q8).
 */
@WebServlet("/receptionist/customers")
public class CustomerProfileListServlet extends HttpServlet {

    private static final String VIEW_NAME = "receptionist/customer-list";
    private static final String ACTION_DELETE = "delete";

    private final CustomerDeletionService customerDeletionService;

    public CustomerProfileListServlet() {
        this.customerDeletionService = new CustomerDeletionService();
    }

    CustomerProfileListServlet(CustomerDeletionService customerDeletionService) {
        this.customerDeletionService = customerDeletionService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String tab = RequestUtil.getTrimmedParam(request, "tab");
        if (tab == null || (!"my-requests".equals(tab) && !"profiles".equals(tab))) {
            tab = "profiles";
        }

        int page = PaginationUtil.getPageNumber(request);
        int pageSize = PaginationUtil.getPageSize(request);

        if ("my-requests".equals(tab)) {
            // Tab 2: Yêu cầu của tôi (Q8)
            ServiceResult<List<CustomerDeletionReviewView>> requestsResult =
                    customerDeletionService.getMyRequestsList(accountId, page, pageSize);
            int totalCount = customerDeletionService.countMyRequests(accountId);
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            request.setAttribute("myRequests", requestsResult.isSuccess() ? requestsResult.getData() : Collections.emptyList());
            request.setAttribute("totalCount", totalCount);
            request.setAttribute("totalPages", Math.max(1, totalPages));
        } else {
            // Tab 1: Danh sách hồ sơ khách hàng đang hoạt động (CBR-04)
            String keyword = RequestUtil.getTrimmedParam(request, "keyword");
            ServiceResult<List<CustomerProfileRowView>> profilesResult =
                    customerDeletionService.getCustomerProfiles(keyword, "Active", false, false, page, pageSize);
            int totalCount = customerDeletionService.countCustomerProfiles(keyword, "Active", false, false);
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            request.setAttribute("customers", profilesResult.isSuccess() ? profilesResult.getData() : Collections.emptyList());
            request.setAttribute("keyword", keyword);
            request.setAttribute("totalCount", totalCount);
            request.setAttribute("totalPages", Math.max(1, totalPages));
        }

        request.setAttribute("currentTab", tab);
        request.setAttribute("page", page);
        request.setAttribute("pageSize", pageSize);
        request.setAttribute("activeNav", "customers");

        RequestUtil.forward(request, response, VIEW_NAME);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action");

        if (ACTION_DELETE.equalsIgnoreCase(action)) {
            handleDeleteCustomer(request, response, accountId);
            return;
        }

        RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
        RequestUtil.redirect(request, response, "/receptionist/customers");
    }

    private void handleDeleteCustomer(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        String reason = RequestUtil.getTrimmedParam(request, "reason");

        if (customerId <= 0) {
            RequestUtil.setFlashToast(request, "error", "Không xác định được hồ sơ khách hàng.");
            RequestUtil.redirect(request, response, "/receptionist/customers");
            return;
        }

        ServiceResult<String> result = customerDeletionService.requestOrDeleteCustomer(accountId, customerId, reason);

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getData());
        } else {
            String errorMsg = result.getMessage();
            if (result.getFieldErrors() != null && result.getFieldErrors().containsKey("reason")) {
                errorMsg = result.getFieldErrors().get("reason");
            }
            RequestUtil.setFlashToast(request, "error", errorMsg);
        }

        RequestUtil.redirect(request, response, "/receptionist/customers");
    }
}
