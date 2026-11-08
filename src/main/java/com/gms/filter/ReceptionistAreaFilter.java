package com.gms.filter;

import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.service.ShiftService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Chỉ cho vai trò Lễ tân (Receptionist) vào các URL /receptionist/* (GBR-10). Logic chung nằm ở RoleAreaFilter.
 * Thêm: trước khi mở trang (GET, không phải AJAX) đặt "shiftNotice" để layout-receptionist.jspf hiện thanh cảnh báo ca:
 *   ca quá giờ chưa chốt → mọi màn lễ tân (CBR-123); chưa mở ca → màn Bán hàng và Bán gói PT (CBR-105).
 */
@WebFilter("/receptionist/*")
public class ReceptionistAreaFilter extends RoleAreaFilter {

    private final ShiftService shiftService = new ShiftService();

    @Override
    protected Role getAllowedRole() {
        return Role.RECEPTIONIST;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpSession session = httpRequest.getSession(false);
        boolean isReceptionist = session != null
                && Role.RECEPTIONIST.getDbValue().equals(session.getAttribute(SessionKey.ROLE));
        boolean isPageView = "GET".equals(httpRequest.getMethod()) && !RequestUtil.isAjaxRequest(httpRequest);

        if (isReceptionist && isPageView) {
            String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
            boolean saleScreen = path.startsWith("/receptionist/sales") || path.startsWith("/receptionist/pt-sales");
            String notice = shiftService.findShiftNotice(LocalDateTime.now(DateTimeUtil.GYM_ZONE), saleScreen);
            if (notice != null) {
                httpRequest.setAttribute("shiftNotice", notice);
            }
        }
        super.doFilter(request, response, chain);
    }
}
