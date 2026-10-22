package com.gms.servlet.manager;

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
 * Controller danh sách hội viên cho Quản lý (Màn #30, UC-41, BF-07).
 * Hỗ trợ tìm kiếm, lọc theo trạng thái (Active/Inactive), và tab riêng hiển thị các yêu cầu xóa chờ duyệt.
 */
@WebServlet("/manager/members")
public class MemberListServlet extends HttpServlet {

    private static final String VIEW_NAME = "manager/member-list";

    private final CustomerDeletionService customerDeletionService;

    public MemberListServlet() {
        this.customerDeletionService = new CustomerDeletionService();
    }

    MemberListServlet(CustomerDeletionService customerDeletionService) {
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

        int managerAccountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String tab = RequestUtil.getTrimmedParam(request, "tab");
        if (tab == null || (!"pending-deletions".equals(tab) && !"all".equals(tab))) {
            tab = "all";
        }

        int page = PaginationUtil.getPageNumber(request);
        int pageSize = PaginationUtil.getPageSize(request);
        int pendingBadgeCount = customerDeletionService.countPendingReviews();

        if ("pending-deletions".equals(tab)) {
            // Tab 2: Danh sách các yêu cầu xóa chờ Quản lý duyệt (#32 tóm tắt)
            ServiceResult<List<CustomerDeletionReviewView>> pendingResult =
                    customerDeletionService.getPendingReviewList(managerAccountId, page, pageSize);
            int totalCount = pendingBadgeCount;
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            request.setAttribute("pendingRequests", pendingResult.isSuccess() ? pendingResult.getData() : Collections.emptyList());
            request.setAttribute("totalCount", totalCount);
            request.setAttribute("totalPages", Math.max(1, totalPages));
        } else {
            // Tab 1: Toàn bộ danh sách hội viên (xem được cả Inactive)
            String keyword = RequestUtil.getTrimmedParam(request, "keyword");
            String status = RequestUtil.getTrimmedParam(request, "status");
            if (status == null || status.isBlank()) {
                status = "ALL";
            }

            ServiceResult<List<CustomerProfileRowView>> profilesResult =
                    customerDeletionService.getCustomerProfiles(keyword, status, false, true, page, pageSize);
            int totalCount = customerDeletionService.countCustomerProfiles(keyword, status, false, true);
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            request.setAttribute("customers", profilesResult.isSuccess() ? profilesResult.getData() : Collections.emptyList());
            request.setAttribute("keyword", keyword);
            request.setAttribute("statusFilter", status);
            request.setAttribute("totalCount", totalCount);
            request.setAttribute("totalPages", Math.max(1, totalPages));
        }

        request.setAttribute("pendingBadgeCount", pendingBadgeCount);
        request.setAttribute("currentTab", tab);
        request.setAttribute("page", page);
        request.setAttribute("pageSize", pageSize);
        request.setAttribute("activeNav", "members");

        RequestUtil.forward(request, response, VIEW_NAME);
    }
}
