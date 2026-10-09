package com.gms.filter;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
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
     * Lọc request: chưa đăng nhập → nhờ AuthFilter xử lý; sai vai trò → 403; đúng vai trò → cho qua.
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

        // B4: sai vai trò → 403 (AJAX nhận JSON, trang thường hiện error/403.jsp khai báo trong web.xml)
        if (RequestUtil.isAjaxRequest(httpRequest)) {
            JsonResponse body = JsonResponse.error(Messages.MSG07_NO_PERMISSION);
            JsonUtil.writeJson(httpResponse, HttpServletResponse.SC_FORBIDDEN, body);
            return;
        }
        httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
