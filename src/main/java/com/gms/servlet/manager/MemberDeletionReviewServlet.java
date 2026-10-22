package com.gms.servlet.manager;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDeletionReviewView;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDeletionService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Controller duyệt hoặc từ chối yêu cầu xóa hồ sơ hội viên cho Quản lý (Màn #32, UC-17, BF-07).
 * URL: /manager/member-deletions/review
 */
@WebServlet("/manager/member-deletions/review")
public class MemberDeletionReviewServlet extends HttpServlet {

    private static final String VIEW_NAME = "manager/member-deletion-review";
    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_REJECT = "reject";

    private final CustomerDeletionService customerDeletionService;

    public MemberDeletionReviewServlet() {
        this.customerDeletionService = new CustomerDeletionService();
    }

    MemberDeletionReviewServlet(CustomerDeletionService customerDeletionService) {
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
        int requestId = RequestUtil.getIntParam(request, "id", 0);

        if (requestId <= 0) {
            RequestUtil.redirect(request, response, "/manager/members?tab=pending-deletions");
            return;
        }

        ServiceResult<CustomerDeletionReviewView> result =
                customerDeletionService.getReviewDetail(managerAccountId, requestId);

        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, "/manager/members?tab=pending-deletions");
            return;
        }

        request.setAttribute("review", result.getData());
        request.setAttribute("activeNav", "members");

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

        int managerAccountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        int requestId = RequestUtil.getIntParam(request, "id", 0);
        String action = RequestUtil.getTrimmedParam(request, "action");

        if (requestId <= 0) {
            RequestUtil.setFlashToast(request, "error", "Không xác định được yêu cầu xóa.");
            RequestUtil.redirect(request, response, "/manager/members?tab=pending-deletions");
            return;
        }

        if (ACTION_APPROVE.equalsIgnoreCase(action)) {
            handleApprove(request, response, managerAccountId, requestId);
            return;
        }

        if (ACTION_REJECT.equalsIgnoreCase(action)) {
            handleReject(request, response, managerAccountId, requestId);
            return;
        }

        RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
        RequestUtil.redirect(request, response, "/manager/member-deletions/review?id=" + requestId);
    }

    private void handleApprove(HttpServletRequest request, HttpServletResponse response, int managerAccountId, int requestId)
            throws IOException {

        ServiceResult<String> result = customerDeletionService.approveDeletion(managerAccountId, requestId);

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getData());
            RequestUtil.redirect(request, response, "/manager/members?tab=pending-deletions");
        } else {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, "/manager/member-deletions/review?id=" + requestId);
        }
    }

    private void handleReject(HttpServletRequest request, HttpServletResponse response, int managerAccountId, int requestId)
            throws IOException {

        String rejectReason = RequestUtil.getTrimmedParam(request, "rejectReason");
        ServiceResult<String> result = customerDeletionService.rejectDeletion(managerAccountId, requestId, rejectReason);

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getData());
            RequestUtil.redirect(request, response, "/manager/members?tab=pending-deletions");
        } else {
            String errorMsg = result.getMessage();
            if (result.getFieldErrors() != null && result.getFieldErrors().containsKey("rejectReason")) {
                errorMsg = result.getFieldErrors().get("rejectReason");
            }
            RequestUtil.setFlashToast(request, "error", errorMsg);
            RequestUtil.redirect(request, response, "/manager/member-deletions/review?id=" + requestId);
        }
    }
}
