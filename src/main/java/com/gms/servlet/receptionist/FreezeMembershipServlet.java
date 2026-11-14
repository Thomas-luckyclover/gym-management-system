package com.gms.servlet.receptionist;

import com.gms.constant.AppConstants;
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
 * Controller màn hình Bảo lưu thẻ hội viên tại quầy Lễ tân (Màn #34 - UC-18, Business Flow BF-09).
 *
 * <p>Mapping URL: {@code @WebServlet("/receptionist/freeze")}</p>
 *
 * <p>GET: Nạp danh sách hội viên có phân trang/bộ lọc, chi tiết hội viên tra cứu và lịch sử bảo lưu.</p>
 * <p>POST: Xử lý tạo lượt bảo lưu mới ({@code applyFreeze}) hoặc mở lại thẻ trước hạn ({@code cancelFreezeEarly}) theo mô hình PRG.</p>
 */
@WebServlet("/receptionist/freeze")
public class FreezeMembershipServlet extends HttpServlet {

    public static final String VIEW_NAME = "receptionist/freeze-membership";

    public static final String ACTION_APPLY_FREEZE = "applyFreeze";
    public static final String ACTION_CANCEL_FREEZE_EARLY = "cancelFreezeEarly";

    private static final int HISTORY_LIMIT = 50;
    private static final DateTimeFormatter FLEXIBLE_DMY = DateTimeFormatter.ofPattern("d/M/yyyy");

    /**
     * Tiện ích chuẩn hóa và parse ngày linh hoạt từ input người dùng (hỗ trợ {@code d/M/yyyy} hoặc {@code yyyy-MM-dd}).
     *
     * @param dateStr chuỗi ngày đầu vào
     * @return {@link LocalDate} đã parse
     */
    public static LocalDate parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new DateTimeParseException("Date string is empty", "", 0);
        }
        String trimmed = dateStr.trim();
        if (trimmed.contains("/")) {
            return LocalDate.parse(trimmed, FLEXIBLE_DMY);
        }
        return LocalDate.parse(trimmed);
    }

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

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }
        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        // B1: Đọc các tham số tab, bộ lọc và phân trang từ request
        String currentTab = RequestUtil.getTrimmedParam(request, "tab");
        String query = RequestUtil.getTrimmedParam(request, "q");
        String filter = defaultIfEmpty(RequestUtil.getTrimmedParam(request, "filter"), "ALL");
        String historyQuery = RequestUtil.getTrimmedParam(request, "hq");
        String historyFilter = defaultIfEmpty(RequestUtil.getTrimmedParam(request, "hfilter"), "ALL");
        //mới vào trang tất cả giá trị trên null ngoại trừ filter = ALL, historyFilter = ALL
        if (ValidationUtil.isNullOrEmpty(currentTab)) {
            boolean hasHistoryFilter = !ValidationUtil.isNullOrEmpty(historyQuery) || !"ALL".equalsIgnoreCase(historyFilter);
            currentTab = hasHistoryFilter ? "history" : "members";
        }

        int page = PaginationUtil.getPageNumber(request);
        int pageSize = AppConstants.DEFAULT_PAGE_SIZE;

        // B2: Gọi Service để nạp dữ liệu bảng hội viên (Tab 1), chi tiết hội viên (nếu tra cứu) và lịch sử (Tab 2)
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

        List<FreezeRecordItemView> freezeHistoryList = freezeService.getRecentFreezes(historyQuery, historyFilter, HISTORY_LIMIT);

        request.setAttribute("currentTab", currentTab);
        request.setAttribute("currentFilter", filter);
        request.setAttribute("freezeHistoryList", freezeHistoryList);
        request.setAttribute("historyQuery", historyQuery);
        request.setAttribute("historyFilter", historyFilter);
        request.setAttribute("activeNav", "freeze");

        RequestUtil.forward(request, response, VIEW_NAME);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

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
        String action = RequestUtil.getTrimmedParam(request, "action");

        switch (action != null ? action : "") {
            case ACTION_APPLY_FREEZE:
                handleApplyFreeze(request, response, accountId);
                return;
            case ACTION_CANCEL_FREEZE_EARLY:
                handleCancelFreezeEarly(request, response, accountId);
                return;
            default:
                finishAction(request, response, "/receptionist/freeze", "error", Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Xử lý xác nhận bảo lưu gói tập.
     */
    private void handleApplyFreeze(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        String redirectUrl = buildRedirectUrl(request);
        int subscriptionId = RequestUtil.getIntParam(request, "subscriptionId", 0);
        String startDateStr = RequestUtil.getTrimmedParam(request, "startDate");
        String plannedEndDateStr = RequestUtil.getTrimmedParam(request, "plannedEndDate");
        String reason = RequestUtil.getTrimmedParam(request, "reason");

        if (subscriptionId <= 0 || ValidationUtil.isNullOrEmpty(startDateStr)
                || ValidationUtil.isNullOrEmpty(plannedEndDateStr)) {
            finishAction(request, response, redirectUrl, "error", Messages.MSG285_FREEZE_PERIOD_REQUIRED);
            return;
        }

        if (reason != null && reason.length() > 500) {
            finishAction(request, response, redirectUrl, "error", Messages.MSG286_FREEZE_REASON_MAX);
            return;
        }

        LocalDate startDate;
        LocalDate plannedEndDate;
        try {
            startDate = parseFlexibleDate(startDateStr);
            plannedEndDate = parseFlexibleDate(plannedEndDateStr);
        } catch (DateTimeParseException e) {
            finishAction(request, response, redirectUrl, "error", Messages.MSG287_DATE_FORMAT_INVALID);
            return;
        }

        ServiceResult<Void> result = freezeService.applyFreeze(accountId, subscriptionId, startDate, plannedEndDate, reason);
        finishAction(request, response, redirectUrl, result);
    }

    /**
     * Xử lý mở lại thẻ tập trước hạn (hủy bảo lưu sớm tại quầy).
     */
    private void handleCancelFreezeEarly(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        String redirectUrl = buildRedirectUrl(request);
        int freezeRecordId = RequestUtil.getIntParam(request, "freezeRecordId", 0);

        if (freezeRecordId <= 0) {
            finishAction(request, response, redirectUrl, "error", Messages.MSG288_FREEZE_ID_INVALID);
            return;
        }

        ServiceResult<Void> result = freezeService.cancelFreezeEarly(accountId, freezeRecordId);
        finishAction(request, response, redirectUrl, result);
    }

    private void finishAction(HttpServletRequest request, HttpServletResponse response,
                              String redirectUrl, ServiceResult<Void> result) throws IOException {
        finishAction(request, response, redirectUrl,
                result.isSuccess() ? "success" : "error",
                result.getMessage());
    }

    private void finishAction(HttpServletRequest request, HttpServletResponse response,
                              String redirectUrl, String toastType, String message) throws IOException {
        RequestUtil.setFlashToast(request, toastType, message);
        RequestUtil.redirect(request, response, redirectUrl);
    }

    private String defaultIfEmpty(String value, String fallback) {
        return ValidationUtil.isNullOrEmpty(value) ? fallback : value;
    }

    private String buildRedirectUrl(HttpServletRequest request) {
        String q = RequestUtil.getTrimmedParam(request, "q");
        String filter = RequestUtil.getTrimmedParam(request, "filter");
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
