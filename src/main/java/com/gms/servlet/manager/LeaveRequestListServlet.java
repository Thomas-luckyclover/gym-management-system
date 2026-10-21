package com.gms.servlet.manager;

import com.gms.dto.LeaveRequestItemView;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.service.LeaveRequestService;
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
 * Controller danh sách đơn xin nghỉ phép cho Manager (màn #26, UC-15).
 */
@WebServlet("/manager/leave-requests")
public class LeaveRequestListServlet extends HttpServlet {

    private static final String VIEW_NAME = "manager/leave-request-list";

    // Tab mặc định của màn khi URL không có status (SRS 5.2.2)
    private static final String DEFAULT_STATUS_TAB = "Pending";

    private final LeaveRequestService leaveRequestService
            = new LeaveRequestService();

    /**
     * Hiện danh sách đơn nghỉ phép.
     * Request attribute đặt cho JSP:
     * leavePage (PageResult của LeaveRequestItemView, Service tạo),
     * statusCounts (Map số đơn theo All/Pending/Approved/Rejected, Service tạo),
     * keyword và statusFilter (giá trị người dùng đang chọn, để giữ ô tìm và tab),
     * pageError (chỉ có khi tải danh sách thất bại).
     */
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        // B1: đọc tham số lọc và phân trang từ URL
        String keyword = RequestUtil.getTrimmedParam(request, "keyword");
        String status = RequestUtil.getTrimmedParam(request, "status");
        String position = RequestUtil.getTrimmedParam(request, "position");
        String leaveType = RequestUtil.getTrimmedParam(request, "leaveType");
        String fromDate = RequestUtil.getTrimmedParam(request, "fromDate");
        String toDate = RequestUtil.getTrimmedParam(request, "toDate");
        int page = PaginationUtil.getPageNumber(request);
        int pageSize = PaginationUtil.getPageSize(request);

        jakarta.servlet.http.HttpSession session = request.getSession(false);
        Integer managerAccountId = session == null
                ? null
                : (Integer) session.getAttribute(com.gms.constant.SessionKey.ACCOUNT_ID);

        // B2: lấy một trang đơn và số đơn của các tab
        ServiceResult<PageResult<LeaveRequestItemView>> pageResult
                = leaveRequestService.getManagerPage(
                        keyword, status, position, leaveType, fromDate, toDate, managerAccountId, page, pageSize);

        com.gms.dto.LeaveRequestFilter countFilter = new com.gms.dto.LeaveRequestFilter(
                null, keyword, position,
                (leaveType == null || leaveType.isBlank() || "All".equalsIgnoreCase(leaveType)) ? null : com.gms.entity.LeaveRequest.LeaveType.fromDbValue(leaveType),
                null, null);
        ServiceResult<Map<String, Integer>> countResult
                = leaveRequestService.getStatusCounts(countFilter);

        // B3: đặt dữ liệu cho JSP; lỗi thì dùng trang rỗng để màn vẫn dựng được
        request.setAttribute(
                "leavePage",
                pageResult.isSuccess()
                        ? pageResult.getData()
                        : new PageResult<LeaveRequestItemView>(
                                Collections.emptyList(), 1, pageSize, 0));

        request.setAttribute(
                "statusCounts",
                countResult.isSuccess()
                        ? countResult.getData()
                        : Collections.emptyMap());

        request.setAttribute("keyword", keyword);
        request.setAttribute("positionFilter", position);
        request.setAttribute("leaveTypeFilter", leaveType);
        request.setAttribute("fromDateFilter", fromDate);
        request.setAttribute("toDateFilter", toDate);

        // URL trống nghĩa là tab mặc định "Pending" (khớp parseStatusFilter của Service)
        boolean hasStatus = status != null && !status.isEmpty();
        request.setAttribute(
                "statusFilter", hasStatus ? status : DEFAULT_STATUS_TAB);

        request.setAttribute("activeNav", "leaves");

        if (!pageResult.isSuccess()) {
            request.setAttribute("pageError", pageResult.getMessage());
        }

        // B4: chuyển sang JSP danh sách
        RequestUtil.forward(request, response, VIEW_NAME);
    }
}
