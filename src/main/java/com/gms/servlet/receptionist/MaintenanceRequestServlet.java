package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Facility;
import com.gms.entity.MaintenanceRequest;
import com.gms.service.MaintenanceRequestService;
import com.gms.util.JsonUtil;
import com.gms.util.PaginationUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Presentation Layer (Servlet Controller): Manages HTTP interactions for Receptionist
 * maintenance request creation, tracking, and closure workflows (UC-28 / BF-06).
 *
 * <p><b>Architectural Responsibility:</b></p>
 * The Servlet acts as the Controller in the MVC pattern. It parses and validates HTTP request parameters,
 * verifies session authentication, delegates business operations to {@link MaintenanceRequestService},
 * and decides the next view:
 * <ul>
 *   <li><b>GET requests:</b> Read-only idempotent page rendering with multi-criteria filtering and pagination.</li>
 *   <li><b>POST requests:</b> State-changing operations (create, close) adhering to the Post-Redirect-Get (PRG)
 *       pattern with session flash messages to prevent duplicate submissions on browser refresh.</li>
 * </ul>
 */
@WebServlet("/receptionist/maintenance-requests")
public class MaintenanceRequestServlet extends HttpServlet {

    private static final String VIEW_NAME =
            "receptionist/maintenance-requests";

    private static final String ACTION_CREATE = "create";
    private static final String ACTION_CLOSE = "close";

    private final MaintenanceRequestService maintenanceRequestService =
            new MaintenanceRequestService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        Integer accountId = getAccountId(request);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        renderPage(request, response, accountId);
    }

    /**
     * Dispatches state-changing POST actions (create or close) following the PRG pattern.
     */
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        Integer accountId = getAccountId(request);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        String action =
                RequestUtil.getTrimmedParam(request, "action");

        if (ACTION_CREATE.equals(action)) {
            handleCreate(request, response, accountId);
            return;
        }

        if (ACTION_CLOSE.equals(action)) {
            handleClose(request, response, accountId);
            return;
        }

        // Unknown action fallback
        RequestUtil.setFlashToast(
                request,
                "error",
                Messages.MSG04_SYSTEM_BUSY
        );

        RequestUtil.redirect(
                request,
                response,
                "/receptionist/maintenance-requests"
        );
    }

    // ===== Tạo yêu cầu =====

    private void handleCreate(
            HttpServletRequest request,
            HttpServletResponse response,
            int accountId
    ) throws ServletException, IOException {

        int facilityId =
                RequestUtil.getIntParam(
                        request,
                        "facilityId",
                        0
                );

        String priority =
                RequestUtil.getTrimmedParam(
                        request,
                        "priority"
                );

        String description =
                RequestUtil.getTrimmedParam(
                        request,
                        "description"
                );

        boolean warningTagPlaced =
                request.getParameter("warningTagPlaced") != null;

        ServiceResult<String> result =
                maintenanceRequestService.createRequest(
                        accountId,
                        facilityId,
                        priority,
                        description,
                        warningTagPlaced
                );

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(
                    request,
                    "success",
                    "Đã gửi yêu cầu bảo trì "
                            + result.getData()
                            + "."
            );

            RequestUtil.redirect(
                    request,
                    response,
                    "/receptionist/maintenance-requests"
            );

            return;
        }

        request.setAttribute(
                "errors",
                result.getFieldErrors()
        );
        request.setAttribute(
                "formMessage",
                result.getMessage()
        );
        request.setAttribute(
                "formFacilityId",
                facilityId
        );
        request.setAttribute(
                "formPriority",
                priority
        );
        request.setAttribute(
                "formDescription",
                description
        );
        request.setAttribute(
                "formWarningTagPlaced",
                warningTagPlaced
        );

        renderPage(request, response, accountId);
    }

    // ===== Đóng yêu cầu =====

    private void handleClose(
            HttpServletRequest request,
            HttpServletResponse response,
            int accountId
    ) throws IOException {

        int requestId =
                RequestUtil.getIntParam(
                        request,
                        "requestId",
                        0
                );

        String acceptanceNote =
                RequestUtil.getTrimmedParam(
                        request,
                        "acceptanceNote"
                );

        ServiceResult<Void> result =
                maintenanceRequestService.closeRequest(
                        accountId,
                        requestId,
                        acceptanceNote
                );

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(
                    request,
                    "success",
                    "Đã nghiệm thu và đóng yêu cầu bảo trì."
            );
        } else {
            RequestUtil.setFlashToast(
                    request,
                    "error",
                    result.getMessage()
            );
        }

        RequestUtil.redirect(
                request,
                response,
                "/receptionist/maintenance-requests"
        );
    }

    // ===== Hiển thị =====

    /**
     * Renders the Receptionist maintenance management page.
     *
     * <p><b>Data Preparation:</b></p>
     * <ul>
     *   <li>Extracts multi-criteria filters: {@code keyword}, {@code status}, {@code priority}, and {@code reportedDate}.</li>
     *   <li>Retrieves selectable facilities (with calculated available & pending quantities) for the dropdown.</li>
     *   <li>Retrieves paginated requests with all-request visibility across the gym.</li>
     *   <li>Retrieves unfinished requests map and serializes it to JSON ({@code unfinishedJson}).
     *       This allows client-side JavaScript to instantaneously render warning banners and cross-links
     *       when the receptionist selects a machine, avoiding asynchronous AJAX round-trips.</li>
     * </ul>
     */
    private void renderPage(
            HttpServletRequest request,
            HttpServletResponse response,
            int accountId
    ) throws ServletException, IOException {

        // Extract and trim filter parameters
        String keyword =
                RequestUtil.getTrimmedParam(
                        request,
                        "keyword"
                );

        String status =
                RequestUtil.getTrimmedParam(
                        request,
                        "status"
                );

        String priority =
                RequestUtil.getTrimmedParam(
                        request,
                        "priority"
                );

        String reportedDateStr =
                RequestUtil.getTrimmedParam(
                        request,
                        "reportedDate"
                );

        // Safe date parsing: invalid strings gracefully default to null
        LocalDate reportedDate = null;
        if (reportedDateStr != null && !reportedDateStr.isEmpty()) {
            try {
                reportedDate = LocalDate.parse(reportedDateStr);
            } catch (Exception ignored) {
            }
        }

        // Support pre-selecting a facility if redirected from the catalog page (?facilityId=...)
        if (request.getAttribute("formFacilityId") == null) {

            int preselectedFacilityId =
                    RequestUtil.getIntParam(
                            request,
                            "facilityId",
                            0
                    );

            if (preselectedFacilityId > 0) {
                request.setAttribute(
                        "formFacilityId",
                        preselectedFacilityId
                );
            }
        }
        int page =
                PaginationUtil.getPageNumber(request);

        int pageSize =
                PaginationUtil.getPageSize(request);

        // Fetch selectable facilities with derived quantities
        ServiceResult<List<Facility>> facilityResult =
                maintenanceRequestService.getSelectableFacilities();

        // Fetch paginated requests visible to receptionist
        ServiceResult<PageResult<MaintenanceRequestDAO.MaintenanceRequestView>>
                pageResult =
                maintenanceRequestService.getReceptionistPage(
                        accountId,
                        keyword,
                        status,
                        priority,
                        reportedDate,
                        page,
                        pageSize
                );

        // Fetch active requests map for client-side warning banners
        ServiceResult<Map<Integer, List<MaintenanceRequestDAO.UnfinishedRequestSummary>>>
                unfinishedResult =
                maintenanceRequestService.getUnfinishedRequestsMap();

        Map<Integer, List<MaintenanceRequestDAO.UnfinishedRequestSummary>> unfinishedMap =
                unfinishedResult.isSuccess() && unfinishedResult.getData() != null
                        ? unfinishedResult.getData()
                        : Collections.emptyMap();

        // Set request attributes for JSP view rendering
        request.setAttribute(
                "facilities",
                facilityResult.isSuccess()
                        ? facilityResult.getData()
                        : Collections.emptyList()
        );

        request.setAttribute(
                "requestPage",
                pageResult.isSuccess()
                        ? pageResult.getData()
                        : new PageResult<>(
                        Collections.emptyList(),
                        1,
                        pageSize,
                        0
                )
        );

        request.setAttribute("unfinishedMap", unfinishedMap);
        request.setAttribute("unfinishedJson", JsonUtil.toJson(unfinishedMap));

        if (!pageResult.isSuccess()) {
            request.setAttribute(
                    "pageError",
                    pageResult.getMessage()
            );
        }

        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", status);
        request.setAttribute("priorityFilter", priority);
        request.setAttribute("reportedDateFilter", reportedDateStr);
        request.setAttribute(
                "priorityOptions",
                MaintenanceRequest.Priority.values()
        );
        request.setAttribute(
                "statusOptions",
                MaintenanceRequest.Status.values()
        );
        request.setAttribute(
                "activeNav",
                "maintenance"
        );

        RequestUtil.forward(
                request,
                response,
                VIEW_NAME
        );
    }

    private Integer getAccountId(
            HttpServletRequest request
    ) {

        HttpSession session =
                request.getSession(false);

        if (session == null) {
            return null;
        }

        return (Integer) session.getAttribute(
                SessionKey.ACCOUNT_ID
        );
    }
}