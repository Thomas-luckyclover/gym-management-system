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
@WebFilter("/receptionist/*")//báo cho tomcat biết class bên dưới là bộ lọc cần chạy trước khi request đi vào servlet
public class ReceptionistAreaFilter extends RoleAreaFilter {

    private final ShiftService shiftService = new ShiftService();

    @Override
    protected Role getAllowedRole() {
        return Role.RECEPTIONIST;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Ép kiểu từ ServletRequest (chung) sang HttpServletRequest (của web) để dùng được getSession(), getMethod(), getRequestURI()
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // Tìm session hiện có của người dùng trên server; truyền false để KHÔNG tự tạo session rỗng mới nếu họ chưa đăng nhập
        HttpSession session = httpRequest.getSession(false);
        boolean isReceptionist = session != null
                && Role.RECEPTIONIST.getDbValue().equals(session.getAttribute(SessionKey.ROLE));
        //Lấy role trong session để kiêm tra xem có phải lễ tân ko
        //session = null khi bỏ máy đi quá lâu, người dùng xóa cookie trình duyệt
        boolean isPageView = "GET".equals(httpRequest.getMethod()) && !RequestUtil.isAjaxRequest(httpRequest);
        //Dòng này kiểm tra xem người dùng có đang thực sự mở một trang web lên để xem trên màn hình hay không

        if (isReceptionist && isPageView) {
            String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
            //cắt path thành "/receptionist/sales"
            boolean saleScreen = path.startsWith("/receptionist/sales") || path.startsWith("/receptionist/pt-sales");
            //kiểm tra xem có đang ở màn bán gói membership hay pt hay không
            String notice = shiftService.findShiftNotice(LocalDateTime.now(DateTimeUtil.GYM_ZONE), saleScreen);
            //hỏi service xem có cần hiện cảnh bảo ca không
            if (notice != null) {
                httpRequest.setAttribute("shiftNotice", notice);
            }
        }
        super.doFilter(request, response, chain);
        //super(class cha) gọi xuống class cha để chạy tiếp
        //chain: dây truyền các trạm kiểm tra đến servlet đích
    }
}
