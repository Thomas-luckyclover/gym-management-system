package com.gms.servlet.pt;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.dto.PtScheduleWeek;
import com.gms.dto.ServiceResult;
import com.gms.service.PtDashboardService;
import com.gms.service.PtScheduleService;
import com.gms.util.DateTimeUtil;
import com.gms.util.JsonUtil;
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
 * SERVLET MÀN LỊCH DẠY PT — URL /pt/schedule
 *
 *   GET  /pt/schedule?date=2026-10-27&view=week   mở trang: Service dựng tuần → schedule.jsp
 *   POST /pt/schedule                             bấm Công bố (JS gửi AJAX): Service lưu → trả JSON
 *
 *   doGet   HÀM CHÍNH: getAccountId → loadWeek, loadView, setPageInfo → JSP
 *   doPost  HÀM CHÍNH: getAccountId → isPtWithValidToken → scheduleService.publish → JSON
 *
 * Quyền PT cho GET do PtAreaFilter (@WebFilter "/pt/*") kiểm tra trước.
 * POST tự kiểm tra lại vai trò + CSRF vì đây là thao tác GHI.
 */
@WebServlet("/pt/schedule")
public class PtScheduleServlet extends HttpServlet {

    private static final String VIEW = "pt/schedule";       // file JSP: /WEB-INF/jsp/pt/schedule.jsp
    private static final String PAGE_URL = "/pt/schedule";  // URL trang, để quay lại sau khi đăng nhập

    private final PtScheduleService scheduleService = new PtScheduleService();
    private final PtDashboardService dashboardService = new PtDashboardService(); // chỉ để lấy mã nhân viên NV-xxxx


    // ================================================================================================================
    // 1. MỞ TRANG (GET)
    // ================================================================================================================

    /**
     * HÀM CHÍNH — trình duyệt gọi khi mở /pt/schedule hoặc bấm ‹ › / Tuần / Danh sách.
     * Chỉ ĐỌC dữ liệu, không ghi gì.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: chưa đăng nhập → về trang đăng nhập, xong quay lại đây.
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: nhờ Service dựng tuần; đọc kiểu xem (week / list).
        loadWeek(request, accountId);
        loadView(request);

        // B3: dữ liệu phụ cho JSP và thanh đầu trang (giống PT Dashboard: tên + mã nhân viên + nút i).
        setPageInfo(request, accountId);

        // B4: mở JSP.
        RequestUtil.forward(request, response, VIEW);
    }

    /** HÀM PHỤ — gọi ở B1 của doGet và doPost. Mã tài khoản trong session; chưa đăng nhập → null. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);   // false = không tạo session mới
        if (session == null) {
            return null;
        }
        return (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /**
     * HÀM PHỤ — gọi ở B2 của doGet.
     * Đọc ?date= trên URL → gọi Service.getWeek → đặt "week" (thành công) hoặc "scheduleError" (lỗi).
     */
    private void loadWeek(HttpServletRequest request, int accountId) {
        // B1: giờ Việt Nam lấy MỘT lần, dùng chung cho cả trang.
        LocalDateTime now = DateTimeUtil.now();
        LocalDate today = now.toLocalDate();

        // B2: ngày muốn xem; thiếu hoặc sai → hôm nay.
        //   ?date= đến từ schedule.jsp dòng 74–75 (‹ tuần trước / tuần sau ›), 77–78 (công tắc Tuần / Danh sách giữ ngày).
        //   Nút "Tuần này" dòng 76 không gửi date → hôm nay.
        LocalDate date = PtScheduleService.parseDate(request.getParameter("date"), today);
        request.setAttribute("today", today);      // → _header.jspf dòng 38 ngày ở topbar (nhúng ở schedule.jsp dòng 52)
        request.setAttribute("viewDate", date);    // → schedule.jsp dòng 77–78 (link Tuần / Danh sách giữ đúng ngày)

        // B3: Service dựng tuần. Lỗi thì JSP hiện thông báo, KHÔNG hiện lịch trống.
        ServiceResult<PtScheduleWeek> result = scheduleService.getWeek(accountId, date, now);
        if (result.isSuccess()) {
            // → schedule.jsp dòng 43 (có week mới nạp schedule.js), 74–75 (‹ ›), 83–92 (data-… cho JS),
            //   104–132 (tiêu đề tuần, form Thêm khung), 158–169 (trục giờ + 7 cột ngày), 276 (duration gửi khi Công bố)
            request.setAttribute("week", result.getData());
        } else {
            request.setAttribute("scheduleError", result.getMessage());   // → schedule.jsp dòng 60–63 khung đỏ + nút Tải lại
        }
    }

    /** HÀM PHỤ — gọi ở B2 của doGet. Chỉ nhận "list"; mọi giá trị khác (kể cả thiếu) → "week". */
    private void loadView(HttpServletRequest request) {
        // ?view= đến từ schedule.jsp dòng 74–76 (‹ › và "Tuần này" giữ kiểu xem), 77–78 (công tắc Tuần / Danh sách)
        String view = "week";
        if ("list".equals(request.getParameter("view"))) {
            view = "list";
        }
        // → schedule.jsp dòng 74–76 (giữ kiểu xem khi đổi tuần), 89 (class is-list), 145–146 (công tắc tô đậm)
        request.setAttribute("view", view);
    }

    /**
     * HÀM PHỤ — gọi ở B3 của doGet.
     * csrfToken cho form Công bố, khóa lưu nháp, menu, tiêu đề + nút (i), mã nhân viên (lỗi thì chỉ không hiện mã).
     */
    private void setPageInfo(HttpServletRequest request, int accountId) {
        // → schedule.jsp dòng 275 ô ẩn csrfToken trong form Công bố (doPost kiểm tra lại ở B2)
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        // → schedule.jsp dòng 92 data-draft-key="mãTàiKhoản:ngàyThứ2" — schedule.js dùng làm khóa lưu nháp
        request.setAttribute("draftAccountId", accountId);
        // → layout-pt.jspf dòng 64–65 tô mục "Lịch dạy" (nhúng ở schedule.jsp dòng 49)
        request.setAttribute("activeNav", "schedule");
        // → _header.jspf dòng 25, 31 tiêu đề topbar; dòng 29–32 nút (i) + chữ hướng dẫn (nhúng ở schedule.jsp dòng 52)
        request.setAttribute("pageTitle", "Lịch dạy");
        request.setAttribute("pageHelp", "Bấm vào chỗ trống trên lịch (hoặc nút Thêm khung) để thêm khung rảnh. "
                + "Bấm vào khung Còn trống để đánh dấu gỡ. Khách chỉ thấy thay đổi sau khi bạn bấm Công bố.");
        ServiceResult<String> employeeCode = dashboardService.getPtEmployeeCode(accountId);
        if (employeeCode.isSuccess()) {
            // → _header.jspf dòng 69–71 "NV-0005" dưới tên PT
            request.setAttribute("ptEmployeeCode", employeeCode.getData());
        }
    }


    // ================================================================================================================
    // 2. CÔNG BỐ (POST)
    // ================================================================================================================

    /**
     * HÀM CHÍNH — JS publishPtChanges gửi POST khi PT bấm Công bố.
     *
     * Tham số nhận — schedule.js (publishPtChanges) gom bằng new FormData(form #pt-publish-form):
     *   slotDate[] + startTime[]   các khung MỚI, đi theo cặp (ví dụ 2026-10-27 + 12:30)
     *                              ← 2 ô ẩn trong <template> khung nháp, schedule.jsp dòng 299–300
     *   removeSlotId[]             mã các khung muốn GỠ (TimeSlots.TimeSlotId, ví dụ 773)
     *                              ← ô checkbox trên thẻ khung trống, schedule.jsp dòng 207
     *   duration                   P-06 trang đang dùng ← ô ẩn schedule.jsp dòng 276
     *   csrfToken                  chống giả mạo request ← ô ẩn schedule.jsp dòng 275
     *
     * KHÔNG nhận mã PT hay giờ kết thúc từ trình duyệt: mã PT lấy từ session, giờ kết thúc Service tự tính.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // B1: phải đăng nhập.
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            JsonUtil.writeJson(response, 401, JsonResponse.error(Messages.MSG06_SESSION_EXPIRED));
            return;
        }

        // B2: phải là PT và có CSRF token đúng.
        if (!isPtWithValidToken(request)) {
            JsonUtil.writeJson(response, 403, JsonResponse.error(Messages.MSG07_NO_PERMISSION));
            return;
        }

        // B3: chuyển nguyên các mảng cho Service; Service kiểm tra lại MỌI giá trị.
        //     Không có tham số nào thì getParameterValues trả null.
        String[] newSlotDates = request.getParameterValues("slotDate");      // ← schedule.jsp dòng 299
        String[] newStartTimes = request.getParameterValues("startTime");   // ← schedule.jsp dòng 300
        String[] removeSlotIds = request.getParameterValues("removeSlotId"); // ← schedule.jsp dòng 207
        Integer duration = RequestUtil.getPositiveIntParam(request, "duration"); // ← schedule.jsp dòng 276
        ServiceResult<Integer> result = scheduleService.publish(accountId, newSlotDates, newStartTimes, removeSlotIds,
                duration == null ? 0 : duration, DateTimeUtil.now());

        // B4: thành công → lưu toast để hiện sau khi JS tải lại trang; luôn trả JSON cho JS.
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        }
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }

    /**
     * HÀM PHỤ — gọi ở B2 của doPost.
     * Vai trò đang chọn trong session phải là PT VÀ csrfToken gửi lên phải khớp token trong session.
     */
    private boolean isPtWithValidToken(HttpServletRequest request) {
        Object role = request.getSession(false).getAttribute(SessionKey.ROLE);
        boolean isPt = Role.PT.getDbValue().equals(role);
        return isPt && RequestUtil.isValidCsrfToken(request);
    }
}
