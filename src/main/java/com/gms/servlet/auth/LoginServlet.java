package com.gms.servlet.auth;

import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.dto.LoginResult;
import com.gms.dto.ServiceResult;
import com.gms.service.AuthService;
import com.gms.util.JsonUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Servlet đăng nhập hệ thống (#1 Sign In, /login).
 *
 * <p>
 * Quy trình:
 * </p>
 * <ul>
 * <li>doGet: Nếu đã đăng nhập -> chuyển về trang chính của vai trò. Nếu chưa ->
 * hiển thị form đăng nhập.</li>
 * <li>doPost: Đọc tham số identifier & password -> gọi AuthService.login.</li>
 * <li>Thành công: đổi session id (chống Session Fixation), lưu accountId, role,
 * displayName, set timeout 7200s -> redirect.</li>
 * <li>Thất bại: nếu AJAX -> trả JsonResponse.from(result); nếu Form submit ->
 * set attribute & forward lại JSP.</li>
 * </ul>
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private final AuthService authService;

    public LoginServlet() {
        this.authService = new AuthService();
    }

    public LoginServlet(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionKey.ACCOUNT_ID) != null) {
            String roleStr = (String) session.getAttribute(SessionKey.ROLE);
            Role role = Role.fromDbValue(roleStr);
            String homePath = role != null ? role.getHomePath() : "/";
            RequestUtil.redirect(request, response, homePath);
            return;
        }

        String returnUrl = RequestUtil.getTrimmedParam(request, "returnUrl");
        if (RequestUtil.isSafeReturnUrl(returnUrl)) {
            request.setAttribute("returnUrl", returnUrl);
        }

        RequestUtil.forward(request, response, "auth/login");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String identifier = RequestUtil.getTrimmedParam(request, "identifier");
        String password = request.getParameter("password");
        String returnUrl = RequestUtil.getTrimmedParam(request, "returnUrl");

        ServiceResult<LoginResult> result = authService.login(identifier, password);

        boolean isAjax = RequestUtil.isAjaxRequest(request);

        if (result.isSuccess()) {
            LoginResult loginResult = result.getData();

            // Chống lỗ hổng Session Fixation: hủy session cũ và tạo session mới
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute(SessionKey.ACCOUNT_ID, loginResult.getAccountId());
            newSession.setAttribute(SessionKey.ROLE, loginResult.getRole().getDbValue());
            newSession.setAttribute(SessionKey.DISPLAY_NAME, loginResult.getFullName());
            newSession.setMaxInactiveInterval(SessionKey.SESSION_TIMEOUT_SECONDS);

            String targetUrl = RequestUtil.isSafeReturnUrl(returnUrl)
                    ? returnUrl
                    : loginResult.getRedirectUrl();

            if (isAjax) {
                JsonUtil.writeJson(response, JsonResponse.from(result));
            } else {
                RequestUtil.redirect(request, response, targetUrl);
            }
        } else {
            if (isAjax) {
                JsonUtil.writeJson(response, JsonResponse.from(result));
            } else {
                request.setAttribute("identifier", identifier);
                request.setAttribute("errorMessage", result.getMessage());
                request.setAttribute("fieldErrors", result.getFieldErrors());
                if (RequestUtil.isSafeReturnUrl(returnUrl)) {
                    request.setAttribute("returnUrl", returnUrl);
                }
                RequestUtil.forward(request, response, "auth/login");
            }
        }
    }
}
