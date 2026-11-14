package com.gms.servlet.pt;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.dto.PtHandleView;
import com.gms.dto.PtScheduleWeek;
import com.gms.dto.ServiceResult;
import com.gms.service.NotificationService;
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
 * SERVLET MÀN LỊCH DẠY PT + HỘP XỬ LÝ BUỔI — /pt/schedule (UC-30 công bố khung rảnh, UC-34 dời / chuyển / hủy buổi).
 *
 * VIỆC CỦA SERVLET: đọc tham số → gọi PtScheduleService / PtSessionHandleService → đặt attribute / toast → forward JSP, redirect hoặc JSON.
 *   Servlet KHÔNG kiểm luật (giờ mở cửa, ca nghỉ, chồng khung, lịch khách…): Service làm hết.
 * TRƯỚC SERVLET: PtAreaFilter chặn vai trò khác khi GET; POST tự kiểm vai trò PT + CSRF (isPtWithValidToken).
 *
 *   URL / nút                                              Servlet gọi                                   Kết quả
 *   GET  /pt/schedule?date=&view=week|list                  doGet → showPage → scheduleService.getWeek    forward schedule.jsp (attribute "week")
 *   GET  /pt/schedule?…&handle=<SessionId>                  showPage → handleService.getHandleForm        như trên + hộp Xử lý buổi ("handle")
 *   POST (không action, fetch từ schedule.js) "Công bố"     doPost → scheduleService.publish              JSON {success, message} (+ toast lần tải sau)
 *   POST action=reschedule  "Dời buổi"                      handleSession → handleService.reschedule      toast + redirect về tuần có buổi
 *   POST action=transfer    "Chuyển PT"                     handleSession → handleService.transfer        toast + redirect / vẽ lại hộp kèm lỗi
 *   POST action=cancel      "Hủy buổi"                      handleSession → handleService.cancel          toast + redirect / vẽ lại hộp kèm lỗi
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  doGet    vẽ lịch tuần (+ hộp Xử lý buổi)    → showPage, loadWeek, loadView, loadHandle, setPageInfo
 *   HÀM CHÍNH 2  doPost   Công bố (JSON) / 3 form xử lý buổi  → isPtWithValidToken, handleSession
 *   DÙNG CHUNG            getAccountId
 */
@WebServlet("/pt/schedule")
public class PtScheduleServlet extends HttpServlet {
    private static final String VIEW = "pt/schedule";
    private static final String PAGE_URL = "/pt/schedule";

    private final PtScheduleService scheduleService = new PtScheduleService();
    private final PtSessionHandleService handleService = new PtSessionHandleService();
    private final NotificationService notificationService = new NotificationService();



    // ============================================================================================================
    // HÀM CHÍNH 1. doGet — vẽ lịch tuần, mở hộp Xử lý buổi khi có ?handle=
    //   Ai gọi: menu "Lịch dạy", nút "Xử lý buổi" ở Dashboard PT, redirect sau POST  ·  Ra: forward schedule.jsp
    //   Hàm phụ ngay dưới, theo thứ tự showPage gọi: showPage → loadWeek → loadView → loadHandle → setPageInfo
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — vẽ lịch dạy 1 tuần (+ hộp Xử lý buổi khi có ?handle=).
     *   Ai gọi: menu "Lịch dạy", nút "Xử lý buổi" ở Dashboard, redirect sau POST
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

        // Tự động đánh dấu đã đọc các thông báo liên quan đến lịch dạy PT khi vào trang
        notificationService.markTopicNotificationsRead(accountId, "buổi tập", "dạy thay", "gói PT", "khách gói PT");

        // B2: có ?handle= → sẽ mở hộp Xử lý buổi
        Integer handleId = RequestUtil.getPositiveIntParam(request, "handle");

        // B3: dựng trang
        showPage(
                request,
                response,
                accountId,
                handleId);
    }

    /**
     * HÀM PHỤ (doGet B3; handleSession B4 khi xử lý buổi bị từ chối) — đặt dữ liệu rồi forward schedule.jsp:
     *   loadWeek → loadView → [loadHandle] → setPageInfo.
     */
    private void showPage(
            HttpServletRequest request,
            HttpServletResponse response,
            int accountId,
            Integer handleId)
            throws ServletException, IOException {
        // B1: lịch tuần
        loadWeek(request, accountId);

        // B2: kiểu xem Tuần / Danh sách
        loadView(request);

        // B3: dữ liệu hộp Xử lý buổi (nếu có)
        if (handleId != null) {
            loadHandle(request, accountId, handleId);
        }

        // B4: tiêu đề, CSRF → hiện trang
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
        request.setAttribute("today", today); //Attribute 1: để tô màu cho cột "HÔM NAY"
        request.setAttribute("viewDate", date); //Attribute 2: Ngày đang xem, giữ khi chuyển Tuần ↔ Danh sách

        ServiceResult<PtScheduleWeek> result = scheduleService.getWeek(accountId, date, now);
        if (result.isSuccess()) {
            request.setAttribute("week", result.getData()); // Attribute 3: Thành công PTScheduleWeek
        } else {
            request.setAttribute("scheduleError", result.getMessage()); // Attribute 4: Thất bại PTScheduleWeek: Khi 1. Không phải Working PT hoặc lỗi cấu hình DB
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

        request.setAttribute("view", view); // Attribute 5: Chọn bảng tuần hay danh sách
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
     * · activeNav, pageTitle, pageHelp (_header.jspf).
     */
    private void setPageInfo(HttpServletRequest request, int accountId) {
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));

        request.setAttribute("draftAccountId", accountId);

        request.setAttribute("activeNav", "schedule");

        request.setAttribute("pageTitle", "Lịch dạy");
        // Hướng dẫn nhiều dòng (\n → xuống dòng; CSS #pt-page-help white-space: pre-line). Giải thích đủ từng màu ô
        // + chữ trạng thái trên ô, để PT mới không phải đoán "Đang giữ", "Chưa chốt" là gì.
        request.setAttribute("pageHelp", String.join("\n",
                "THÊM / GỠ KHUNG",
                "• Bấm chỗ trống trên lịch (hoặc nút Thêm khung) → thêm khung nháp. Chỗ trùng khung khác sẽ báo đỏ, không thêm được.",
                "• Bấm khung Còn trống → đánh dấu gỡ. Bấm lại để giữ.",
                "• Khách chỉ thấy thay đổi sau khi bạn bấm Công bố.",
                "",
                "MÀU Ô",
                "• Còn trống: khung đã công bố, chưa ai đặt.",
                "• Có khách: đã có buổi. Bấm buổi sắp tới để Dời / Chuyển PT / Hủy.",
                "• Đang giữ: một đơn mua gói PT đang giữ khung này chờ thanh toán. Trả tiền → Có khách; hết hạn / hủy đơn → Còn trống.",
                "• Tạm khóa: khung trùng ca bạn xin nghỉ (chờ duyệt hoặc đã duyệt), khách không đặt được.",
                "• Nháp mới: khung vừa thêm, chưa lưu. Chờ gỡ: khung sẽ gỡ khi Công bố.",
                "",
                "CHỮ TRÊN BUỔI",
                "• Sắp tới / Đang diễn ra: buổi chưa xong.",
                "• Chưa chốt: buổi đã hết giờ, hệ thống sẽ tự chốt check-in / vắng lúc 00:05 hôm sau.",
                "• Đã check-in · Vắng · Đã hủy: kết quả cuối, chỉ xem."));
    }



    // ============================================================================================================
    // HÀM CHÍNH 2. doPost — 2 loại: Công bố (fetch JSON, không có action) · form Xử lý buổi (action=reschedule|transfer|cancel)
    //   Ai gọi: schedule.js (nút Công bố) · 3 form trong _schedule-handle*.jspf  ·  Gọi xuống: PtScheduleService.publish, PtSessionHandleService
    //   Hàm phụ ngay dưới: isPtWithValidToken · handleSession
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — Công bố khung (fetch, trả JSON) hoặc 3 form Xử lý buổi (có ô action).
     *   Ai gọi: schedule.js · form trong hộp Xử lý buổi
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // B1: có ô action = form Xử lý buổi, không có = Công bố
        boolean handleForm = request.getParameter("action") != null;

        // B2: hết phiên đăng nhập → về đăng nhập / trả 401
        Integer accountId = getAccountId(request);
        if (accountId == null && handleForm) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }
        if (accountId == null) {
            JsonUtil.writeJson(response, 401, JsonResponse.error(Messages.MSG06_SESSION_EXPIRED));
            return;
        }

        // B3: không phải PT hoặc sai CSRF → báo không có quyền
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
            handleSession(
                    request,
                    response,
                    accountId,
                    action);
            return;
        }

        // B5: Công bố: lưu khung nháp + gỡ khung đã tích
        String[] newSlotDates = request.getParameterValues("slotDate");
        String[] newStartTimes = request.getParameterValues("startTime");
        String[] removeSlotIds = request.getParameterValues("removeSlotId");
        Integer duration = RequestUtil.getPositiveIntParam(request, "duration");
        ServiceResult<Integer> result = scheduleService.publish(
                accountId,
                newSlotDates,
                newStartTimes,
                removeSlotIds,
                duration == null ? 0 : duration,
                DateTimeUtil.now());

        // B6: trả JSON (thành công → toast ở lần tải trang sau)
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        }
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }

    /** HÀM PHỤ (doPost B3) — session đang ở vai trò PT VÀ csrfToken đúng. */
    private boolean isPtWithValidToken(HttpServletRequest request) {
        Object role = request.getSession(false).getAttribute(SessionKey.ROLE);
        boolean isPt = Role.PT.getDbValue().equals(role);
        return isPt && RequestUtil.isValidCsrfToken(request);
    }

    /**
     * HÀM PHỤ (doPost B4) — 3 form trong hộp Xử lý buổi: reschedule · transfer · cancel.
     *   Thành công → toast + redirect về tuần có buổi; bị từ chối → giữ lựa chọn + câu lỗi rồi showPage (hộp vẫn mở).
     */
    private void handleSession(
            HttpServletRequest request,
            HttpServletResponse response,
            int accountId,
            String action)
            throws IOException {
        // B1: thiếu mã buổi → lỗi 400
        Integer sessionId = RequestUtil.getPositiveIntParam(request, "sessionId");
        if (sessionId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // B2: gọi đúng nút: Dời buổi / Chuyển PT / Hủy buổi
        String slotId = request.getParameter("timeSlotId");
        String reason = request.getParameter("reason");
        ServiceResult<LocalDate> result = switch (action) {
            case "reschedule" -> handleService.reschedule(
                    accountId,
                    sessionId,
                    slotId,
                    request.getParameter("newSlotDate"),
                    request.getParameter("newStartTime"),
                    reason,
                    DateTimeUtil.now());
            case "transfer" -> handleService.transfer(
                    accountId,
                    sessionId,
                    slotId,
                    DateTimeUtil.now());
            case "cancel" -> handleService.cancel(
                    accountId,
                    sessionId,
                    reason,
                    request.getParameter("refundAgreed") != null,
                    DateTimeUtil.now());
            default -> null;
        };
        if (result == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // B3: thành công → báo + về đúng tuần, cuộn tới buổi
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            String view = "list".equals(request.getParameter("view")) ? "list" : "week";
            String anchor = "transfer".equals(action) ? "" : "#session-" + sessionId;
            RequestUtil.redirect(request, response, PAGE_URL + "?date=" + result.getData() + "&view=" + view + anchor);
            return;
        }

        // B4: bị từ chối → vẽ lại trang, hộp vẫn mở, giữ lựa chọn cũ
        request.setAttribute("handleFormError", result.getMessage());
        request.setAttribute("handleAction", action);
        request.setAttribute("handleReason", reason);
        request.setAttribute("handleSlotChoice", slotId);
        request.setAttribute("handleNewDate", request.getParameter("newSlotDate"));
        request.setAttribute("handleNewStart", request.getParameter("newStartTime"));
        try {
            showPage(
                    request,
                    response,
                    accountId,
                    sessionId);
        } catch (ServletException exception) {
            throw new IOException(exception);
        }
    }



    // ============================================================================================================
    // DÙNG CHUNG — đọc AccountId trong session (doGet, doPost)
    // ============================================================================================================

    /** HÀM PHỤ (dùng chung: doGet, doPost) — AccountId trong session; null = chưa đăng nhập. */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }
}
