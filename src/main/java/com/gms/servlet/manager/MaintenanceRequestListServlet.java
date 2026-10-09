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
import java.util.Collections;
import java.util.Map;

/**
 * Controller danh sách yêu cầu bảo trì cho Manager (UC-29).
 */
@WebServlet("/manager/maintenance-requests")
public class MaintenanceRequestListServlet extends HttpServlet {

    private static final String VIEW_NAME =
            "manager/maintenance-request-list";

    private final MaintenanceRequestService maintenanceRequestService =
            new MaintenanceRequestService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

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

        int page =
                PaginationUtil.getPageNumber(request);

        int pageSize =
                PaginationUtil.getPageSize(request);

        ServiceResult<PageResult<MaintenanceRequestDAO.MaintenanceRequestView>>
                pageResult =
                maintenanceRequestService.getManagerPage(
                        keyword,
                        status,
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