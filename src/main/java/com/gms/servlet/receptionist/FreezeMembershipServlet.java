package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CustomerMembershipRow;
import com.gms.dto.FreezeCustomerView;
import com.gms.dto.FreezeRecordItemView;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.service.FreezeService;
import com.gms.util.PaginationUtil;
import com.gms.util.RequestUtil;
import com.gms.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Servlet màn hình Bảo lưu thẻ hội viên tại quầy Lễ tân (URL
 * /receptionist/freeze).
 *
 * <p>
 * GET: Tra cứu hội viên (mã/tên/SĐT), xem thông tin gói tập và xem danh sách
 * bảo lưu gần đây.
 * </p>
 * <p>
 * POST: Xác nhận bảo lưu gói tập hoặc mở lại thẻ trước hạn (hủy bảo lưu sớm).
 * </p>
 */
@WebServlet("/receptionist/freeze")
public class FreezeMembershipServlet extends HttpServlet {

    public static final String VIEW_NAME = "receptionist/freeze-membership";
    public static final String ACTION_APPLY_FREEZE = "applyFreeze";
    public static final String ACTION_CANCEL_FREEZE_EARLY = "cancelFreezeEarly";
    public static final String ACTION_PREVIEW_SESSIONS = "previewSessions";

    private final FreezeService freezeService;

    public FreezeMembershipServlet() {
        this(new FreezeService());
    }

    public FreezeMembershipServlet(FreezeService freezeService) {
        this.freezeService = freezeService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: Lấy tài khoản đăng nhập từ session để kiểm tra quyền
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }
        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        // B2: Xác định tab đang chọn (members hoặc history)
        String currentTab = RequestUtil.getTrimmedParam(request, "tab");
        String historyQuery = RequestUtil.getTrimmedParam(request, "hq");
        String historyFilter = RequestUtil.getTrimmedParam(request, "hfilter");
        if (ValidationUtil.isNullOrEmpty(historyFilter)) {
            historyFilter = "ALL";
        }
        if (ValidationUtil.isNullOrEmpty(currentTab)) {
            currentTab = (!ValidationUtil.isNullOrEmpty(historyQuery) || !"ALL".equalsIgnoreCase(historyFilter)) ? "history" : "members";
        }
        request.setAttribute("currentTab", currentTab);

        // Lấy từ khóa q (Tab 1) và bộ lọc filter
        String query = RequestUtil.getTrimmedParam(request, "q");
        String filter = RequestUtil.getTrimmedParam(request, "filter");
        if (ValidationUtil.isNullOrEmpty(filter)) {
            filter = "ALL";
        }
        int page = PaginationUtil.getPageNumber(request);
        int pageSize = 10;

        // B4: Lấy danh sách hội viên & hợp đồng hiển thị ở bảng (Tab 1)
        PageResult<CustomerMembershipRow> membershipsPage = freezeService.getCustomerMembershipsPage(query, filter, page, pageSize);
        request.setAttribute("membershipsPage", membershipsPage);
        request.setAttribute("customerMemberships", membershipsPage.getItems());

        if (!ValidationUtil.isNullOrEmpty(query)) {
            ServiceResult<FreezeCustomerView> lookupResult = freezeService.lookupCustomerForFreeze(query, accountId);
            if (lookupResult.isSuccess()) {
                request.setAttribute("freezeCustomer", lookupResult.getData());
            } else if (membershipsPage.isEmpty()) {
                request.setAttribute("searchError", lookupResult.getMessage());
            }
            request.setAttribute("query", query);
        }
        request.setAttribute("currentFilter", filter);

        // B5: Lấy toàn bộ lịch sử các đợt bảo lưu (Tab 2)
        List<FreezeRecordItemView> freezeHistoryList = freezeService.getRecentFreezes(historyQuery, historyFilter, 50);
        request.setAttribute("freezeHistoryList", freezeHistoryList);
        request.setAttribute("recentFreezes", freezeHistoryList); // tương thích ngược
        request.setAttribute("historyQuery", historyQuery);
        request.setAttribute("historyFilter", historyFilter);
        request.setAttribute("activeNav", "freeze");

        // B6: Chuyển tiếp dữ liệu từ servlet sang jsp để hiển thị giao diện
        RequestUtil.forward(request, response, VIEW_NAME);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Kiểm tra phiên đăng nhập của nhân viên Lễ tân
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // CSRF: token sai / thiếu → chặn (form: toast + quay lại; AJAX: JSON 403).
        if (RequestUtil.rejectInvalidCsrf(request, response, "/receptionist/freeze")) {
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action"); // Đọc action để biết người dùng muốn làm gì

        if (ACTION_APPLY_FREEZE.equalsIgnoreCase(action)) { // Nếu là xác nhận bảo lưu
            handleApplyFreeze(request, response, accountId);
            return;
        }

        if (ACTION_CANCEL_FREEZE_EARLY.equalsIgnoreCase(action)) { // Nếu là mở lại thẻ trước hạn
            handleCancelFreezeEarly(request, response, accountId);
            return;
        }

        // Trường hợp action không hợp lệ
        RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
        RequestUtil.redirect(request, response, "/receptionist/freeze");
    }

    /**
     * Xử lý xác nhận bảo lưu gói tập.
     */
    private void handleApplyFreeze(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int subscriptionId = RequestUtil.getIntParam(request, "subscriptionId", 0);
        String startDateStr = RequestUtil.getTrimmedParam(request, "startDate");
        String plannedEndDateStr = RequestUtil.getTrimmedParam(request, "plannedEndDate");
        String reason = RequestUtil.getTrimmedParam(request, "reason");
        String q = RequestUtil.getTrimmedParam(request, "q");
        String filter = RequestUtil.getTrimmedParam(request, "filter");
        String redirectUrl = buildRedirectUrl(q, filter);

        if (subscriptionId <= 0 || ValidationUtil.isNullOrEmpty(startDateStr)
                || ValidationUtil.isNullOrEmpty(plannedEndDateStr)) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG285_FREEZE_PERIOD_REQUIRED);
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        if (ValidationUtil.isNullOrEmpty(reason) || reason.length() < 5) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG278_FREEZE_REASON_MIN);
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        if (reason.length() > 500) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG286_FREEZE_REASON_MAX);
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        LocalDate startDate;
        LocalDate plannedEndDate;
        try {
            startDate = parseFlexibleDate(startDateStr);
            plannedEndDate = parseFlexibleDate(plannedEndDateStr);
        } catch (DateTimeParseException e) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG287_DATE_FORMAT_INVALID);
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        // Thực hiện bảo lưu thông qua service
        ServiceResult<Void> result = freezeService.applyFreeze(accountId, subscriptionId, startDate, plannedEndDate,
                reason);
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        RequestUtil.redirect(request, response, redirectUrl);
    }

    /**
     * Xử lý mở lại thẻ tập trước hạn (hủy bảo lưu sớm tại quầy).
     */
    private void handleCancelFreezeEarly(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int freezeRecordId = RequestUtil.getIntParam(request, "freezeRecordId", 0);
        String q = RequestUtil.getTrimmedParam(request, "q");
        String filter = RequestUtil.getTrimmedParam(request, "filter");
        String redirectUrl = buildRedirectUrl(q, filter);

        if (freezeRecordId <= 0) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG288_FREEZE_ID_INVALID);
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        // Thực hiện mở lại thẻ trước hạn thông qua service
        ServiceResult<Void> result = freezeService.cancelFreezeEarly(accountId, freezeRecordId);
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        RequestUtil.redirect(request, response, redirectUrl);
    }

    private LocalDate parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new DateTimeParseException("Date string is empty", "", 0);
        }
        dateStr = dateStr.trim();
        if (dateStr.contains("/")) {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("d/M/yyyy"));
        }
        return LocalDate.parse(dateStr);
    }

    private String buildRedirectUrl(String q, String filter) {
        StringBuilder sb = new StringBuilder("/receptionist/freeze");
        boolean hasParam = false;
        if (!ValidationUtil.isNullOrEmpty(q)) {
            sb.append("?q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
            hasParam = true;
        }
        if (!ValidationUtil.isNullOrEmpty(filter) && !"ALL".equalsIgnoreCase(filter)) {
            sb.append(hasParam ? "&" : "?").append("filter=").append(URLEncoder.encode(filter, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
