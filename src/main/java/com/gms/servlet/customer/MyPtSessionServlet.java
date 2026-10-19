package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionPage;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDashboardService;
import com.gms.service.MyPtSessionService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Màn "Lịch tập của tôi" — URL: /customer/pt-sessions
 *
 *   GET (xem trang) → doGet: nạp dữ liệu các khối rồi forward sang JSP.
 *   Màn CHỈ XEM. [CHOT 19/10] Khách không tự hủy / tự đổi buổi nữa:
 *   muốn hủy hoặc đổi thì nhắn PT qua Zalo, PT cập nhật lịch giúp (UC-35).
 *
 * TÌM NHANH: [GOI-PT] [SAP-TOI] [LICH-SU] [PHAN-TRANG] [QUYEN]
 */
@WebServlet("/customer/pt-sessions")
public class MyPtSessionServlet extends HttpServlet {

    private static final String VIEW = "customer/my-pt-sessions";
    private static final String PAGE_URL = "/customer/pt-sessions";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyPtSessionService sessionService = new MyPtSessionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1 [QUYEN]: phải đăng nhập
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: nạp dữ liệu cho từng khối của trang
        loadHeader(request, accountId);
        loadPackages(request, accountId);
        loadSessionBlocks(request, accountId);

        // B3: chuyển sang JSP
        request.setAttribute("activeSidebar", "pt-sessions");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-pt-sessions-page");
        RequestUtil.forward(request, response, VIEW);
    }

    /** Header: tên + mã khách (dùng chung với Customer Dashboard). */
    private void loadHeader(HttpServletRequest request, int accountId) {
        ServiceResult<CustomerDashboardView> result = dashboardService.getDashboard(accountId);
        if (result.isSuccess()) {
            request.setAttribute("dashboard", result.getData());
        } else {
            request.setAttribute("dashboardError", result.getMessage());
        }
    }

    /** [GOI-PT] Khối "Gói PT của bạn" (gói Active). */
    private void loadPackages(HttpServletRequest request, int accountId) {
        ServiceResult<List<MyPtPackageView>> packages = sessionService.getPtPackages(accountId);
        if (packages.isSuccess()) {
            request.setAttribute("ptPackages", packages.getData());
        } else {
            request.setAttribute("ptPackagesError", packages.getMessage());
        }
    }

    /** [SAP-TOI][LICH-SU][PHAN-TRANG] Khối "Sắp tới" và "Lịch sử": mỗi khối 1 trang riêng. */
    private void loadSessionBlocks(HttpServletRequest request, int accountId) {
        // Lấy giờ ĐÚNG 1 LẦN để 2 khối chia buổi theo cùng 1 thời điểm (không buổi nào lọt giữa 2 khối)
        LocalDateTime now = DateTimeUtil.now();
        request.setAttribute("todayDate", now.toLocalDate());

        int upcomingPageNumber = sessionService.parsePageNumber(request.getParameter("upcomingPage"));
        ServiceResult<MyPtSessionPage> upcoming = sessionService.getUpcomingPage(accountId, now, upcomingPageNumber);
        if (upcoming.isSuccess()) {
            request.setAttribute("upcomingPage", upcoming.getData());
        } else {
            request.setAttribute("upcomingSessionsError", upcoming.getMessage());
        }

        int historyPageNumber = sessionService.parsePageNumber(request.getParameter("historyPage"));
        ServiceResult<MyPtSessionPage> history = sessionService.getHistoryPage(accountId, now, historyPageNumber);
        if (history.isSuccess()) {
            request.setAttribute("historyPage", history.getData());
        } else {
            request.setAttribute("historySessionsError", history.getMessage());
        }
    }

    /** accountId trong HttpSession (đặt lúc đăng nhập). null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
