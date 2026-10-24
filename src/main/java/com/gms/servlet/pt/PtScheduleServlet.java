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
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        request.setAttribute("draftAccountId", accountId);   // JS dùng làm khóa lưu nháp theo tài khoản
        request.setAttribute("activeNav", "schedule");
        request.setAttribute("pageTitle", "Lịch dạy");
        request.setAttribute("pageHelp", "Bấm vào chỗ trống trên lịch (hoặc nút Thêm khung) để thêm khung rảnh. "
                + "Bấm vào khung Còn trống để đánh dấu gỡ. Khách chỉ thấy thay đổi sau khi bạn bấm Công bố.");
        ServiceResult<String> employeeCode = dashboardService.getPtEmployeeCode(accountId);
        if (employeeCode.isSuccess()) {
            request.setAttribute("ptEmployeeCode", employeeCode.getData());
        }

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
        LocalDate date = PtScheduleService.parseDate(request.getParameter("date"), today);
        request.setAttribute("today", today);
        request.setAttribute("viewDate", date);

        // B3: Service dựng tuần. Lỗi thì JSP hiện thông báo, KHÔNG hiện lịch trống.
        ServiceResult<PtScheduleWeek> result = scheduleService.getWeek(accountId, date, now);
        if (result.isSuccess()) {
            request.setAttribute("week", result.getData());
        } else {
            request.setAttribute("scheduleError", result.getMessage());
        }
    }

    /** HÀM PHỤ — gọi ở B2 của doGet. Chỉ nhận "list"; mọi giá trị khác (kể cả thiếu) → "week". */
    private void loadView(HttpServletRequest request) {
        String view = "week";
        if ("list".equals(request.getParameter("view"))) {
            view = "list";
        }
        request.setAttribute("view", view);
    }


    // ================================================================================================================
    // 2. CÔNG BỐ (POST)
    // ================================================================================================================

    /**
     * HÀM CHÍNH — JS publishPtChanges gửi POST khi PT bấm Công bố.
     *
     * Tham số nhận:
     *   slotDate[] + startTime[]   các khung MỚI, đi theo cặp (ví dụ 2026-10-27 + 12:30)
     *   removeSlotId[]             mã các khung muốn GỠ (ví dụ 773)
     *   duration                   P-06 trang đang dùng; csrfToken chống giả mạo request
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
        Object role = request.getSession(false).getAttribute(SessionKey.ROLE);
        boolean isPt = Role.PT.getDbValue().equals(role);
        if (!isPt || !RequestUtil.isValidCsrfToken(request)) {
            JsonUtil.writeJson(response, 403, JsonResponse.error(Messages.MSG07_NO_PERMISSION));
            return;
        }

        // B3: chuyển nguyên các mảng cho Service; Service kiểm tra lại MỌI giá trị.
        //     Không có tham số nào thì getParameterValues trả null.
        Integer duration = RequestUtil.getPositiveIntParam(request, "duration");
        ServiceResult<Integer> result = scheduleService.publish(accountId,
                request.getParameterValues("slotDate"),
                request.getParameterValues("startTime"),
                request.getParameterValues("removeSlotId"),
                duration == null ? 0 : duration,
                DateTimeUtil.now());

        // B4: thành công → lưu toast để hiện sau khi JS tải lại trang; luôn trả JSON cho JS.
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        }
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }
}
