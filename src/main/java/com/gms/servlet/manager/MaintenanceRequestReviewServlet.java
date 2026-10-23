package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dto.ServiceResult;
import com.gms.service.MaintenanceRequestService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Controller phê duyệt hoặc từ chối yêu cầu bảo trì của Manager (UC-29).
 */
@WebServlet("/manager/maintenance-requests/review")
public class MaintenanceRequestReviewServlet extends HttpServlet {

    private static final String VIEW_NAME =
            "manager/maintenance-request-review";

    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_REJECT = "reject";

    private final MaintenanceRequestService maintenanceRequestService =
            new MaintenanceRequestService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        int requestId =
                RequestUtil.getIntParam(
                        request,
                        "id",
                        0
                );

        renderReview(
                request,
                response,
                requestId
        );
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        HttpSession session =
                request.getSession(false);

        if (session == null
                || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {

            RequestUtil.redirect(
                    request,
                    response,
                    "/login"
            );
            return;
        }

        int accountId =
                (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID
                );

        if (!RequestUtil.isValidCsrfToken(request)) {
            RequestUtil.setFlashToast(
                    request,
                    "error",
                    "Yêu cầu không hợp lệ hoặc đã hết hạn (CSRF)."
            );
            RequestUtil.redirect(
                    request,
                    response,
                    "/manager/maintenance-requests"
            );
            return;
        }

        int requestId =
                RequestUtil.getIntParam(
                        request,
                        "requestId",
                        0
                );

        String action =
                RequestUtil.getTrimmedParam(
                        request,
                        "action"
                );

        if (ACTION_APPROVE.equals(action)) {
            boolean setUnderMaintenance =
                    request.getParameter(
                            "setUnderMaintenance"
                    ) != null;

            ServiceResult<Void> result =
                    maintenanceRequestService.approveRequest(
                            accountId,
                            requestId,
                            setUnderMaintenance
                    );

            if (result.isSuccess()) {
                RequestUtil.setFlashToast(
                        request,
                        "success",
                        "Đã duyệt yêu cầu bảo trì."
                );

                RequestUtil.redirect(
                        request,
                        response,
                        "/manager/maintenance-requests"
                );

                return;
            }

            request.setAttribute(
                    "formMessage",
                    result.getMessage()
            );

            renderReview(
                    request,
                    response,
                    requestId
            );
            return;
        }

        if (ACTION_REJECT.equals(action)) {
            String rejectReason =
                    RequestUtil.getTrimmedParam(
                            request,
                            "rejectReason"
                    );

            ServiceResult<Void> result =
                    maintenanceRequestService.rejectRequest(
                            accountId,
                            requestId,
                            rejectReason
                    );

            if (result.isSuccess()) {
                RequestUtil.setFlashToast(
                        request,
                        "success",
                        "Đã từ chối yêu cầu bảo trì."
                );

                RequestUtil.redirect(
                        request,
                        response,
                        "/manager/maintenance-requests"
                );

                return;
            }

            request.setAttribute(
                    "errors",
                    result.getFieldErrors()
            );
            request.setAttribute(
                    "rejectReason",
                    rejectReason
            );
            request.setAttribute(
                    "formMessage",
                    result.getMessage()
            );

            renderReview(
                    request,
                    response,
                    requestId
            );
            return;
        }

        RequestUtil.redirect(
                request,
                response,
                "/manager/maintenance-requests"
        );
    }

    private void renderReview(
            HttpServletRequest request,
            HttpServletResponse response,
            int requestId
    ) throws ServletException, IOException {

        ServiceResult<MaintenanceRequestDAO.MaintenanceRequestView>
                result =
                maintenanceRequestService.getRequestDetail(
                        requestId
                );

        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(
                    request,
                    "error",
                    result.getMessage()
            );

            RequestUtil.redirect(
                    request,
                    response,
                    "/manager/maintenance-requests"
            );

            return;
        }

        request.setAttribute(
                "item",
                result.getData()
        );

        request.setAttribute(
                "activeNav",
                "maintenance"
        );

        HttpSession session = request.getSession(true);
        String csrfToken = RequestUtil.getOrCreateCsrfToken(session);
        request.setAttribute("csrfToken", csrfToken);

        RequestUtil.forward(
                request,
                response,
                VIEW_NAME
        );
    }
}