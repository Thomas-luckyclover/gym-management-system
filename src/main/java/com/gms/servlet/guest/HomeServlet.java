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

        // B3: đưa dữ liệu cho JSP. "home" (HomePageView) được đọc ở home.jsp:
        //   dòng 65–69    giờ mở cửa + số huấn luyện viên (home.openingHours, home.ptCount)
        //   dòng 140–145  thẻ gói tập (home.gymPackages)
        //   dòng 170–175  thẻ gói PT (home.ptPackages)
        //   dòng 197–204  đội ngũ PT (home.pts)
        //   dòng 218–228  3 bài blog mới nhất (home.latestPosts)
        //   dòng 282      giờ mở cửa cho chân trang → guest-footer.jspf dòng 10 (nhúng ở home.jsp dòng 283)
        request.setAttribute("home", result.getData());
        RequestUtil.forward(request, response, "guest/home");
    }
}