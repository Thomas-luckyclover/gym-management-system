package com.gms.servlet.manager;

import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.MaintenanceRequest;
import com.gms.service.MaintenanceRequestService;
import com.gms.util.PaginationUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
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

    /**
     * Handles GET requests to display the filtered and paginated maintenance requests list for Managers.
     */
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

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
}