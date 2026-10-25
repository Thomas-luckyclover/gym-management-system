package com.gms.servlet.receptionist;

import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Facility;
import com.gms.entity.MaintenanceRequest;
import com.gms.service.MaintenanceRequestService;
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

@WebServlet("/receptionist/facilities")
public class FacilityCatalogServlet
        extends HttpServlet {

    private static final String VIEW_NAME =
            "receptionist/facility-catalog";

    private final MaintenanceRequestService
            maintenanceRequestService =
            new MaintenanceRequestService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        // Read single keyword search parameter (obsolete category/status parameters are safely ignored)
        String keyword =
                RequestUtil.getTrimmedParam(
                        request,
                        "keyword"
                );

        int page =
                PaginationUtil.getPageNumber(
                        request
                );

        int pageSize =
                PaginationUtil.getPageSize(
                        request
                );

        ServiceResult<PageResult<Facility>> result =
                maintenanceRequestService.getFacilityCatalogPage(
                        keyword,
                        page,
                        pageSize
                );

        request.setAttribute(
                "facilityPage",
                result.isSuccess()
                        ? result.getData()
                        : new PageResult<>(
                        Collections.emptyList(),
                        1,
                        pageSize,
                        0
                )
        );

        request.setAttribute(
                "keyword",
                keyword
        );

        request.setAttribute(
                "activeNav",
                "equipment"
        );

        request.setAttribute(
                "priorityOptions",
                MaintenanceRequest.Priority.values()
        );

        request.setAttribute(
                "incidentTypeOptions",
                MaintenanceRequest.IncidentType.values()
        );

        request.setAttribute(
                "detectedByOptions",
                MaintenanceRequest.DetectedBy.values()
        );

        HttpSession session = request.getSession(true);
        String csrfToken = RequestUtil.getOrCreateCsrfToken(session);
        request.setAttribute("csrfToken", csrfToken);

        if (!result.isSuccess()) {
            request.setAttribute(
                    "pageError",
                    result.getMessage()
            );
        }

        RequestUtil.forward(
                request,
                response,
                VIEW_NAME
        );
    }
}