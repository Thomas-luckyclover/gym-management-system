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
import com.gms.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Servlet xử lý đăng nhập hệ thống (URL /login).
 *
 * <p>doGet: nếu đã đăng nhập chuyển về trang chủ của vai trò; nếu chưa thì hiện form đăng nhập.</p>
 * <p>doPost: xác thực tài khoản; thành công thì tạo session mới, lưu vai trò và chuyển trang, thất bại thì trả lại form kèm lỗi.</p>
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

    /**
     * GET /login — mở form đăng nhập; nếu đã đăng nhập thì chuyển về trang chính của vai trò hiện tại.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionKey.ACCOUNT_ID) != null) {
            String roleStr = (String) session.getAttribute(SessionKey.ROLE);
            Role role = Role.fromDbValue(roleStr);
            String homePath = role != null ? role.getHomePath() : AuthService.SELECT_ROLE_PATH;
            RequestUtil.redirect(request, response, homePath);
            return;
        }

        String returnUrl = RequestUtil.getTrimmedParam(request, "returnUrl");
        if (RequestUtil.isSafeReturnUrl(returnUrl)) {
            request.setAttribute("returnUrl", returnUrl);
        }

        RequestUtil.forward(request, response, "auth/login");
    }

    /**
     * POST /login — xác thực identifier + password. Thành công thì chuyển trang, thất bại thì báo lỗi lại form.
     */
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
            newSession.setAttribute(SessionKey.AVAILABLE_ROLES, SelectRoleServlet.joinRoles(loginResult.getAvailableRoles()));
            newSession.setAttribute(SessionKey.DISPLAY_NAME, loginResult.getFullName());
            newSession.setMaxInactiveInterval(SessionKey.SESSION_TIMEOUT_SECONDS);
            RequestUtil.getOrCreateCsrfToken(newSession);

            String targetUrl;
            if (loginResult.isRoleChoiceRequired()) {
                // B5: ≥ 2 vai trò → chưa đặt ROLE, sang màn chọn vai trò (CBR-113)
                targetUrl = AuthService.SELECT_ROLE_PATH;
                if (RequestUtil.isSafeReturnUrl(returnUrl)) {
                    targetUrl += "?returnUrl=" + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8);
                }
            } else {
                newSession.setAttribute(SessionKey.ROLE, loginResult.getRole().getDbValue());
                targetUrl = RequestUtil.isSafeReturnUrl(returnUrl)
                        ? returnUrl
                        : loginResult.getRedirectUrl();
            }

            if (isAjax) {
                JsonUtil.writeJson(response, JsonResponse.from(result));
            } else {
                RequestUtil.redirect(request, response, targetUrl);
            }
        } else {
            // Tài khoản chưa có mật khẩu (MSG38, VD tài khoản Lễ tân tạo tại quầy) → khách bấm "Đặt mật khẩu"
            // sẽ sang Quên mật khẩu. Để sẵn email trong session để ô email bên đó điền sẵn (ForgotPasswordServlet đọc 1 lần).
            // CHỈ điền khi khách gõ EMAIL: nếu khách gõ số điện thoại thì không điền, vì điền vào là để lộ
            // email gắn với số điện thoại đó cho bất kỳ ai biết SĐT.
            boolean isNoPasswordYet = Messages.MSG38_NO_PASSWORD_YET.equals(result.getMessage());
            if (isNoPasswordYet && ValidationUtil.isValidEmail(identifier)) {
                request.getSession().setAttribute(SessionKey.RECOVERY_PREFILL_EMAIL, identifier);
            }

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
        if (errorMessage != null && errorMessage.startsWith(LOCK_MESSAGE_PREFIX)) {
            return "blocked-lock";
        }
        return "form";
    }

    /** Phần đầu cố định của MSG35 (trước %d), lấy từ Messages để không phụ thuộc ngôn ngữ câu thông báo. */
    private static final String LOCK_MESSAGE_PREFIX = Messages.MSG35_ACCOUNT_TEMP_LOCKED
            .substring(0, Messages.MSG35_ACCOUNT_TEMP_LOCKED.indexOf('%'));

    /** Thời gian còn lại dạng mm:ss ở cuối MSG35. */
    private static final java.util.regex.Pattern LOCK_RETRY_PATTERN
            = java.util.regex.Pattern.compile("(\\d+):(\\d{2})");

    /**
     * Lấy số giây còn lại tới khi hết khóa từ câu MSG35 (phần mm:ss); null nếu không phải câu khóa tạm.
     */
    private Integer extractLockSecondsLeft(String errorMessage) {
        if (errorMessage == null || !errorMessage.startsWith(LOCK_MESSAGE_PREFIX)) {
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
