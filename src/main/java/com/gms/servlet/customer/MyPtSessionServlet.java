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
 * SERVLET MÀN LỊCH TẬP (khách) — /customer/pt-sessions?view=week|month&date=yyyy-MM-dd (CHỈ XEM).
 *
 * VIỆC CỦA SERVLET: đọc tham số → gọi MyPtSessionService → đặt attribute → forward my-pt-sessions.jsp. Không có form POST:
 *   khách không tự hủy / đổi buổi, muốn đổi thì nhắn PT qua Zalo (chốt 19/10).
 * TRƯỚC SERVLET: CustomerAreaFilter chặn vai trò khác.
 *
 *   URL                                                      Servlet gọi                             Kết quả
 *   GET  /customer/pt-sessions[?view=week&date=]              doGet → loadTimetable → getWeek          forward my-pt-sessions.jsp ("timetable")
 *   GET  /customer/pt-sessions?view=month&date=               doGet → loadTimetable → getMonth         forward my-pt-sessions.jsp ("month")
 *   GET  …&openSession=<SessionId>&backPackage=<Id>|single    readLinkParams                           mở sẵn hộp chi tiết + nút "‹ Gói của tôi"
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  doGet   dựng cả màn   → getAccountId, loadHeader, loadTimetable, readViewDate, readLinkParams, setPageInfo
 */
@WebServlet("/customer/pt-sessions")
public class MyPtSessionServlet extends HttpServlet {
    private static final String VIEW = "customer/my-pt-sessions";
    private static final String PAGE_URL = "/customer/pt-sessions";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final MyPtSessionService sessionService = new MyPtSessionService();



    // ============================================================================================================
    // HÀM CHÍNH 1. doGet — mở "Lịch tập" hoặc bấm ‹ › / Tuần / Tháng / Chọn ngày
    //   Ai gọi: menu "Lịch tập", link từ "Gói của tôi"  ·  Ra: forward my-pt-sessions.jsp
    //   Hàm phụ ngay dưới, theo thứ tự doGet gọi: getAccountId → loadHeader → loadTimetable → readViewDate · readLinkParams → setPageInfo
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — dựng cả màn Lịch tập.
     *   Ai gọi: menu "Lịch tập", nút ‹ › / Tuần / Tháng / Chọn ngày, link từ "Gói của tôi"
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chưa đăng nhập → về trang đăng nhập
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: tên khách ở topbar + lịch tuần / tháng
        loadHeader(request, accountId);
        loadTimetable(request, accountId);

        // B3: tô menu → hiện trang
        setPageInfo(request);
        RequestUtil.forward(request, response, VIEW);
    }

    /** HÀM PHỤ (doGet B1) — AccountId trong session; null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /** HÀM PHỤ (doGet B2) — tên + mã khách ở topbar (dùng chung với Dashboard khách). Lỗi thì chỉ không hiện tên, lịch vẫn hiện. */
    private void loadHeader(HttpServletRequest request, int accountId) {
        ServiceResult<CustomerDashboardView> result = dashboardService.getDashboard(accountId);
        if (result.isSuccess()) {
            request.setAttribute("dashboard", result.getData());
        }
    }

    /** HÀM PHỤ (doGet B2) — đọc ?view, ?date rồi lấy thời khóa biểu tuần ("timetable") hoặc lưới tháng ("month"). */
    private void loadTimetable(HttpServletRequest request, int accountId) {
        // B1: giờ hiện tại, ngày đang xem, buổi cần mở sẵn
        LocalDateTime now = DateTimeUtil.now();
        LocalDate date = readViewDate(request, now.toLocalDate());
        readLinkParams(request);

        // B2: xem tuần hay tháng
        boolean monthView = "month".equals(request.getParameter("view"));   // ?view= — xem danh sách link ở trên
        request.setAttribute("view", monthView ? "month" : "week");         // → my-pt-sessions.jsp dòng 94–131, 280

        // B3: lấy lịch; lỗi → trang hiện câu báo thay cho bảng
        ServiceResult<?> result = monthView
                ? sessionService.getMonth(accountId, date, now)
                : sessionService.getWeek(accountId, date, now);
        if (result.isSuccess()) {
            request.setAttribute(monthView ? "month" : "timetable", result.getData());
        } else {
            request.setAttribute("timetableError", result.getMessage());   // → my-pt-sessions.jsp dòng 67–70
        }
    }

    /** HÀM PHỤ (loadTimetable B1) — ?date= sai / thiếu → hôm nay. Giữ đúng ngày đã chọn để tô "Đang chọn". */
    private LocalDate readViewDate(HttpServletRequest request, LocalDate today) {
        String requestedDate = request.getParameter("date");
        LocalDate date = MyPtSessionService.parseViewDate(requestedDate, today);

        request.setAttribute("viewDate", date);
        if (requestedDate != null && !requestedDate.isBlank()) {
            request.setAttribute("selectedDate", date);
        }
        return date;
    }

    /** HÀM PHỤ (loadTimetable B1) — ?openSession= (buổi cần mở sẵn hộp chi tiết) · ?backPackage= (nút "‹ Gói của tôi" quay về đúng gói). */
    private void readLinkParams(HttpServletRequest request) {
        Integer openSessionId = RequestUtil.getPositiveIntParam(request, "openSession");
        if (openSessionId != null) {
            request.setAttribute("openSessionId", openSessionId);
        }

        String backPackageKey = request.getParameter("backPackage");
        if ("single".equals(backPackageKey) || RequestUtil.getPositiveIntParam(request, "backPackage") != null) {
            request.setAttribute("backPackageKey", backPackageKey);
        }
    }

    /** HÀM PHỤ (doGet B3) — tô menu "Lịch tập" + class giao diện cho body. */
    private void setPageInfo(HttpServletRequest request) {
        request.setAttribute("activeSidebar", "pt-sessions");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-pt-sessions-page");
    }
}
