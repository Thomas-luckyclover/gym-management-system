package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dto.LeaveEmployeeView;
import com.gms.dto.LeaveRequestFilter;
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

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestListServlet() {
        this(new LeaveRequestService());
    }

    /** Truyền Service giả để kiểm tra bộ lọc mà không cần truy vấn DB thật. */
    LeaveRequestListServlet(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    /**
     * Tab Tất cả hiện nhóm nhân viên; các tab trạng thái hiện từng đơn.
     * Request attribute đặt cho JSP:
     * employeePage hoặc requestPage (Service tạo), listPage dùng chung cho phân trang,
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
        // Chỉ lọc ngày gửi khi người dùng chọn, tránh bỏ sót đơn từ ngày trước.
        int page = PaginationUtil.getPageNumber(request);
        int pageSize = PaginationUtil.getPageSize(request);

        HttpSession session = request.getSession(false);
        if (session != null) {
            request.setAttribute("bulkResult", session.getAttribute(SessionKey.LEAVE_BULK_RESULT));
            session.removeAttribute(SessionKey.LEAVE_BULK_RESULT);
        }
        Integer managerAccountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        // B2: chuẩn hóa một lần; đếm tab bỏ qua trạng thái nhưng giữ các điều kiện còn lại.
        LeaveRequestFilter filter = leaveRequestService.createManagerFilter(
                keyword, status, position, leaveType, fromDate, toDate);
        boolean isEmployeeView = filter.getStatus() == null;
        PageResult<?> listPage;
        String pageError = null;
        if (isEmployeeView) {
            ServiceResult<PageResult<LeaveEmployeeView>> result = leaveRequestService.getEmployeePage(
                    filter, managerAccountId, page, pageSize);
            listPage = result.isSuccess() ? result.getData()
                    : new PageResult<LeaveEmployeeView>(Collections.emptyList(), 1, pageSize, 0);
            request.setAttribute("employeePage", listPage);
            if (!result.isSuccess()) {
                pageError = result.getMessage();
            }
        } else {
            ServiceResult<PageResult<LeaveRequestItemView>> result = leaveRequestService.getRequestPage(
                    filter, managerAccountId, page, pageSize);
            listPage = result.isSuccess() ? result.getData()
                    : new PageResult<LeaveRequestItemView>(Collections.emptyList(), 1, pageSize, 0);
            request.setAttribute("requestPage", listPage);
            if (!result.isSuccess()) {
                pageError = result.getMessage();
            }
        }
        ServiceResult<Map<String, Integer>> countResult = leaveRequestService.getStatusCounts(filter);

        // B3: đặt dữ liệu cho JSP; lỗi thì dùng trang rỗng để màn vẫn dựng được
        request.setAttribute("listPage", listPage);
        request.setAttribute("employeeView", isEmployeeView);
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));
        String listReturnUrl = "/manager/leave-requests";
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            listReturnUrl += "?" + request.getQueryString();
        }
        request.setAttribute("listReturnUrl", listReturnUrl);

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

        if (pageError != null) {
            request.setAttribute("pageError", pageError);
        } else if (!countResult.isSuccess()) {
            request.setAttribute("pageError", countResult.getMessage());
        }

        // B4: chuyển sang JSP danh sách
        RequestUtil.forward(request, response, VIEW_NAME);
    }
}
