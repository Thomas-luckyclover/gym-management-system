package com.gms.filter;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.util.JsonUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Kiểm tra ĐÃ ĐĂNG NHẬP CHƯA cho toàn site (codeImplementationBackend.md Mục 12.1).
 * Phân quyền theo vai trò nằm ở các Filter riêng (CustomerAreaFilter, ManagerAreaFilter...).
 *
 * Chưa đăng nhập:
 * - Request thường → redirect /login?returnUrl=... để đăng nhập xong quay lại đúng trang (GBR-31)
 * - Request AJAX   → trả JSON 401, js/common.js hiện hộp thoại MSG06 rồi chuyển trang đăng nhập
 */
@WebFilter("/*")
public class AuthFilter implements Filter {

    /** Đường dẫn công khai khớp CHÍNH XÁC (trang chủ phải so khớp tuyệt đối, vì mọi URL đều bắt đầu bằng "/"). */
    private static final Set<String> PUBLIC_EXACT_PATHS = Set.of(
            "",
            "/",
            "/login",
            "/register",
            "/forgot-password",
            "/favicon.ico"
    );

    /** Đường dẫn công khai khớp THEO TIỀN TỐ: tài nguyên tĩnh, blog, và 2 điểm nhận kết quả từ VNPay. */
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/css/",
            "/js/",
            "/images/",
            "/blog",
            "/payment/vnpay-return",
            "/api/v1/commerce/payment/webhook/"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = RequestUtil.getPathWithinApp(httpRequest);

        // B1: URL công khai → cho qua luôn
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // B2: đã đăng nhập → cho đi tiếp (getSession(false): không tạo session mới chỉ để kiểm tra)
        HttpSession session = httpRequest.getSession(false);
        boolean isLoggedIn = session != null && session.getAttribute(SessionKey.ACCOUNT_ID) != null;
        if (isLoggedIn) {
            // B2b: chưa chọn vai trò → chỉ vào /select-role, /logout (CBR-113)
            boolean isRoleChosen = session.getAttribute(SessionKey.ROLE) != null;
            boolean isRoleChoicePath = "/select-role".equals(path) || "/logout".equals(path);
            if (!isRoleChosen && !isRoleChoicePath) {
                if (RequestUtil.isAjaxRequest(httpRequest)) {
                    JsonResponse body = JsonResponse.error(Messages.MSG07_NO_PERMISSION);
                    JsonUtil.writeJson(httpResponse, HttpServletResponse.SC_FORBIDDEN, body);
                    return;
                }
                httpResponse.sendRedirect(httpRequest.getContextPath() + "/select-role");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        // B3: chưa đăng nhập + AJAX → JSON 401
        if (RequestUtil.isAjaxRequest(httpRequest)) {
            JsonResponse body = JsonResponse.error(Messages.MSG06_SESSION_EXPIRED);
            JsonUtil.writeJson(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, body);
            return;
        }

        // B4: chưa đăng nhập + request thường → về trang đăng nhập, nhớ trang đang mở
        String loginUrl = buildLoginRedirectUrl(httpRequest, path);
        httpResponse.sendRedirect(loginUrl);
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_EXACT_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tạo URL /login?returnUrl=... ; chỉ nhớ trang với request GET (POST không mở lại được bằng redirect).
     */
    private String buildLoginRedirectUrl(HttpServletRequest request, String path) {
        String loginUrl = request.getContextPath() + "/login";
        boolean isGetRequest = "GET".equalsIgnoreCase(request.getMethod());
        if (!isGetRequest) {
            return loginUrl;
        }

        String returnUrl = path;
        String queryString = request.getQueryString();
        if (queryString != null) {
            returnUrl = path + "?" + queryString;
        }
        String encodedReturnUrl = URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
        return loginUrl + "?returnUrl=" + encodedReturnUrl;
    }
}
