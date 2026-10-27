package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.ExerciseLibraryView;
import com.gms.dto.ServiceResult;
import com.gms.service.ExerciseLibraryService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * SERVLET MÀN THƯ VIỆN BÀI TẬP — 1 Servlet cho 2 URL (27/10/2026):
 *
 *   GET /customer/exercises   khách (CustomerAreaFilter kiểm vai trò). Chỉ xem được khi có gói mở khóa còn hiệu lực.
 *   GET /pt/exercises         PT (PtAreaFilter kiểm vai trò). Luôn xem được.
 *   Tham số (đều tùy chọn): ?q= tên bài · &category= nhóm cơ · &equipment= dụng cụ · &sort=name|image · &page=
 *
 *   doGet  HÀM CHÍNH: accountId từ session → libraryService.getPage (1 lần) → đặt "library" / "libraryError"
 *          → thanh đầu trang theo vai trò → forward customer/exercises.jsp hoặc pt/exercises.jsp
 *   Màn chỉ xem, không có POST.
 */
@WebServlet({"/customer/exercises", "/pt/exercises"})
public class ExerciseLibraryServlet extends HttpServlet {

    private final ExerciseLibraryService libraryService = new ExerciseLibraryService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: vai trò theo URL (Filter đã chặn sai vai trò), tài khoản theo session.
        boolean isPt = request.getServletPath().startsWith("/pt/");
        String pageUrl = isPt ? "/pt/exercises" : "/customer/exercises";
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + pageUrl);
            return;
        }

        // B2: dữ liệu màn — Service tự làm sạch mọi tham số URL.
        ServiceResult<ExerciseLibraryView> result = libraryService.getPage(accountId, isPt,
                request.getParameter("q"), request.getParameter("category"), request.getParameter("equipment"),
                request.getParameter("sort"), request.getParameter("page"), DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("library", result.getData());     // → _exercise-library.jspf
        } else {
            request.setAttribute("libraryError", result.getMessage());
        }
        request.setAttribute("libraryUrl", pageUrl);                // → link bộ lọc, chip, phân trang

        // B3: thanh đầu trang + menu trái theo vai trò.
        if (isPt) {
            request.setAttribute("activeNav", "exercises");         // → layout-pt.jspf tô mục "Thư viện bài tập"
            request.setAttribute("pageTitle", "Thư viện bài tập");   // → pt/_header.jspf
            request.setAttribute("pageHelp", "Tra bài theo nhóm cơ, dụng cụ hoặc tên tiếng Anh khi soạn giáo án. "
                    + "Dữ liệu lấy từ wger.de, cập nhật mỗi ngày.");
            request.setAttribute("today", DateTimeUtil.today());
            RequestUtil.forward(request, response, "pt/exercises");
        } else {
            request.setAttribute("activeSidebar", "exercises");     // → layout-customer.jspf
            RequestUtil.forward(request, response, "customer/exercises");
        }
    }

    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
