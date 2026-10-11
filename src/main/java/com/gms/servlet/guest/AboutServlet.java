package com.gms.servlet.guest;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet trang Về chúng tôi (URL /about) — trang công khai, nội dung tĩnh giới thiệu Super Gym.
 *
 * <p>Không đọc DB: câu chuyện, cột mốc, danh ngôn và minigame Mochi đều nằm trong JSP/JS.</p>
 */
@WebServlet("/about")
public class AboutServlet extends HttpServlet {

    /**
     * GET /about — đặt mục menu đang chọn rồi chuyển sang JSP.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: đánh dấu menu "Về chúng tôi" trên guest-header.jspf
        request.setAttribute("activeNav", "about");
        // B2: hiển thị trang
        request.getRequestDispatcher("/WEB-INF/jsp/guest/about.jsp").forward(request, response);
    }
}
