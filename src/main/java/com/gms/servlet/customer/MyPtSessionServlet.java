package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MonthView;
import com.gms.dto.ServiceResult;
import com.gms.dto.TimetableView;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Màn "Lịch tập của tôi" — URL: /customer/pt-sessions
 *
 *   GET ?view=week|month&date=yyyy-MM-dd → doGet: nạp thời khóa biểu rồi forward sang JSP.
 *   Màn CHỈ XEM. [CHOT 19/10] Khách không tự hủy / tự đổi buổi nữa:
 *   muốn hủy hoặc đổi thì nhắn PT qua Zalo, PT cập nhật lịch giúp (UC-35).
 *
 * TÌM NHANH: [QUYEN] [TKB] [GOI-PT]
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
        loadTimetable(request, accountId);

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

    /**
     * [TKB] Thời khóa biểu: ?view=week|month (mặc định week), ?date=yyyy-MM-dd (ngày bất kỳ trong tuần/tháng muốn xem).
     * date sai định dạng hoặc thiếu → hôm nay (không báo lỗi, không trả 500).
     */
    private void loadTimetable(HttpServletRequest request, int accountId) {
        LocalDateTime now = DateTimeUtil.now();   // lấy giờ 1 LẦN cho cả request
        LocalDate date = DateTimeUtil.parseHtmlDate(request.getParameter("date"));
        if (date == null) {
            date = now.toLocalDate();
        }
        boolean monthView = "month".equals(request.getParameter("view"));
        request.setAttribute("view", monthView ? "month" : "week");

        if (monthView) {
            ServiceResult<MonthView> result = sessionService.getMonth(accountId, date, now);
            if (result.isSuccess()) {
                request.setAttribute("month", result.getData());
            } else {
                request.setAttribute("timetableError", result.getMessage());
            }
        } else {
            ServiceResult<TimetableView> result = sessionService.getWeek(accountId, date, now);
            if (result.isSuccess()) {
                request.setAttribute("timetable", result.getData());
            } else {
                request.setAttribute("timetableError", result.getMessage());
            }
        }
    }

    /** accountId trong HttpSession (đặt lúc đăng nhập). null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
