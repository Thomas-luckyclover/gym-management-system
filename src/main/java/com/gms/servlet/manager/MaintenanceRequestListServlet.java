package com.gms.servlet.manager;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.MaintenanceRequest;
import com.gms.service.MaintenanceRequestService;
import com.gms.service.NotificationService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Presentation Layer (Servlet Controller): Manages HTTP interactions for the Manager's
 * maintenance request review dashboard (UC-29 / BF-06).
 *
 * <p><b>Architectural Responsibility:</b></p>
 * Handles request routing, multi-criteria filter extraction (keyword, status, priority, and date),
 * aggregates system-wide status counts for operational badges, and forwards paginated results
 * to the manager JSP view.
 */
@WebServlet("/manager/maintenance-requests")
public class MaintenanceRequestListServlet extends HttpServlet {

    private static final String VIEW_NAME =
            "manager/maintenance-request-list";

    private final MaintenanceRequestService maintenanceRequestService =
            new MaintenanceRequestService();

    private final NotificationService notificationService =
            new NotificationService();

    /**
     * Handles GET requests to display the filtered and paginated maintenance requests list for Managers.
     */
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        HttpSession session = request.getSession(true);
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));

        // Tự động đánh dấu đã đọc các thông báo bảo trì của Quản lý này khi vào trang
        Integer currentAccountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        if (currentAccountId != null && currentAccountId > 0) {
            notificationService.markTopicNotificationsRead(currentAccountId, "Bảo trì");
        }

        // Extract and sanitize search and filter parameters
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

        LocalDate reportedDate = null;
        if (reportedDateStr != null && !reportedDateStr.isEmpty()) {
            try {
                reportedDate = LocalDate.parse(reportedDateStr);
            } catch (Exception ignored) {
            }
        }

        int page =
                PaginationUtil.getPageNumber(request);

        int pageSize =
                PaginationUtil.getPageSize(request);

        ServiceResult<PageResult<MaintenanceRequestDAO.MaintenanceRequestView>>
                pageResult =
                maintenanceRequestService.getManagerPage(
                        keyword,
                        status,
                        priority,
                        reportedDate,
                        page,
                        pageSize
                );

        ServiceResult<Map<String, Integer>> countResult =
                maintenanceRequestService.getStatusCounts();
        int totalStatusCount = 0;

        if (countResult.isSuccess()
                && countResult.getData() != null) {

            for (Integer count : countResult.getData().values()) {
                if (count != null) {
                    totalStatusCount += count;
                }
            }
        }
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

        request.setAttribute(
                "statusCounts",
                countResult.isSuccess()
                        ? countResult.getData()
                        : Collections.emptyMap()
        );
        request.setAttribute(
                "totalStatusCount",
                totalStatusCount
        );
        request.setAttribute(
                "statusOptions",
                MaintenanceRequest.Status.values()
        );

        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", status);
        request.setAttribute("priorityFilter", priority);
        request.setAttribute("reportedDateFilter", reportedDateStr);
        request.setAttribute(
                "priorityOptions",
                MaintenanceRequest.Priority.values()
        );
        request.setAttribute("activeNav", "maintenance");

        if (!pageResult.isSuccess()) {
            request.setAttribute(
                    "pageError",
                    pageResult.getMessage()
            );
        }

        RequestUtil.forward(
                request,
                response,
                VIEW_NAME
        );
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (!RequestUtil.isValidCsrfToken(request)) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG214_FORM_SESSION_EXPIRED);
            RequestUtil.redirect(request, response, "/manager/maintenance-requests");
            return;
        }

        String action = RequestUtil.getTrimmedParam(request, "action");
        if ("batchApprove".equals(action)) {
            String rawIds = RequestUtil.getTrimmedParam(request, "requestIds");
            List<Integer> requestIds = new ArrayList<>();

            if (rawIds != null && !rawIds.isBlank()) {
                for (String part : rawIds.split(",")) {
                    try {
                        int id = Integer.parseInt(part.trim());
                        if (id > 0 && !requestIds.contains(id)) {
                            requestIds.add(id);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            String[] multiIds = request.getParameterValues("requestIds");
            if (multiIds != null) {
                for (String mid : multiIds) {
                    try {
                        int id = Integer.parseInt(mid.trim());
                        if (id > 0 && !requestIds.contains(id)) {
                            requestIds.add(id);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            boolean setUnderMaintenance = request.getParameter("setUnderMaintenance") != null;
            String managerNote = RequestUtil.getTrimmedParam(request, "managerNote");

            ServiceResult<Integer> result = maintenanceRequestService.approveRequestsBatch(
                    accountId,
                    requestIds,
                    setUnderMaintenance,
                    managerNote
            );

            if (result.isSuccess()) {
                RequestUtil.setFlashToast(
                        request,
                        "success",
                        String.format(Messages.MSG347_MAINTENANCE_BATCH_APPROVE_SUCCESS, result.getData())
                );
            } else {
                RequestUtil.setFlashToast(
                        request,
                        "error",
                        result.getMessage()
                );
            }

            RequestUtil.redirect(request, response, "/manager/maintenance-requests");
            return;
        }

        RequestUtil.redirect(request, response, "/manager/maintenance-requests");
    }

}
