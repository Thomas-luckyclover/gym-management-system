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
 * TRANG CHỦ CÔNG KHAI — GET / (màn #5, UC-06). Chỉ xem.
 *
 *   doGet   HÀM CHÍNH: HomeService.getHomePage → attribute "home" → JSP WEB-INF/jsp/guest/home.jsp
 */
@WebServlet("")
public class HomeServlet extends HttpServlet {

    private final HomeService homeService = new HomeService();


    /**
     * HÀM CHÍNH — trình duyệt gọi khi mở trang gốc "/" (hoặc bấm logo / "Trang chủ").
     * Lỗi DB → trang lỗi 500 (trang chủ không có dữ liệu thì không có gì để hiện).
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