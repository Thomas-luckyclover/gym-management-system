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
import com.gms.dto.CustomerDeletionReviewView;

/** BF-07: quản lý xem yêu cầu rồi duyệt hoặc từ chối; Service kiểm lại trạng thái khi ghi. */
@WebServlet("/manager/member-deletions/review")
public class MemberDeletionReviewServlet extends HttpServlet {

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
        int requestId = RequestUtil.getIntParam(request, "id", 0);
        String action = RequestUtil.getTrimmedParam(request, "action");
        String rejectReason = RequestUtil.getTrimmedParam(request, "rejectReason");
        ServiceResult<String> result;
        if ("approve".equals(action)) {
            result = service.approveDeletion(accountId, requestId);
        } else if ("reject".equals(action)) {
            result = service.rejectDeletion(accountId, requestId, rejectReason);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getData());
            RequestUtil.redirect(request, response, "/manager/members?tab=pending-deletions");
            return;
        }
        // Lỗi phải giữ nội dung, không redirect làm mất lý do đã nhập.
        request.setAttribute("actionError", result.getMessage());
        request.setAttribute("errors", result.getFieldErrors());
        request.setAttribute("rejectReason", rejectReason);
        request.setAttribute("reopenReject", "reject".equals(action));
        showPage(request, response, requestId);
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response, int requestId) throws ServletException, IOException {
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        ServiceResult<CustomerDeletionReviewView> result = service.getReviewDetail(accountId, requestId);
        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, "/manager/members?tab=pending-deletions");
            return;
        }
        request.setAttribute("review", result.getData());
        request.setAttribute("activeNav", "members");
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        RequestUtil.forward(request, response, "manager/member-deletion-review");
    }
}
