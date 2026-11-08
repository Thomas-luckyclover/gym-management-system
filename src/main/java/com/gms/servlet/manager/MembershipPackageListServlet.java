package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dto.ManagerPackageRowView;
import com.gms.dto.ServiceResult;
import com.gms.service.PackageService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Controller màn hình Danh sách gói hội viên & vé ngày (#17 - UC-10).
 * Cho phép xem danh sách gói tập, tìm kiếm theo tên, lọc trạng thái, và bật/tắt ngừng bán/mở bán lại.
 */
@WebServlet(urlPatterns = {"/manager/membership-packages"})
public class MembershipPackageListServlet extends HttpServlet {

    private final PackageService packageService = new PackageService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String keyword = RequestUtil.getTrimmedParam(request, "keyword");
        String status = RequestUtil.getTrimmedParam(request, "status");
        if (status == null || status.isBlank()) {
            status = "all";
        }

        ServiceResult<List<ManagerPackageRowView>> result = packageService.getMembershipPackages(keyword, status);
        List<ManagerPackageRowView> packages = result.getData();

        String csrfToken = RequestUtil.getOrCreateCsrfToken(session);
        request.setAttribute("csrfToken", csrfToken);
        request.setAttribute("packages", packages);
        request.setAttribute("keyword", keyword != null ? keyword : "");
        request.setAttribute("status", status);
        request.setAttribute("activeNav", "packages");
        request.setAttribute("pageTitle", "Danh mục gói hội viên · Super Gym Quản lý");

        request.getRequestDispatcher("/WEB-INF/jsp/manager/membership-package-list.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        if (!RequestUtil.isValidCsrfToken(request)) {
            RequestUtil.setFlashToast(request, "danger", "Phiên làm việc đã hết hạn hoặc mã bảo mật CSRF không hợp lệ.");
            response.sendRedirect(request.getContextPath() + "/manager/membership-packages");
            return;
        }

        int accountId = (int) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action");
        String keyword = RequestUtil.getTrimmedParam(request, "keyword");
        String status = RequestUtil.getTrimmedParam(request, "status");

        if ("toggleStatus".equalsIgnoreCase(action)) {
            int packageId = RequestUtil.getIntParam(request, "packageId", 0);
            boolean targetActive = Boolean.parseBoolean(RequestUtil.getTrimmedParam(request, "targetActive"));
            String rowVer = RequestUtil.getTrimmedParam(request, "rowVersion");
            String reason = RequestUtil.getTrimmedParam(request, "reason");

            ServiceResult<Void> toggleResult = packageService.togglePackageStatus(
                    accountId,
                    packageId,
                    targetActive,
                    rowVer,
                    reason
            );

            if (toggleResult.isSuccess()) {
                RequestUtil.setFlashToast(request, "success", toggleResult.getMessage());
            } else {
                RequestUtil.setFlashToast(request, "danger", toggleResult.getMessage());
            }
        }

        StringBuilder redirectUrl = new StringBuilder(request.getContextPath()).append("/manager/membership-packages?");
        if (keyword != null && !keyword.isBlank()) {
            redirectUrl.append("keyword=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8)).append("&");
        }
        if (status != null && !status.isBlank()) {
            redirectUrl.append("status=").append(URLEncoder.encode(status, StandardCharsets.UTF_8));
        }

        response.sendRedirect(redirectUrl.toString());
    }
}
