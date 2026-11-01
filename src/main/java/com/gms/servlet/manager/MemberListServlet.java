package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDeletionService;
import com.gms.util.PaginationUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.gms.dto.CustomerDeletionReviewView;
import com.gms.dto.CustomerProfileRowView;
import com.gms.dto.PageResult;

/** BF-07: đọc bộ lọc, gọi Service và đưa một trang kết quả sang JSP. */
@WebServlet("/manager/members")
public class MemberListServlet extends HttpServlet {

    private final CustomerDeletionService service = new CustomerDeletionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // B1: đăng nhập và vai trò đã được AuthFilter/ManagerAreaFilter kiểm tra.
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        int page = PaginationUtil.getPageNumber(request);
        int pageSize = 10; // BF-07 cố định 10 hồ sơ mỗi trang.
        String tab = RequestUtil.getTrimmedParam(request, "tab");
        String keyword = RequestUtil.getTrimmedParam(request, "keyword");
        String status = service.normalizeStatus(RequestUtil.getTrimmedParam(request, "status"), true);

        // B2: mỗi tab nhận một PageResult; lỗi được hiển thị riêng với danh sách rỗng.
        if ("pending-deletions".equals(tab)) {
            ServiceResult<PageResult<CustomerDeletionReviewView>> result = service.getPendingPage(accountId, page, pageSize);
            if (result.isSuccess()) {
                request.setAttribute("pageData", result.getData());
                request.setAttribute("pendingCount", result.getData().getTotalItems());
            } else {
                request.setAttribute("loadError", result.getMessage());
            }
        } else {
            tab = "all";
            ServiceResult<PageResult<CustomerProfileRowView>> result = service.getProfilesPage(accountId, keyword, status, true, page, pageSize);
            if (result.isSuccess()) {
                request.setAttribute("pageData", result.getData());
            } else {
                request.setAttribute("loadError", result.getMessage());
            }
            ServiceResult<Integer> count = service.getPendingCount();
            if (count.isSuccess()) {
                request.setAttribute("pendingCount", count.getData());
            }
        }

        // B3: các đường dẫn do Servlet đặt, JSP không tự đoán vai trò theo URL.
        request.setAttribute("managerView", true);
        request.setAttribute("currentTab", tab);
        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", status);
        request.setAttribute("pageSize", pageSize);
        request.setAttribute("listPath", "/manager/members");
        request.setAttribute("detailPath", "/manager/members/detail");
        request.setAttribute("activeNav", "members");
        RequestUtil.forward(request, response, "manager/member-list");
    }
}
