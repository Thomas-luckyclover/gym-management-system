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

/**
 * MÀN CHỌN VAI TRÒ — /select-role
 * GET  → doGet:  hiện các vai trò của tài khoản (select-role.jsp)
 * POST → doPost: bấm 1 vai trò → lưu vào session → về trang chủ vai trò đó
 * Lấy vai trò: AuthService.findActiveRoles → AccountDAO → trả List<Role> (không có DTO riêng)
 */
@WebServlet("/select-role")
public class SelectRoleServlet extends HttpServlet {
    private final AuthService authService = new AuthService();

    // GET /select-role: hiện màn chọn vai trò.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chưa đăng nhập → về /login
        Integer accountId = currentAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B2: đọc vai trò còn hiệu lực: AuthService.findActiveRoles → List<Role>
        //     rỗng (tài khoản bị khóa) → forceLogout: hủy session + MSG37 + về /login
        List<Role> roles = authService.findActiveRoles(accountId);
        if (roles.isEmpty()) {
            forceLogout(request, response);
            return;
        }

        // B3: lưu "PT,Customer" vào session → menu tài khoản biết có nút "Đổi vai trò"
        HttpSession session = request.getSession(false);
        session.setAttribute(SessionKey.AVAILABLE_ROLES, joinRoles(roles));

        // B4: chỉ 1 vai trò → chọn luôn, về trang chủ vai trò đó (VD /pt/dashboard)
        if (roles.size() == 1) {
            session.setAttribute(SessionKey.ROLE, roles.get(0).getDbValue());
            RequestUtil.redirect(request, response, roles.get(0).getHomePath());
            return;
        }

        // B5: nhiều vai trò → gửi sang JSP:
        //     roles = List<Role> (mỗi vai trò 1 nút) · currentRole = vai trò đang dùng (nhãn "Đang dùng")
        //     chooseRoleMessage = MSG74 · returnUrl = trang định vào trước đó (chỉ nhận URL nội bộ)
        request.setAttribute("roles", roles);
        request.setAttribute("currentRole", session.getAttribute(SessionKey.ROLE));
        request.setAttribute("chooseRoleMessage", Messages.MSG74_CHOOSE_ROLE);
        String returnUrl = RequestUtil.getTrimmedParam(request, "returnUrl");
        if (RequestUtil.isSafeReturnUrl(returnUrl)) {
            request.setAttribute("returnUrl", returnUrl);
        }

        // B6: hiện select-role.jsp
        RequestUtil.forward(request, response, "auth/select-role");
    }

    // POST /select-role (role, returnUrl): lưu vai trò đã chọn.
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chưa đăng nhập → về /login
        Integer accountId = currentAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B2: sai CSRF → toast MSG214, quay lại /select-role
        if (RequestUtil.rejectInvalidCsrf(request, response, "/select-role")) {
            return;
        }

        // B3: ô ẩn role ("PT") → enum Role (giá trị lạ → null)
        Role chosen = Role.fromDbValue(RequestUtil.getTrimmedParam(request, "role"));

        // B4: đọc lại vai trò từ DB (không tin form); tài khoản bị khóa giữa chừng → forceLogout
        List<Role> roles = authService.findActiveRoles(accountId);
        if (roles.isEmpty()) {
            forceLogout(request, response);
            return;
        }

        // B5: chọn vai trò mình không có (sửa F12) → MSG07, quay lại màn chọn
        if (chosen == null || !roles.contains(chosen)) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG07_NO_PERMISSION);
            RequestUtil.redirect(request, response, AuthService.SELECT_ROLE_PATH);
            return;
        }

        HttpSession session = request.getSession(false);

        // B6: lưu vai trò vào session; trước đó đã có vai trò = đang ĐỔI → toast MSG75
        boolean switching = session.getAttribute(SessionKey.ROLE) != null;
        session.setAttribute(SessionKey.ROLE, chosen.getDbValue());
        if (switching) {
            RequestUtil.setFlashToast(request, "success", String.format(Messages.MSG75_ROLE_SWITCHED, chosen.getDisplayName()));
        }

        // B7: cập nhật danh sách vai trò + xóa dữ liệu "đổi email/SĐT đang chờ" của vai trò cũ
        session.setAttribute(SessionKey.AVAILABLE_ROLES, joinRoles(roles));
        session.removeAttribute(SessionKey.PENDING_CONTACT_FIELD);
        session.removeAttribute(SessionKey.PENDING_CONTACT_VALUE);
        session.removeAttribute(SessionKey.PENDING_CONTACT_VERIFY_EMAIL);

        // B8: returnUrl thuộc khu vực vai trò mới → về đó, không thì về trang chủ vai trò
        String returnUrl = RequestUtil.getTrimmedParam(request, "returnUrl");
        String target = isInRoleArea(returnUrl, chosen) ? returnUrl : chosen.getHomePath();
        RequestUtil.redirect(request, response, target);
    }

    /**
     * HÀM PHỤ (doGet B3, doPost B7) — [PT, Customer] → "PT,Customer" để lưu session.
     */
    public static String joinRoles(List<Role> roles) {
        StringJoiner joiner = new StringJoiner(",");
        for (Role role : roles) {
            joiner.add(role.getDbValue());
        }
        return joiner.toString();
    }

    /**
     * HÀM PHỤ (menu tài khoản) — "PT,Customer" → [PT, Customer]; bỏ giá trị lạ.
     */
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

    /**
     * HÀM PHỤ (doPost B8) — returnUrl có thuộc khu vực vai trò không (PT → "/pt/"); trả true = được quay về.
     */
    private boolean isInRoleArea(String returnUrl, Role role) {
        if (!RequestUtil.isSafeReturnUrl(returnUrl)) {
            return false;
        }
        String home = role.getHomePath();
        int secondSlash = home.indexOf('/', 1);
        String area = secondSlash > 0 ? home.substring(0, secondSlash + 1) : home;
        return returnUrl.startsWith(area);
    }

    /**
     * HÀM PHỤ — AccountId trong session; null = chưa đăng nhập.
     */
    private Integer currentAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /**
     * HÀM PHỤ — hủy session + toast MSG37 + về /login.
     */
    private void forceLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        RequestUtil.setFlashToast(request, "error", Messages.MSG37_ACCOUNT_INACTIVE);
        RequestUtil.redirect(request, response, "/login");
    }
}
