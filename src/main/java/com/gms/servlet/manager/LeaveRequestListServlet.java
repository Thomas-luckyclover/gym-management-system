package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dto.LeaveEmployeeView;
import com.gms.dto.LeaveRequestFilter;
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
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Controller danh sách đơn xin nghỉ phép cho Manager (màn #26, UC-15).
 */
@WebServlet("/manager/leave-requests")
public class LeaveRequestListServlet extends HttpServlet {

    private static final String VIEW_NAME = "manager/leave-request-list";

    private final LeaveRequestService leaveRequestService
            = new LeaveRequestService();

    /**
     * Hiện nhân viên và các đơn nghỉ phép mở rộng bên dưới.
     * Request attribute đặt cho JSP:
     * employeePage (PageResult của LeaveEmployeeView, Service tạo),
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

        HttpSession session = request.getSession(false);
        Integer managerAccountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        // B2: chuẩn hóa một lần; đếm tab bỏ qua trạng thái nhưng giữ các điều kiện còn lại.
        LeaveRequestFilter filter = leaveRequestService.createManagerFilter(
                keyword, status, position, leaveType, fromDate, toDate);
        ServiceResult<PageResult<LeaveEmployeeView>> pageResult = leaveRequestService.getEmployeePage(
                filter, managerAccountId, page, pageSize);
        ServiceResult<Map<String, Integer>> countResult = leaveRequestService.getStatusCounts(filter);

        // B3: đặt dữ liệu cho JSP; lỗi thì dùng trang rỗng để màn vẫn dựng được
        request.setAttribute(
                "employeePage",
                pageResult.isSuccess()
                        ? pageResult.getData()
                        : new PageResult<LeaveEmployeeView>(
                                Collections.emptyList(), 1, pageSize, 0));

        request.setAttribute(
                "statusCounts",
                countResult.isSuccess()
                        ? countResult.getData()
                        : Collections.emptyMap());

        request.setAttribute("keyword", filter.getKeyword());
        request.setAttribute("positionFilter", filter.getPosition());
        request.setAttribute("leaveTypeFilter", filter.getLeaveType() == null ? null : filter.getLeaveType().getDbValue());
        request.setAttribute("fromDateFilter", filter.getFromDate());
        request.setAttribute("toDateFilter", filter.getToDate());
        request.setAttribute("statusFilter", filter.getStatus() == null ? "All" : filter.getStatus().getDbValue());

        request.setAttribute("activeNav", "leaves");

        if (!pageResult.isSuccess()) {
            request.setAttribute("pageError", pageResult.getMessage());
        } else if (!countResult.isSuccess()) {
            request.setAttribute("pageError", countResult.getMessage());
        }

        // B4: chuyển sang JSP danh sách
        RequestUtil.forward(request, response, VIEW_NAME);
    }
}
