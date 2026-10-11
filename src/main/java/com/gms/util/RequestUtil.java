package com.gms.util;

import com.gms.constant.SessionKey;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Các thao tác lặp lại ở Servlet/Filter: đọc tham số, forward/redirect, toast qua redirect, nhận diện AJAX.
 * Không chứa logic nghiệp vụ.
 */
public final class RequestUtil {

    /** Thư mục gốc chứa JSP — JSP đặt trong WEB-INF để không ai gõ URL mở thẳng được. */
    private static final String VIEW_PREFIX = "/WEB-INF/jsp/";
    private static final String VIEW_SUFFIX = ".jsp";

    /** Header mà js/common.js gắn vào mọi request AJAX. */
    private static final String AJAX_HEADER = "X-Requested-With";
    private static final String AJAX_HEADER_VALUE = "XMLHttpRequest";

    private RequestUtil() {
    }

    // ===== Đọc tham số =====

    /**
     * Đọc tham số dạng chuỗi (đã trim). Trả null nếu không gửi lên.
     */
    public static String getTrimmedParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    /**
     * Đọc tham số dạng số nguyên. Sai/không có thì trả defaultValue.
     */
    public static int getIntParam(HttpServletRequest request, String name, int defaultValue) {
        String value = request.getParameter(name);
        if (!ValidationUtil.isValidInteger(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    // ===== Điều hướng =====

    /**
     * Forward tới JSP trong WEB-INF/jsp (đường dẫn không kèm đuôi .jsp).
     */
    public static void forward(HttpServletRequest request, HttpServletResponse response, String viewName)
            throws ServletException, IOException {
        String viewPath = VIEW_PREFIX + viewName + VIEW_SUFFIX;
        request.getRequestDispatcher(viewPath).forward(request, response);
    }

    /**
     * Redirect tới đường dẫn trong ứng dụng (hàm tự gắn context path).
     */
    public static void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    // ===== Toast qua redirect (Post/Redirect/Get) =====

    /**
     * Gắn toast để trang KẾ TIẾP (sau redirect) hiển thị rồi tự xoá.
     */
    public static void setFlashToast(HttpServletRequest request, String type, String message) {
        Map<String, String> toast = new LinkedHashMap<>();
        toast.put("type", type);
        toast.put("message", message);
        HttpSession session = request.getSession();
        session.setAttribute(SessionKey.FLASH_TOAST, toast);
    } 

    // ===== Nhận diện request =====

    /**
     * Request có phải gọi bằng AJAX (qua js/common.js) không — Filter dùng để trả JSON 401/403 thay vì redirect.
     */
    public static boolean isAjaxRequest(HttpServletRequest request) {
        String headerValue = request.getHeader(AJAX_HEADER);
        return AJAX_HEADER_VALUE.equals(headerValue);
    }

    /**
     * Đường dẫn request tính từ context path (không gồm context path).
     */
    public static String getPathWithinApp(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return uri.substring(contextPath.length());
    }

    /**
     * Kiểm tra returnUrl có phải đường dẫn nội bộ an toàn không (chặn open redirect tới trang lừa đảo).
     */
    public static boolean isSafeReturnUrl(String returnUrl) {
        if (ValidationUtil.isNullOrEmpty(returnUrl)) {
            return false;
        }
        boolean startsWithSlash = returnUrl.startsWith("/");
        boolean isProtocolRelative = returnUrl.startsWith("//");
        boolean hasBackslash = returnUrl.contains("\\");
        return startsWithSlash && !isProtocolRelative && !hasBackslash;
    }

    /**
     * Lấy IP thật của người dùng (VNPay bắt buộc gửi vnp_IpAddr).
     * Nếu chạy sau proxy thì IP nằm ở header X-Forwarded-For.
     */
    public static String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (ValidationUtil.isNullOrEmpty(forwardedFor)) {
            return request.getRemoteAddr();
        }
        String[] ipList = forwardedFor.split(",");
        return ipList[0].trim();
    }

    // ===== CSRF Token (TC-17A) =====

    /**
     * Lấy hoặc sinh mới CSRF token ngẫu nhiên gắn với session hiện tại.
     * @param session session của người dùng
     * @return chuỗi token ngẫu nhiên, hoặc null nếu session null
     */
    public static String getOrCreateCsrfToken(HttpSession session) {
        if (session == null) {
            return null;
        }
        String token = (String) session.getAttribute(SessionKey.CSRF_TOKEN);
        if (token == null || token.isBlank()) {
            token = UUID.randomUUID().toString();
            session.setAttribute(SessionKey.CSRF_TOKEN, token);
        }
        return token;
    }

    /**
     * Kiểm tra CSRF token gửi lên từ request có khớp với token trong session không.
     * Sử dụng so sánh an toàn tránh timing attack.
     * @param request request gửi lên từ form
     * @return true nếu token hợp lệ và khớp với session
     */
    public static boolean isValidCsrfToken(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        String sessionToken = (String) session.getAttribute(SessionKey.CSRF_TOKEN);
        if (sessionToken == null || sessionToken.isBlank()) {
            return false;
        }
        String requestToken = request.getParameter("csrfToken");
        if (requestToken == null || requestToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                sessionToken.getBytes(StandardCharsets.UTF_8),
                requestToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
