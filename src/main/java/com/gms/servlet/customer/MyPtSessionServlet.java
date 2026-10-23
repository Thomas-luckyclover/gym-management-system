package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Màn "Lịch tập" — GET /customer/pt-sessions?view=week|month&date=yyyy-MM-dd (CHỈ XEM).
 *   view: week (mặc định) | month    date: ngày bất kỳ trong tuần/tháng muốn xem (sai/thiếu → hôm nay)
 */
@WebServlet("/customer/pt-sessions")
public class MyPtSessionServlet extends HttpServlet {

    private static final String VIEW = "customer/my-pt-sessions";
    private static final String PAGE_URL = "/customer/pt-sessions";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyPtSessionService sessionService = new MyPtSessionService();

    // ================================================================================================================
    // 1. MỞ MÀN — doGet gọi các hàm đọc session, header và lịch ngay dưới
    // ================================================================================================================

    /** Đọc tài khoản, tải header và lịch tuần/tháng rồi mở JSP. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: phải đăng nhập
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: header (tên + mã khách) và thời khóa biểu
        loadHeader(request, accountId);
        loadTimetable(request, accountId);

        // B3: sang JSP
        request.setAttribute("activeSidebar", "pt-sessions");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-pt-sessions-page");
        RequestUtil.forward(request, response, VIEW);
    }

    /** accountId trong HttpSession (đặt lúc đăng nhập). Null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /** Tên + mã khách ở header (dùng chung với Customer Dashboard). */
    private void loadHeader(HttpServletRequest request, int accountId) {
        ServiceResult<CustomerDashboardView> result = dashboardService.getDashboard(accountId);
        if (result.isSuccess()) {
            request.setAttribute("dashboard", result.getData());
        } else {
            request.setAttribute("dashboardError", result.getMessage());
        }
    }

    /** Đọc ?view, ?date rồi lấy thời khóa biểu tuần hoặc lưới tháng. */
    private void loadTimetable(HttpServletRequest request, int accountId) {
        // B1: giờ hiện tại lấy 1 lần cho cả request; ngày sai/thiếu → hôm nay (không trả lỗi 500)
        LocalDateTime now = DateTimeUtil.now();
        String requestedDate = request.getParameter("date");
        LocalDate date = MyPtSessionService.parseViewDate(requestedDate, now.toLocalDate());

        // Giữ đúng ngày đã chọn, không thay bằng Thứ 2 hoặc ngày đầu tháng khi hiển thị.
        request.setAttribute("viewDate", date);
        if (requestedDate != null && !requestedDate.isBlank()) {
            request.setAttribute("selectedDate", date);
        }

        // Đến từ "Gói của tôi": ?session= buổi cần mở sẵn chi tiết; ?from= gói để nút "‹ Gói của tôi" quay về đúng gói.
        // Chỉ nhận số dương / "single"; buổi không có trong tuần thì JS tự bỏ qua (không lộ dữ liệu người khác).
        Integer focusSession = RequestUtil.getPositiveIntParam(request, "session");
        if (focusSession != null) {
            request.setAttribute("focusSessionId", focusSession);
        }
        String from = request.getParameter("from");
        if ("single".equals(from) || RequestUtil.getPositiveIntParam(request, "from") != null) {
            request.setAttribute("backBookingId", from);
        }

        // B2: chọn cách xem
        boolean monthView = "month".equals(request.getParameter("view"));
        request.setAttribute("view", monthView ? "month" : "week");

        // B3: gọi Service, lỗi thì JSP hiện thông báo thay cho bảng
        ServiceResult<?> result = monthView
                ? sessionService.getMonth(accountId, date, now)
                : sessionService.getWeek(accountId, date, now);
        if (result.isSuccess()) {
            request.setAttribute(monthView ? "month" : "timetable", result.getData());
        } else {
            request.setAttribute("timetableError", result.getMessage());
        }
    }

}
