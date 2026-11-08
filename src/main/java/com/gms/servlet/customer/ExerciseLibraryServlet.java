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
 * SERVLET MÀN THƯ VIỆN BÀI TẬP — 1 Servlet cho 2 URL: /customer/exercises (khách) · /pt/exercises (PT).
 *
 * VIỆC CỦA SERVLET: xác định vai trò theo URL → gọi ExerciseLibraryService.getPage (1 lần) → đặt attribute → forward JSP.
 *   Màn chỉ xem, không có POST. Servlet KHÔNG kiểm quyền mở khóa / làm sạch bộ lọc: Service làm hết.
 * TRƯỚC SERVLET: Filter đã kiểm vai trò theo URL — /customer/* qua CustomerAreaFilter (phải là Hội viên),
 *   /pt/* qua PtAreaFilter (phải là PT) → khi code chạy tới đây, URL đã cho biết chắc người dùng là ai.
 *
 *   URL (tham số đều tùy chọn: ?q= tên bài · &category= nhóm cơ · &equipment= dụng cụ · &sort=name|image · &page=)
 *                                Servlet gọi                          Kết quả
 *   GET /customer/exercises      doGet → libraryService.getPage        forward customer/exercises.jsp ("library" / "libraryError")
 *                                                                      khách chưa có gói mở khóa → màn khóa (library.access ≠ OPEN)
 *   GET /pt/exercises            doGet → libraryService.getPage        forward pt/exercises.jsp (PT luôn xem được)
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  doGet   mở trang / lọc / tìm / đổi trang   → getAccountId
 */
@WebServlet({"/customer/exercises", "/pt/exercises"})
public class ExerciseLibraryServlet extends HttpServlet {
    private final ExerciseLibraryService libraryService = new ExerciseLibraryService();



    // ============================================================================================================
    // HÀM CHÍNH 1. doGet — mở trang thư viện, lọc, tìm, đổi trang (mọi thao tác đều là GET cùng URL, thêm tham số)
    //   Ai gọi: menu "Thư viện bài tập" của khách / PT, link bộ lọc · chip · phân trang  ·  Gọi xuống: ExerciseLibraryService.getPage
    //   Hàm phụ ngay dưới: getAccountId
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — dựng màn Thư viện bài tập cho khách hoặc PT.
     *   Ai gọi: menu "Thư viện bài tập", link bộ lọc / chip / phân trang
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: vai trò theo URL, chưa đăng nhập → về trang đăng nhập
        boolean isPt = request.getServletPath().startsWith("/pt/");
        String pageUrl = isPt ? "/pt/exercises" : "/customer/exercises";
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + pageUrl);
            return;
        }

        // B2: dữ liệu màn (Service tự làm sạch tham số URL)
        ServiceResult<ExerciseLibraryView> result = libraryService.getPage(
                accountId,
                isPt,
                request.getParameter("q"),
                request.getParameter("category"),
                request.getParameter("equipment"),
                request.getParameter("sort"),
                request.getParameter("page"),
                DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("library", result.getData());     // → _exercise-library.jspf
        } else {
            request.setAttribute("libraryError", result.getMessage());
        }
        request.setAttribute("libraryUrl", pageUrl);                // → link bộ lọc, chip, phân trang

        
        
        
        // B3: đầu trang + menu theo vai trò → hiện trang
        if (isPt) {
            request.setAttribute("activeNav", "exercises");         // → layout-pt.jspf tô mục "Thư viện bài tập"
            request.setAttribute("pageTitle", "Thư viện bài tập");   // → pt/_header.jspf
            request.setAttribute("pageHelp", "Tra bài theo nhóm cơ, dụng cụ hoặc tên tiếng Anh khi soạn giáo án. "
                    + "Nguồn: dữ liệu và hình từ wger.de (CC BY-SA 4.0), cập nhật mỗi ngày; "
                    + "ảnh nhóm cơ từ free-exercise-db (public domain).");
            request.setAttribute("today", DateTimeUtil.today());
            RequestUtil.forward(request, response, "pt/exercises");
        } else {
            request.setAttribute("activeSidebar", "exercises");     // → layout-customer.jspf
            RequestUtil.forward(request, response, "customer/exercises");
        }
    }

    /** HÀM PHỤ (doGet B1) — AccountId trong session; null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
