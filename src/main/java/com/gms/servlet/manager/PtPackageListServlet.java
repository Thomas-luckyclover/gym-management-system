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
 * Controller màn hình Danh sách gói PT & Buổi lẻ (#19 - UC-10).
 * Cho phép xem danh sách gói tập với PT, tìm kiếm, lọc theo phân loại (PT 1:1, PT 1:2, Buổi lẻ), và đổi trạng thái bán.
 */
@WebServlet(urlPatterns = {"/manager/pt-packages"})
public class PtPackageListServlet extends HttpServlet {

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
        String ptType = RequestUtil.getTrimmedParam(request, "ptType");
        if (ptType == null || ptType.isBlank()) {
            ptType = "ALL";
        }

        ServiceResult<List<ManagerPackageRowView>> result = packageService.getPtPackages(keyword, status, ptType);
        List<ManagerPackageRowView> packages = result.getData();

        String csrfToken = RequestUtil.getOrCreateCsrfToken(session);
        request.setAttribute("csrfToken", csrfToken);
        request.setAttribute("packages", packages);
        request.setAttribute("keyword", keyword != null ? keyword : "");
        request.setAttribute("status", status);
        request.setAttribute("ptType", ptType);
        request.setAttribute("activeNav", "packages");
        request.setAttribute("pageTitle", "Danh mục gói PT & Huấn luyện viên · Super Gym Quản lý");

        request.getRequestDispatcher("/WEB-INF/jsp/manager/pt-package-list.jsp")
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
            response.sendRedirect(request.getContextPath() + "/manager/pt-packages");
            return;
        }

        int accountId = (int) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action");
        String keyword = RequestUtil.getTrimmedParam(request, "keyword");
        String status = RequestUtil.getTrimmedParam(request, "status");
        String ptType = RequestUtil.getTrimmedParam(request, "ptType");

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

        StringBuilder redirectUrl = new StringBuilder(request.getContextPath()).append("/manager/pt-packages?");
        if (keyword != null && !keyword.isBlank()) {
            redirectUrl.append("keyword=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8)).append("&");
        }
        if (status != null && !status.isBlank()) {
            redirectUrl.append("status=").append(URLEncoder.encode(status, StandardCharsets.UTF_8)).append("&");
        }
        if (ptType != null && !ptType.isBlank()) {
            redirectUrl.append("ptType=").append(URLEncoder.encode(ptType, StandardCharsets.UTF_8));
        }

        response.sendRedirect(redirectUrl.toString());
    }
}
