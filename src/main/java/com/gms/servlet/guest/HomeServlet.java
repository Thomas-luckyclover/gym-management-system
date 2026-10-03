package com.gms.servlet.guest;

import com.gms.dto.HomePageView;
import com.gms.dto.ServiceResult;
import com.gms.service.HomeService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet xử lý trang chủ công khai (URL "" — root context, GET /).
 *
 * <p>Nạp dữ liệu tổng quan (gói tập, huấn luyện viên, blog nổi bật…) rồi forward sang JSP /WEB-INF/views/guest/home.jsp.</p>
 */
@WebServlet("")
public class HomeServlet extends HttpServlet {

    private final HomeService homeService = new HomeService();

    // ===== GET: Trang chủ =====

    /**
     * GET / — mở trang chủ; gọi HomeService lấy dữ liệu, lỗi Service thì trả 500.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: lấy toàn bộ dữ liệu trang chủ
        ServiceResult<HomePageView> result = homeService.getHomePage();

        // B2: lỗi DB → trang lỗi 500
        if (!result.isSuccess()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // B3: đưa dữ liệu cho JSP
        request.setAttribute("home", result.getData());
        RequestUtil.forward(request, response, "guest/home");
    }
}