package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.service.AuthService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Màn chọn / đổi vai trò khi tài khoản có từ 2 vai trò (UC-01, CBR-113). */
@WebServlet("/select-role")
public class SelectRoleServlet extends HttpServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer accountId = currentAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B1: lấy lại vai trò từ DB (CBR-86)
        List<Role> roles = authService.findActiveRoles(accountId);
        if (roles.isEmpty()) {
            forceLogout(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        session.setAttribute(SessionKey.AVAILABLE_ROLES, joinRoles(roles));

        // B2: chỉ còn 1 vai trò → không cần chọn
        if (roles.size() == 1) {
            session.setAttribute(SessionKey.ROLE, roles.get(0).getDbValue());
            RequestUtil.redirect(request, response, roles.get(0).getHomePath());
            return;
        }

        // B3: hiện màn chọn
        request.setAttribute("roles", roles);
        request.setAttribute("currentRole", session.getAttribute(SessionKey.ROLE));
        String returnUrl = RequestUtil.getTrimmedParam(request, "returnUrl");
        if (RequestUtil.isSafeReturnUrl(returnUrl)) {
            request.setAttribute("returnUrl", returnUrl);
        }
        RequestUtil.forward(request, response, "auth/select-role");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer accountId = currentAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B1: vai trò người dùng bấm chọn
        Role chosen = Role.fromDbValue(RequestUtil.getTrimmedParam(request, "role"));

        // B2: kiểm vai trò chọn có thuộc tài khoản (GBR-13)
        List<Role> roles = authService.findActiveRoles(accountId);
        if (roles.isEmpty()) {
            forceLogout(request, response);
            return;
        }
        if (chosen == null || !roles.contains(chosen)) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG07_NO_PERMISSION);
            RequestUtil.redirect(request, response, AuthService.SELECT_ROLE_PATH);
            return;
        }

        // B3: lưu vai trò đang dùng vào session
        HttpSession session = request.getSession(false);
        session.setAttribute(SessionKey.ROLE, chosen.getDbValue());
        session.setAttribute(SessionKey.AVAILABLE_ROLES, joinRoles(roles));
        session.removeAttribute(SessionKey.PENDING_CONTACT_FIELD);
        session.removeAttribute(SessionKey.PENDING_CONTACT_VALUE);
        session.removeAttribute(SessionKey.PENDING_CONTACT_VERIFY_EMAIL);

        // B4: về returnUrl cùng khu vai trò, không thì về trang chủ
        String returnUrl = RequestUtil.getTrimmedParam(request, "returnUrl");
        String target = isInRoleArea(returnUrl, chosen) ? returnUrl : chosen.getHomePath();
        RequestUtil.redirect(request, response, target);
    }

    // ===== Hàm phụ =====

    /** Ghép vai trò thành chuỗi lưu session. */
    public static String joinRoles(List<Role> roles) {
        StringJoiner joiner = new StringJoiner(",");
        for (Role role : roles) {
            joiner.add(role.getDbValue());
        }
        return joiner.toString();
    }

    /** Tách chuỗi vai trò trong session. */
    public static List<Role> splitRoles(String value) {
        List<Role> roles = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return roles;
        }
        for (String part : value.split(",")) {
            Role role = Role.fromDbValue(part.trim());
            if (role != null) {
                roles.add(role);
            }
        }
        return roles;
    }

    /** returnUrl có thuộc khu của vai trò không. */
    private boolean isInRoleArea(String returnUrl, Role role) {
        if (!RequestUtil.isSafeReturnUrl(returnUrl)) {
            return false;
        }
        String home = role.getHomePath();
        int secondSlash = home.indexOf('/', 1);
        String area = secondSlash > 0 ? home.substring(0, secondSlash + 1) : home;
        return returnUrl.startsWith(area);
    }

    private Integer currentAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /** Hết vai trò → hủy phiên, báo MSG37 (CBR-86). */
    private void forceLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        RequestUtil.setFlashToast(request, "error", Messages.MSG37_ACCOUNT_INACTIVE);
        RequestUtil.redirect(request, response, "/login");
    }
}
