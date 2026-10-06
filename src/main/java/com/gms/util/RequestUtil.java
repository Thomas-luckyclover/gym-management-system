package com.gms.util;

import com.gms.constant.SessionKey;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * Đọc tham số dạng chuỗi, đã trim (GBR-19: từ khóa luôn được trim).
     * @param request request hiện tại
     * @param name tên tham số
     * @return chuỗi đã trim, hoặc null nếu không gửi lên
     */
    public static String getTrimmedParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    /**
     * Đọc tham số dạng số nguyên, sai định dạng thì dùng giá trị mặc định.
     * @param request request hiện tại
     * @param name tên tham số
     * @param defaultValue giá trị khi thiếu/sai
     * @return số nguyên
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
     * Forward tới JSP trong WEB-INF/jsp.
     * @param request request hiện tại
     * @param response response hiện tại
     * @param viewName đường dẫn tương đối, không đuôi .jsp — VD "customer/dashboard"
     */
    public static void forward(HttpServletRequest request, HttpServletResponse response, String viewName)
            throws ServletException, IOException {
        String viewPath = VIEW_PREFIX + viewName + VIEW_SUFFIX;
        request.getRequestDispatcher(viewPath).forward(request, response);
    }

    /**
     * Redirect tới 1 đường dẫn trong ứng dụng (tự gắn context path ở đầu).
     * @param request request hiện tại
     * @param response response hiện tại
     * @param path đường dẫn tính từ context path — VD "/customer/dashboard"
     */
    public static void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    // ===== Toast qua redirect (Post/Redirect/Get) =====

    /**
     * Gắn toast để trang KẾ TIẾP (sau redirect) hiển thị — JSP include common/flash-toast.jspf sẽ hiện rồi xóa.
     * @param request request hiện tại
     * @param type "success" | "info" | "warning" | "error"
     * @param message nội dung thông báo
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
     * @param request request hiện tại
     * @return true nếu là AJAX
     */
    public static boolean isAjaxRequest(HttpServletRequest request) {
        String headerValue = request.getHeader(AJAX_HEADER);
        return AJAX_HEADER_VALUE.equals(headerValue);
    }

    /**
     * Đường dẫn request tính từ context path, VD "/customer/dashboard".
     * @param request request hiện tại
     * @return đường dẫn không gồm context path
     */
    public static String getPathWithinApp(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return uri.substring(contextPath.length());
    }

    /**
     * GBR-31: kiểm tra returnUrl an toàn trước khi redirect về sau đăng nhập — chỉ cho phép đường dẫn nội bộ,
     * chặn kiểu "https://trang-lua-dao.com" hoặc "//trang-lua-dao.com" (lỗ hổng Open Redirect).
     * @param returnUrl giá trị lấy từ tham số returnUrl
     * @return true nếu là đường dẫn nội bộ hợp lệ, VD "/customer/bookings?page=2"
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
     * Lấy IP thật của người dùng — VNPay bắt buộc gửi vnp_IpAddr.
     * Nếu chạy sau proxy (ngrok khi test IPN) thì IP thật nằm ở header X-Forwarded-For.
     * @param request request hiện tại
     * @return địa chỉ IP
     */
    public static String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (ValidationUtil.isNullOrEmpty(forwardedFor)) {
            return request.getRemoteAddr();
        }
        String[] ipList = forwardedFor.split(",");
        return ipList[0].trim();
    }
}
