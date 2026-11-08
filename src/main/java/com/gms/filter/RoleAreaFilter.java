package com.gms.filter;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.service.AuthService;
import com.gms.servlet.auth.SelectRoleServlet;
import com.gms.util.JsonUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Khung chung cho các Filter phân quyền theo khu vực URL (ví dụ /customer/*, /manager/*, /admin/*…).
 *
 * <p>Mỗi khu vực có 1 Filter con riêng (CustomerAreaFilter, ManagerAreaFilter, AdminAreaFilter…) khai báo vai trò được phép.</p>
 * <p>Filter này tự kiểm tra cả đăng nhập, không phụ thuộc thứ tự chạy với AuthFilter.</p>
 */
public abstract class RoleAreaFilter implements Filter {

    /**
     * Vai trò được vào khu vực này — Filter con khai báo.
     * @return vai trò được phép
     */
    protected abstract Role getAllowedRole();

    /**
     * Lọc request: chưa đăng nhập → nhờ AuthFilter xử lý; đúng vai trò → cho qua;
     * sai vai trò → AJAX nhận 403, trang thường được đưa về màn Chọn vai trò / trang chính kèm lời nhắn.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);

        // B1: chưa đăng nhập → giao cho AuthFilter xử lý (redirect login / JSON 401)
        boolean isLoggedIn = session != null && session.getAttribute(SessionKey.ACCOUNT_ID) != null;
        if (!isLoggedIn) {
            chain.doFilter(request, response);
            return;
        }

        // B2: đọc vai trò trong session, so với vai trò được phép
        String roleValue = (String) session.getAttribute(SessionKey.ROLE);
        Role currentRole = Role.fromDbValue(roleValue);
        boolean isAllowed = currentRole == getAllowedRole();

        // B3: đúng vai trò → cho đi tiếp
        if (isAllowed) {
            chain.doFilter(request, response);
            return;
        }

        // B4: sai vai trò + AJAX → JSON 403 (JS tự báo lỗi)
        if (RequestUtil.isAjaxRequest(httpRequest)) {
            JsonResponse body = JsonResponse.error(Messages.MSG07_NO_PERMISSION);
            JsonUtil.writeJson(httpResponse, HttpServletResponse.SC_FORBIDDEN, body);
            return;
        }

        // B5: sai vai trò + trang thường → KHÔNG hiện trang 403 trống, mà đưa về đúng chỗ kèm lời nhắn:
        //     tài khoản có vai trò của trang → màn Chọn vai trò (chọn xong quay lại đúng trang)
        //     không có (thường do vừa đăng nhập tài khoản khác ở tab khác) → trang chính của vai trò đang dùng
        Role areaRole = getAllowedRole();
        List<Role> ownRoles = SelectRoleServlet.splitRoles((String) session.getAttribute(SessionKey.AVAILABLE_ROLES));
        if (ownRoles.contains(areaRole) || currentRole == null) {
            RequestUtil.setFlashToast(httpRequest, "info", String.format(Messages.MSG339_SWITCH_ROLE_TO_OPEN, areaRole.getDisplayName()));
            RequestUtil.redirect(httpRequest, httpResponse, AuthService.SELECT_ROLE_PATH + returnUrlParam(httpRequest));
            return;
        }
        RequestUtil.setFlashToast(httpRequest, "info", Messages.MSG340_PAGE_OF_OTHER_ACCOUNT);
        RequestUtil.redirect(httpRequest, httpResponse, currentRole.getHomePath());
    }

    /**
     * HÀM PHỤ (doFilter B5) — "?returnUrl=/customer/bookings%3Fpackage%3D82" để chọn vai trò xong quay lại đúng trang.
     * Chỉ GET mới quay lại (POST cũ từ tab trước không gửi lại); URL không an toàn → bỏ.
     */
    private String returnUrlParam(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return "";
        }
        String query = request.getQueryString();
        String url = RequestUtil.getPathWithinApp(request) + (query == null ? "" : "?" + query);
        if (!RequestUtil.isSafeReturnUrl(url)) {
            return "";
        }
        return "?returnUrl=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
    }
}
