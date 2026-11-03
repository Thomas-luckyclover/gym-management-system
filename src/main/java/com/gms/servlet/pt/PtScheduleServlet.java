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
 * MÀN LỊCH DẠY PT + HỘP XỬ LÝ BUỔI — /pt/schedule
 * GET  ?date=&view=week|list[&handle=<SessionId>] → doGet: vẽ lịch tuần (+ mở hộp Xử lý buổi nếu có handle)
 * POST không có action (fetch từ schedule.js)     → doPost: Công bố khung nháp / gỡ khung → trả JSON
 * POST action=reschedule|transfer|cancel (form)  → doPost → handleSession: Dời buổi / Chuyển PT / Hủy buổi → redirect
 * Dữ liệu: PtScheduleService.getWeek → "week" (PtScheduleWeek) · PtSessionHandleService.getHandleForm → "handle" (PtHandleView)
 * → forward /WEB-INF/jsp/pt/schedule.jsp
 */
@WebServlet("/pt/schedule")
public class PtScheduleServlet extends HttpServlet {
    private static final String VIEW = "pt/schedule";
    private static final String PAGE_URL = "/pt/schedule";

    private final PtScheduleService scheduleService = new PtScheduleService();
    private final PtDashboardService dashboardService = new PtDashboardService();
    private final PtSessionHandleService handleService = new PtSessionHandleService();

    // GET /pt/schedule: vẽ lịch tuần.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chưa đăng nhập → /login
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: ?handle=<SessionId> (bấm 1 buổi trên lịch / nút "Xử lý buổi" ở Dashboard) → mở hộp Xử lý buổi
        Integer handleId = RequestUtil.getPositiveIntParam(request, "handle");

        // B3: dựng trang (showPage)
        showPage(request, response, accountId, handleId);
    }

    /**
     * HÀM PHỤ — AccountId trong session; null = chưa đăng nhập.
     */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /**
     * HÀM PHỤ (doGet, và handleSession khi xử lý buổi bị từ chối) — đặt dữ liệu rồi forward schedule.jsp.
     */
    private void showPage(HttpServletRequest request, HttpServletResponse response, int accountId, Integer handleId)
            throws ServletException, IOException {
        // B1: lịch tuần → "week" (hoặc "scheduleError")
        loadWeek(request, accountId);

        // B2: kiểu xem "week" | "list" → "view"
        loadView(request);

        // B3: có handle → dữ liệu hộp Xử lý buổi → "handle" (hoặc "handleError")
        if (handleId != null) {
            loadHandle(request, accountId, handleId);
        }

        // B4: CSRF, tiêu đề, mã PT → forward
        setPageInfo(request, accountId);

        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * HÀM PHỤ (showPage B1) — ?date= (sai / trống → hôm nay) → PtScheduleService.getWeek → "week" | "scheduleError";
     * đặt thêm "today" và "viewDate" (ngày đang xem).
     */
    private void loadWeek(HttpServletRequest request, int accountId) {
        LocalDateTime now = DateTimeUtil.now();
        LocalDate today = now.toLocalDate();

        LocalDate date = PtScheduleService.parseDate(request.getParameter("date"), today);
        request.setAttribute("today", today);
        request.setAttribute("viewDate", date);

        ServiceResult<PtScheduleWeek> result = scheduleService.getWeek(accountId, date, now);
        if (result.isSuccess()) {
            request.setAttribute("week", result.getData());
        } else {
            request.setAttribute("scheduleError", result.getMessage());
        }
    }

    /**
     * HÀM PHỤ (showPage B2) — ?view=list → "list", còn lại "week".
     */
    private void loadView(HttpServletRequest request) {
        String view = "week";
        if ("list".equals(request.getParameter("view"))) {
            view = "list";
        }

        request.setAttribute("view", view);
    }

    /**
     * HÀM PHỤ (showPage B3) — PtSessionHandleService.getHandleForm → "handle" (PtHandleView) | "handleError" (buổi không còn xử lý được).
     */
    private void loadHandle(HttpServletRequest request, int accountId, int sessionId) {
        ServiceResult<PtHandleView> result = handleService.getHandleForm(accountId, sessionId, DateTimeUtil.now());
        if (result.isSuccess()) {
            request.setAttribute("handle", result.getData());
        } else {
            request.setAttribute("handleError", result.getMessage());
        }
    }

    /**
     * HÀM PHỤ (showPage B4) — csrfToken (form Công bố + 3 form xử lý) · draftAccountId (khóa lưu nháp trong trình duyệt)
     * · activeNav, pageTitle, pageHelp (_header.jspf) · ptEmployeeCode (thẻ tài khoản).
     */
    private void setPageInfo(HttpServletRequest request, int accountId) {
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));

        request.setAttribute("draftAccountId", accountId);

        request.setAttribute("activeNav", "schedule");

        request.setAttribute("pageTitle", "Lịch dạy");
        request.setAttribute("pageHelp", "Bấm vào chỗ trống trên lịch (hoặc nút Thêm khung) để thêm khung rảnh. "
                + "Bấm vào khung Còn trống để đánh dấu gỡ. Khách chỉ thấy thay đổi sau khi bạn bấm Công bố.");
        ServiceResult<String> employeeCode = dashboardService.getPtEmployeeCode(accountId);
        if (employeeCode.isSuccess()) {
            request.setAttribute("ptEmployeeCode", employeeCode.getData());
        }
    }

    // POST /pt/schedule: 2 loại — form Xử lý buổi (có action) hoặc Công bố (fetch JSON, không có action).
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // B1: có ô action = form Xử lý buổi (trả trang); không = Công bố (trả JSON)
        boolean handleForm = request.getParameter("action") != null;

        // B2: hết phiên đăng nhập → form: về /login · JSON: 401 + MSG06
        Integer accountId = getAccountId(request);
        if (accountId == null && handleForm) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }
        if (accountId == null) {
            JsonUtil.writeJson(response, 401, JsonResponse.error(Messages.MSG06_SESSION_EXPIRED));
            return;
        }

        // B3: không phải vai trò PT hoặc sai CSRF → form: toast MSG07 + về lịch · JSON: 403 + MSG07
        if (!isPtWithValidToken(request)) {
            if (handleForm) {
                RequestUtil.setFlashToast(request, "error", Messages.MSG07_NO_PERMISSION);
                RequestUtil.redirect(request, response, PAGE_URL);
                return;
            }
            JsonUtil.writeJson(response, 403, JsonResponse.error(Messages.MSG07_NO_PERMISSION));
            return;
        }

        // B4: form Xử lý buổi → handleSession
        String action = request.getParameter("action");
        if (action != null) {
            handleSession(request, response, accountId, action);
            return;
        }

        // B5: Công bố: slotDate[] + startTime[] (khung nháp) · removeSlotId[] (khung gỡ) · duration (P-06 lúc mở trang)
        //     → PtScheduleService.publish → ServiceResult<Integer> (số khung đã đổi)
        String[] newSlotDates = request.getParameterValues("slotDate");
        String[] newStartTimes = request.getParameterValues("startTime");
        String[] removeSlotIds = request.getParameterValues("removeSlotId");
        Integer duration = RequestUtil.getPositiveIntParam(request, "duration");
        ServiceResult<Integer> result = scheduleService.publish(accountId, newSlotDates, newStartTimes, removeSlotIds,
                duration == null ? 0 : duration, DateTimeUtil.now());

        // B6: thành công → đặt toast cho lần tải trang sau (JS tự tải lại) · trả JSON {success, message}
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        }
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }

    /**
     * HÀM PHỤ (doPost B3) — session đang ở vai trò PT VÀ csrfToken đúng.
     */
    private boolean isPtWithValidToken(HttpServletRequest request) {
        Object role = request.getSession(false).getAttribute(SessionKey.ROLE);
        boolean isPt = Role.PT.getDbValue().equals(role);
        return isPt && RequestUtil.isValidCsrfToken(request);
    }

    /**
     * HÀM PHỤ (doPost B4) — 3 form trong hộp Xử lý buổi.
     */
    private void handleSession(HttpServletRequest request, HttpServletResponse response, int accountId, String action)
            throws IOException {
        // B1: ô ẩn sessionId sai → 400
        Integer sessionId = RequestUtil.getPositiveIntParam(request, "sessionId");
        if (sessionId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // B2: gọi đúng hàm theo action → ServiceResult<LocalDate> (ngày để quay lại lịch):
        //     reschedule → PtSessionHandleService.reschedule (timeSlotId = khung mới, reason = ghi chú)
        //     transfer   → PtSessionHandleService.transfer   (timeSlotId = khung của PT nhận)
        //     cancel     → PtSessionHandleService.cancel     (reason = lý do, refundAgreed = ô tích đã thỏa thuận hoàn tiền)
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

        // B3: thành công → toast + redirect về tuần có buổi (?date=…&view=…#session-<id> để cuộn tới buổi; chuyển PT thì không cuộn)
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            String view = "list".equals(request.getParameter("view")) ? "list" : "week";
            String anchor = "transfer".equals(action) ? "" : "#session-" + sessionId;
            RequestUtil.redirect(request, response, PAGE_URL + "?date=" + result.getData() + "&view=" + view + anchor);
            return;
        }

        // B4: bị từ chối → vẽ lại trang với hộp đang mở: handleFormError (câu lỗi), handleAction (tab đang chọn), handleReason (chữ đã gõ)
        request.setAttribute("handleFormError", result.getMessage());
        request.setAttribute("handleAction", action);
        request.setAttribute("handleReason", reason);
        try {
            showPage(request, response, accountId, sessionId);
        } catch (ServletException exception) {
            throw new IOException(exception);
        }
    }
}
