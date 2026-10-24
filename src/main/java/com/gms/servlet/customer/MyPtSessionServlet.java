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
 *
 *   doGet           HÀM CHÍNH: kiểm tra đăng nhập → các hàm phụ đặt attribute → JSP customer/my-pt-sessions.jsp
 *   getAccountId    HÀM PHỤ: đọc tài khoản đang đăng nhập từ session
 *   loadHeader      HÀM PHỤ: tên + mã khách ở topbar
 *   loadTimetable   HÀM PHỤ: đọc ?date, ?view → gọi getWeek hoặc getMonth
 *     ├ readViewDate     ngày đang xem (sai/thiếu → hôm nay)
 *     └ readLinkParams   ?openSession= / ?backPackage= khi đến từ "Gói của tôi"
 *   setPageInfo     HÀM PHỤ: menu đang chọn + class giao diện
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

    /** HÀM CHÍNH — trình duyệt gọi khi mở "Lịch tập" hoặc bấm ‹ › / Tuần / Tháng / Chọn ngày. */
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
        setPageInfo(request);
        RequestUtil.forward(request, response, VIEW);
    }

    /** HÀM PHỤ — gọi ở B1 của doGet. accountId trong HttpSession (đặt lúc đăng nhập). Null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /**
     * HÀM PHỤ — gọi ở B2 của doGet. Tên + mã khách ở header (dùng chung với Customer Dashboard).
     * Lỗi thì chỉ không hiện tên ở topbar; lịch vẫn hiện.
     */
    private void loadHeader(HttpServletRequest request, int accountId) {
        ServiceResult<CustomerDashboardView> result = dashboardService.getDashboard(accountId);
        if (result.isSuccess()) {
            // → _schedule-header.jspf dòng 43–53 (tên, chữ cái đầu, mã khách); jspf nhúng ở my-pt-sessions.jsp dòng 60
            request.setAttribute("dashboard", result.getData());
        }
    }

    /**
     * HÀM PHỤ — gọi ở B2 của doGet. Đọc ?view, ?date rồi lấy thời khóa biểu tuần hoặc lưới tháng.
     *
     * ?view= ĐẾN TỪ ĐÂU ("week" | "month"; thiếu / lạ → week):
     *   my-pt-sessions.jsp dòng 95, 103, 105   nút ‹ › và "Hôm nay" giữ nguyên kiểu đang xem
     *   my-pt-sessions.jsp dòng 113            ô ẩn trong form "Chọn ngày"
     *   my-pt-sessions.jsp dòng 123, 124       công tắc Tuần / Tháng
     *   my-pt-sessions.jsp dòng 149, 233, 239  bấm 1 ngày (điện thoại), 1 ô ngày / 1 buổi ở lịch tháng → view=week
     *   my-bookings.jsp dòng 53, 267, 433      link từ "Gói của tôi" → view=week
     *
     * JSP ĐỌC Ở ĐÂU:
     *   "view"            my-pt-sessions.jsp dòng 94–124 (nút, tiêu đề, công tắc), 131 (vẽ tuần), 280 (thống kê tháng)
     *   "timetable"       my-pt-sessions.jsp dòng 95–103 (‹ › + khoảng ngày), 138–191 (lưới tuần)
     *   "month"           my-pt-sessions.jsp dòng 95–124, 218–239 (lưới tháng), 282–287 (dòng đếm cuối tháng)
     *   "timetableError"  my-pt-sessions.jsp dòng 67–70 hộp "Chưa tải được lịch tập"
     */
    private void loadTimetable(HttpServletRequest request, int accountId) {
        // B1: giờ hiện tại lấy 1 lần cho cả request; ngày đang xem + buổi cần mở sẵn
        LocalDateTime now = DateTimeUtil.now();
        LocalDate date = readViewDate(request, now.toLocalDate());
        readLinkParams(request);

        // B2: chọn cách xem
        boolean monthView = "month".equals(request.getParameter("view"));   // ?view= — xem danh sách link ở trên
        request.setAttribute("view", monthView ? "month" : "week");         // → my-pt-sessions.jsp dòng 94–131, 280

        // B3: gọi Service, lỗi thì JSP hiện thông báo thay cho bảng
        ServiceResult<?> result = monthView
                ? sessionService.getMonth(accountId, date, now)
                : sessionService.getWeek(accountId, date, now);
        if (result.isSuccess()) {
            // "timetable" → dòng 138–191 (lưới tuần) | "month" → dòng 218–239, 282–287 (lưới tháng)
            request.setAttribute(monthView ? "month" : "timetable", result.getData());
        } else {
            request.setAttribute("timetableError", result.getMessage());   // → my-pt-sessions.jsp dòng 67–70
        }
    }

    /**
     * HÀM PHỤ — gọi ở B1 của loadTimetable. Ngày sai/thiếu → hôm nay (không trả lỗi 500).
     * Giữ đúng ngày đã chọn, không thay bằng Thứ 2 hoặc ngày đầu tháng khi hiển thị.
     */
    private LocalDate readViewDate(HttpServletRequest request, LocalDate today) {
        // ?date=2026-10-26 đến từ: ô "Ngày muốn xem" my-pt-sessions.jsp dòng 116; link ‹ › dòng 95, 103;
        //   Tuần/Tháng dòng 123–124; ngày điện thoại dòng 149; ô ngày / buổi lịch tháng dòng 233, 239;
        //   "Gói của tôi": my-bookings.jsp dòng 270, 404, 435, 473 và _booking-groups.jspf dòng 20 (nhúng ở my-bookings.jsp dòng 504)
        String requestedDate = request.getParameter("date");
        LocalDate date = MyPtSessionService.parseViewDate(requestedDate, today);

        // → my-pt-sessions.jsp dòng 116 (giá trị sẵn trong ô ngày), 123–124 (công tắc Tuần/Tháng giữ ngày)
        request.setAttribute("viewDate", date);
        if (requestedDate != null && !requestedDate.isBlank()) {
            // → my-pt-sessions.jsp dòng 142, 176, 181, 193 (tuần) và 232, 236 (tháng): viền + chữ "Đang chọn"
            request.setAttribute("selectedDate", date);
        }
        return date;
    }

    /**
     * HÀM PHỤ — gọi ở B1 của loadTimetable.
     *   ?openSession=120  mã buổi (Sessions.SessionId) cần mở sẵn hộp chi tiết
     *   ?backPackage=82   mã gói (Subscriptions.SubscriptionId) hoặc "single" → nút "‹ Gói của tôi" quay về đúng gói
     * Chỉ nhận số dương / "single"; buổi không có trong tuần thì JS tự bỏ qua (không lộ dữ liệu người khác).
     */
    private void readLinkParams(HttpServletRequest request) {
        // ?openSession= đến từ: my-bookings.jsp dòng 271, 404, 436, 473; _booking-groups.jspf dòng 20
        //   (nhúng ở my-bookings.jsp dòng 504); lịch tháng my-pt-sessions.jsp dòng 239
        Integer openSessionId = RequestUtil.getPositiveIntParam(request, "openSession");
        if (openSessionId != null) {
            // → my-pt-sessions.jsp dòng 63 <main data-open-session="…"> → my-pt-sessions.js đọc rồi mở hộp chi tiết
            request.setAttribute("openSessionId", openSessionId);
        }

        // ?backPackage= đến từ: my-bookings.jsp dòng 53 (weekLink), 268, 434
        String backPackageKey = request.getParameter("backPackage");
        if ("single".equals(backPackageKey) || RequestUtil.getPositiveIntParam(request, "backPackage") != null) {
            // → my-pt-sessions.jsp dòng 77–78 link "‹ Quay lại Gói của tôi" (/customer/bookings?package=…)
            request.setAttribute("backPackageKey", backPackageKey);
        }
    }

    /** HÀM PHỤ — gọi ở B3 của doGet. Menu trái đang chọn "Lịch tập" + class giao diện cho body. */
    private void setPageInfo(HttpServletRequest request) {
        // → layout-customer.jspf dòng 45–46 tô mục "Lịch tập" (jspf nhúng ở my-pt-sessions.jsp dòng 56);
        //   _schedule-header.jspf dòng 18, 28 chọn chữ nút (i) + hiện nút "Đặt buổi lẻ" (nhúng ở my-pt-sessions.jsp dòng 60)
        request.setAttribute("activeSidebar", "pt-sessions");
        // → my-pt-sessions.jsp dòng 52 <body class="dock ${themeClass}">
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-pt-sessions-page");
    }

}
