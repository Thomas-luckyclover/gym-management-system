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
import java.util.List;
import java.util.Map;

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

        // Đọc từ khóa tìm kiếm và khu vực lọc
        String keyword = RequestUtil.getTrimmedParam(request, "keyword");
        String location = RequestUtil.getTrimmedParam(request, "location");

        int page = PaginationUtil.getPageNumber(request);
        int pageSize = 30; // Hiển thị đầy đủ lưới các nhóm máy

        ServiceResult<PageResult<Facility>> result =
                maintenanceRequestService.getFacilityCatalogPage(
                        keyword,
                        page,
                        pageSize
                );

        // Lấy danh sách vị trí để hiển thị bộ lọc chip tags
        ServiceResult<List<String>> locationsResult = maintenanceRequestService.getDistinctLocations();
        request.setAttribute("locationOptions", locationsResult.isSuccess() ? locationsResult.getData() : Collections.emptyList());
        request.setAttribute("selectedLocation", location);

        // Đếm số đơn bảo trì đang mở (Open + InProgress) để hiển thị trên nút góc trên
        ServiceResult<Map<String, Integer>> statusCountsResult = maintenanceRequestService.getStatusCounts();
        int openCount = 0;
        if (statusCountsResult.isSuccess() && statusCountsResult.getData() != null) {
            Map<String, Integer> counts = statusCountsResult.getData();
            openCount = counts.getOrDefault("Open", 0) + counts.getOrDefault("InProgress", 0);
        }
        request.setAttribute("openRequestsCount", openCount);

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