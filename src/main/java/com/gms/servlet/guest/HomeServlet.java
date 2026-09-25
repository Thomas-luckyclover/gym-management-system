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
 * Trang chủ (#5 Home, UC-06). Công khai — AuthFilter đã cho "" và "/" đi qua.
 *
 * @WebServlet("") = đúng địa chỉ gốc của web, VD http://localhost:8080/
 * (KHÔNG dùng "/" — "/" sẽ nuốt cả file css/js/ảnh)
 *
 * @author Le Cong Minh
 */
@WebServlet("")
public class HomeServlet extends HttpServlet {

    private final HomeService homeService = new HomeService();

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