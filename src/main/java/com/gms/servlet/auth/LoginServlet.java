package com.gms.servlet.auth;

import com.gms.constant.Messages;
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
                request.setAttribute("loginStep", resolveLoginStep(result.getMessage()));

                Integer lockSecondsLeft = extractLockSecondsLeft(result.getMessage());
                if (lockSecondsLeft != null) {
                    request.setAttribute("lockSecondsLeft", lockSecondsLeft);
                }

                if (RequestUtil.isSafeReturnUrl(returnUrl)) {
                    request.setAttribute("returnUrl", returnUrl);
                }
                RequestUtil.forward(request, response, "auth/login");
            }
        }
    }

    /**
     * Chọn panel giao diện đăng nhập theo câu thông báo từ AuthService.
     *
     * @param errorMessage thông báo lỗi login
     * @return form | blocked-no-password | blocked-lock
     */
    private String resolveLoginStep(String errorMessage) {
        if (Messages.MSG38_NO_PASSWORD_YET.equals(errorMessage)) {
            return "blocked-no-password";
        }
        if (errorMessage != null && errorMessage.startsWith("Tài khoản bị khóa")) {
            return "blocked-lock";
        }
        return "form";
    }
    private static final java.util.regex.Pattern LOCK_RETRY_PATTERN
            = java.util.regex.Pattern.compile("Thử lại sau\\s+(\\d+):(\\d{2})");

    /**
     * Lấy số giây còn lại tới khi hết khoá, từ câu thông báo
     * MSG35_ACCOUNT_TEMP_LOCKED ("… Thử lại sau mm:ss."). Trả về null nếu không phải lỗi khoá tài khoản
     * hoặc không parse được.
     */
    private Integer extractLockSecondsLeft(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        java.util.regex.Matcher matcher = LOCK_RETRY_PATTERN.matcher(errorMessage);
        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            return minutes * 60 + seconds;
        }
        return null;
    }
}
