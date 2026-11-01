package com.gms.servlet.manager;

import com.gms.constant.Messages;
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
                    Messages.MSG214_FORM_SESSION_EXPIRED
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
            String managerNote = RequestUtil.getTrimmedParam(request, "managerNote");

            ServiceResult<Void> result =
                    maintenanceRequestService.approveRequest(
                            accountId,
                            requestId,
                            setUnderMaintenance,
                            managerNote
                    );

            if (result.isSuccess()) {
                RequestUtil.setFlashToast(
                        request,
                        "success",
                        Messages.MSG252_MAINTENANCE_APPROVED
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
                        Messages.MSG253_MAINTENANCE_REJECTED
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

        MaintenanceRequestDAO.MaintenanceRequestView viewItem = result.getData();
        request.setAttribute("item", viewItem);

        // Tra cứu lần hỏng trước của máy này (Closed gần nhất)
        if (viewItem.getRequest() != null) {
            ServiceResult<MaintenanceRequestDAO.MaintenanceRequestView> prevResult =
                    maintenanceRequestService.getLastClosedRequest(viewItem.getRequest().getEquipmentItemId(), requestId);
            if (prevResult.isSuccess()) {
                request.setAttribute("lastClosedRequest", prevResult.getData());
            }

            // Tải thông tin dòng máy để biết số máy khả dụng trong dòng
            ServiceResult<com.gms.entity.Facility> facilityResult =
                    maintenanceRequestService.getFacilityById(viewItem.getFacilityId());
            if (facilityResult.isSuccess()) {
                request.setAttribute("facility", facilityResult.getData());
            }
        }

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