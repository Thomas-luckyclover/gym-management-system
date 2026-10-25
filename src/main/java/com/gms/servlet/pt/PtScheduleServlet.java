package com.gms.servlet.pt;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.dto.PtHandleView;
import com.gms.dto.PtScheduleWeek;
import com.gms.dto.ServiceResult;
import com.gms.service.PtDashboardService;
import com.gms.service.PtScheduleService;
import com.gms.service.PtSessionHandleService;
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
 *   GET  /pt/schedule?date=2026-10-27&view=week        mở trang: Service dựng tuần → schedule.jsp
 *   GET  /pt/schedule?date=…&view=…&handle=120         như trên + mở hộp "Xử lý buổi" của buổi 120
 *   POST /pt/schedule                                  bấm Công bố (JS gửi AJAX): Service lưu → trả JSON
 *   POST /pt/schedule  action=reschedule|transfer|cancel   form trong hộp Xử lý buổi (POST thường, không AJAX)
 *
 *   doGet   HÀM CHÍNH: getAccountId → showPage (loadWeek, loadView, loadHandle, setPageInfo) → JSP
 *   doPost  HÀM CHÍNH: getAccountId → isPtWithValidToken → có action? handleSession : scheduleService.publish
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
    private final PtSessionHandleService handleService = new PtSessionHandleService(); // hộp Xử lý buổi


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

        // B2: ?handle=120 (link trên thẻ buổi, hoặc nút "Xử lý buổi" ở PT Dashboard) → mở sẵn hộp của buổi đó.
        //     Thiếu / không phải số dương → null → không mở hộp.
        Integer handleId = RequestUtil.getPositiveIntParam(request, "handle");

        // B3: dựng trang (cùng hàm doPost dùng lại khi Lưu bị từ chối).
        showPage(request, response, accountId, handleId);
    }

    /**
     * HÀM PHỤ — gọi ở B3 của doGet, và ở doPost (handleSession) khi Lưu bị từ chối để mở lại hộp kèm câu lỗi.
     * Thứ tự: tuần → kiểu xem → hộp xử lý (nếu có) → thông tin đầu trang → JSP.
     */
    private void showPage(HttpServletRequest request, HttpServletResponse response, int accountId, Integer handleId)
            throws ServletException, IOException {
        // B1: nhờ Service dựng tuần; đọc kiểu xem (week / list).
        loadWeek(request, accountId);
        loadView(request);

        // B2: có mã buổi cần xử lý → nhờ Service chuẩn bị hộp.
        if (handleId != null) {
            loadHandle(request, accountId, handleId);
        }

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
     * HÀM PHỤ — gọi ở B1 của showPage.
     * Đọc ?date= trên URL → gọi Service.getWeek → đặt "week" (thành công) hoặc "scheduleError" (lỗi).
     */
    private void loadWeek(HttpServletRequest request, int accountId) {
        // B1: giờ Việt Nam lấy MỘT lần, dùng chung cho cả trang.
        LocalDateTime now = DateTimeUtil.now();
        LocalDate today = now.toLocalDate();

        // B2: ngày muốn xem; thiếu hoặc sai → hôm nay.
        //   ?date= đến từ schedule.jsp dòng 77–78 (‹ tuần trước / tuần sau ›), 80–81 (công tắc Tuần / Danh sách giữ ngày).
        //   Nút "Tuần này" dòng 79 không gửi date → hôm nay.
        LocalDate date = PtScheduleService.parseDate(request.getParameter("date"), today);
        request.setAttribute("today", today);      // → _header.jspf dòng 37 ngày ở topbar (nhúng ở schedule.jsp dòng 55)
        request.setAttribute("viewDate", date);    // → schedule.jsp dòng 80–81 (link Tuần / Danh sách giữ đúng ngày), _handle-dialog.jspf (nút Đóng + ô ẩn date)

        // B3: Service dựng tuần. Lỗi thì JSP hiện thông báo, KHÔNG hiện lịch trống.
        ServiceResult<PtScheduleWeek> result = scheduleService.getWeek(accountId, date, now);
        if (result.isSuccess()) {
            // → schedule.jsp dòng 46 (có week mới nạp schedule.js), 77–78 (‹ ›), 86–95 (data-… cho JS),
            //   107–135 (tiêu đề tuần, form Thêm khung), 168–179 (trục giờ + 7 cột ngày), 301 (duration gửi khi Công bố)
            request.setAttribute("week", result.getData());
        } else {
            request.setAttribute("scheduleError", result.getMessage());   // → schedule.jsp dòng 63–66 khung đỏ + nút Tải lại
        }
    }

    /** HÀM PHỤ — gọi ở B1 của showPage. Chỉ nhận "list"; mọi giá trị khác (kể cả thiếu) → "week". */
    private void loadView(HttpServletRequest request) {
        // ?view= đến từ schedule.jsp dòng 77–79 (‹ › và "Tuần này" giữ kiểu xem), 80–81 (công tắc Tuần / Danh sách)
        String view = "week";
        if ("list".equals(request.getParameter("view"))) {
            view = "list";
        }
        // → schedule.jsp dòng 77–79 (giữ kiểu xem khi đổi tuần), 92 (class is-list), 148–149 (công tắc tô đậm),
        //   link thẻ buổi ?handle= (dòng 239), _handle-dialog.jspf (ô ẩn view)
        request.setAttribute("view", view);
    }

    /**
     * HÀM PHỤ — gọi ở B2 của showPage.
     * Gọi handleService.getHandleForm MỘT lần → đặt "handle" (hộp mở) hoặc "handleError" (buổi không xử lý được).
     */
    private void loadHandle(HttpServletRequest request, int accountId, int sessionId) {
        ServiceResult<PtHandleView> result = handleService.getHandleForm(accountId, sessionId, DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("handle", result.getData());          // → _handle-dialog.jspf (nhúng ở schedule.jsp dòng 337, KHỐI 4)
        } else {
            request.setAttribute("handleError", result.getMessage());  // → schedule.jsp dòng 156–159 dòng báo lỗi đỏ trên lịch
        }
    }

    /**
     * HÀM PHỤ — gọi ở B3 của showPage.
     * csrfToken cho form Công bố, khóa lưu nháp, menu, tiêu đề + nút (i), mã nhân viên (lỗi thì chỉ không hiện mã).
     */
    private void setPageInfo(HttpServletRequest request, int accountId) {
        // → schedule.jsp dòng 300 ô ẩn csrfToken trong form Công bố + 3 form của _handle-dialog.jspf (doPost kiểm tra lại ở B2)
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        // → schedule.jsp dòng 95 data-draft-key="mãTàiKhoản:ngàyThứ2" — schedule.js dùng làm khóa lưu nháp
        request.setAttribute("draftAccountId", accountId);
        // → layout-pt.jspf dòng 64–65 tô mục "Lịch dạy" (nhúng ở schedule.jsp dòng 55)
        request.setAttribute("activeNav", "schedule");
        // → _header.jspf dòng 24, 30 tiêu đề topbar; dòng 28–31 nút (i) + chữ hướng dẫn (nhúng ở schedule.jsp dòng 55)
        request.setAttribute("pageTitle", "Lịch dạy");
        request.setAttribute("pageHelp", "Bấm vào chỗ trống trên lịch (hoặc nút Thêm khung) để thêm khung rảnh. "
                + "Bấm vào khung Còn trống để đánh dấu gỡ. Khách chỉ thấy thay đổi sau khi bạn bấm Công bố.");
        ServiceResult<String> employeeCode = dashboardService.getPtEmployeeCode(accountId);
        if (employeeCode.isSuccess()) {
            // → _header.jspf dòng 60–62 "NV-0005" dưới tên PT
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
     *                              ← 2 ô ẩn trong <template> khung nháp, schedule.jsp dòng 324–325
     *   removeSlotId[]             mã các khung muốn GỠ (TimeSlots.TimeSlotId, ví dụ 773)
     *                              ← ô checkbox trên thẻ khung trống, schedule.jsp dòng 219
     *   duration                   P-06 trang đang dùng ← ô ẩn schedule.jsp dòng 301
     *   csrfToken                  chống giả mạo request ← ô ẩn schedule.jsp dòng 300
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

        // B2b: form trong hộp Xử lý buổi có ô action → nhánh riêng (POST thường, trả trang chứ không trả JSON).
        String action = request.getParameter("action");
        if (action != null) {
            handleSession(request, response, accountId, action);
            return;
        }

        // B3: chuyển nguyên các mảng cho Service; Service kiểm tra lại MỌI giá trị.
        //     Không có tham số nào thì getParameterValues trả null.
        String[] newSlotDates = request.getParameterValues("slotDate");      // ← schedule.jsp dòng 324
        String[] newStartTimes = request.getParameterValues("startTime");   // ← schedule.jsp dòng 325
        String[] removeSlotIds = request.getParameterValues("removeSlotId"); // ← schedule.jsp dòng 219
        Integer duration = RequestUtil.getPositiveIntParam(request, "duration"); // ← schedule.jsp dòng 301
        ServiceResult<Integer> result = scheduleService.publish(accountId, newSlotDates, newStartTimes, removeSlotIds,
                duration == null ? 0 : duration, DateTimeUtil.now());

        // B4: thành công → lưu toast để hiện sau khi JS tải lại trang; luôn trả JSON cho JS.
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        }
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }

    /**
     * HÀM PHỤ — gọi ở B2b của doPost. Tham số nhận từ _handle-dialog.jspf (mỗi phương án 1 form):
     *   action        reschedule | transfer | cancel
     *   sessionId     mã buổi (Sessions.SessionId) đang xử lý
     *   timeSlotId    khung đích (radio) — chỉ Dời buổi / Chuyển PT
     *   reason        ghi chú khi dời (không bắt buộc) / lý do hủy (bắt buộc 5–500 ký tự)
     *   refundAgreed  "on" khi PT tích "khách đã đồng ý nhận lại tiền mặt" — chỉ Hủy buổi
     *   date, view    tuần + kiểu xem đang mở, để quay về đúng chỗ
     *
     * Thành công → toast + redirect (PRG: F5 không gửi lại form) về tuần có buổi, cuộn tới thẻ #session-120.
     * Bị từ chối → KHÔNG redirect: dựng lại trang, hộp mở đúng phương án, giữ chữ PT đã nhập, câu lỗi ở đầu hộp.
     */
    private void handleSession(HttpServletRequest request, HttpServletResponse response, int accountId, String action)
            throws IOException {
        Integer sessionId = RequestUtil.getPositiveIntParam(request, "sessionId");
        if (sessionId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // B1: gọi đúng 1 hàm Service theo phương án PT chọn.
        String slotId = request.getParameter("timeSlotId");
        String reason = request.getParameter("reason");
        ServiceResult<LocalDate> result = switch (action) {
            case "reschedule" -> handleService.reschedule(accountId, sessionId, slotId, reason, DateTimeUtil.now());
            case "transfer" -> handleService.transfer(accountId, sessionId, slotId, DateTimeUtil.now());
            case "cancel" -> handleService.cancel(accountId, sessionId, reason,
                    request.getParameter("refundAgreed") != null, DateTimeUtil.now());
            default -> null;
        };
        if (result == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // B2: thành công → toast + về tuần có buổi (dời buổi: tuần của ngày mới).
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            String view = "list".equals(request.getParameter("view")) ? "list" : "week";
            String anchor = "transfer".equals(action) ? "" : "#session-" + sessionId;
            RequestUtil.redirect(request, response, PAGE_URL + "?date=" + result.getData() + "&view=" + view + anchor);
            return;
        }

        // B3: bị từ chối → mở lại hộp với câu lỗi + giữ phương án và chữ đã nhập.
        request.setAttribute("handleFormError", result.getMessage());   // → _handle-dialog.jspf đầu hộp
        request.setAttribute("handleAction", action);                    // → radio phương án được chọn sẵn
        request.setAttribute("handleReason", reason);                    // → ô ghi chú / lý do
        try {
            showPage(request, response, accountId, sessionId);
        } catch (ServletException exception) {
            throw new IOException(exception);
        }
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
