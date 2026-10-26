package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CustomerSearchItem;
import com.gms.dto.FreezeCustomerView;
import com.gms.dto.FreezeRecordItemView;
import com.gms.dto.FreezeSessionImpactView;
import com.gms.dto.JsonResponse;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.service.FreezeService;
import com.gms.util.JsonUtil;
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
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Controller điều phối nghiệp vụ Bảo lưu thẻ hội viên (Màn #34, UC-18, Workflow BF-09).
 *
 * <p>Triển khai theo mô hình MVC:
 * <ul>
 *   <li><b>GET:</b> Tra cứu hội viên (theo mã/SĐT/tên), hiển thị hồ sơ, gói tập, xem trước buổi PT qua AJAX.</li>
 *   <li><b>POST applyFreeze:</b> Tiếp nhận tạo phiếu bảo lưu mới theo mô hình PRG.</li>
 *   <li><b>POST cancelFreezeEarly:</b> Tiếp nhận mở lại thẻ trước hạn (hủy bảo lưu sớm).</li>
 * </ul>
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

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        // 1. Phục vụ AJAX xem trước danh sách buổi PT bị ảnh hưởng
        String action = RequestUtil.getTrimmedParam(request, "action");
        if (ACTION_PREVIEW_SESSIONS.equalsIgnoreCase(action)) {
            handleAjaxPreviewSessions(request, response);
            return;
        }

        // 2. Tra cứu hội viên theo từ khóa (Mã KH, Số điện thoại hoặc Họ tên)
        String query = RequestUtil.getTrimmedParam(request, "q");
        if (!ValidationUtil.isNullOrEmpty(query)) {
            ServiceResult<FreezeCustomerView> lookupResult = freezeService.lookupCustomerForFreeze(query, accountId);

            if (lookupResult.isSuccess()) {
                request.setAttribute("freezeCustomer", lookupResult.getData());
            } else {
                int page = PaginationUtil.getPageNumber(request);
                PageResult<CustomerSearchItem> searchResultsPage = freezeService.searchCustomersPage(query, page, 10);
                if (!searchResultsPage.isEmpty()) {
                    request.setAttribute("searchResultsPage", searchResultsPage);
                    request.setAttribute("searchResults", searchResultsPage.getItems());
                } else {
                    request.setAttribute("searchError", lookupResult.getMessage());
                }
            }
            request.setAttribute("query", query);
        }

        // 3. Tải danh sách các đợt bảo lưu gần đây của phòng tập
        List<FreezeRecordItemView> recentFreezes = freezeService.getRecentFreezes(20);
        request.setAttribute("recentFreezes", recentFreezes);
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

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action");

        if (ACTION_APPLY_FREEZE.equalsIgnoreCase(action)) {
            handleApplyFreeze(request, response, accountId);
            return;
        }

        if (ACTION_CANCEL_FREEZE_EARLY.equalsIgnoreCase(action)) {
            handleCancelFreezeEarly(request, response, accountId);
            return;
        }

        RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
        RequestUtil.redirect(request, response, "/receptionist/freeze");
    }

    /**
     * Xử lý POST tạo mới phiếu bảo lưu thẻ tập.
     */
    private void handleApplyFreeze(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int subscriptionId = RequestUtil.getIntParam(request, "subscriptionId", 0);
        String startDateStr = RequestUtil.getTrimmedParam(request, "startDate");
        String plannedEndDateStr = RequestUtil.getTrimmedParam(request, "plannedEndDate");
        String reason = RequestUtil.getTrimmedParam(request, "reason");
        String q = RequestUtil.getTrimmedParam(request, "q");

        String redirectUrl = "/receptionist/freeze" + (!ValidationUtil.isNullOrEmpty(q)
                ? "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8) : "");

        if (subscriptionId <= 0 || ValidationUtil.isNullOrEmpty(startDateStr) || ValidationUtil.isNullOrEmpty(plannedEndDateStr)) {
            RequestUtil.setFlashToast(request, "error", "Vui lòng chọn đầy đủ thời gian bảo lưu.");
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        if (ValidationUtil.isNullOrEmpty(reason) || reason.length() < 5) {
            RequestUtil.setFlashToast(request, "error", "Lý do bảo lưu phải có ít nhất 5 ký tự.");
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        LocalDate startDate;
        LocalDate plannedEndDate;
        try {
            startDate = LocalDate.parse(startDateStr);
            plannedEndDate = LocalDate.parse(plannedEndDateStr);
        } catch (DateTimeParseException e) {
            RequestUtil.setFlashToast(request, "error", "Định dạng ngày không hợp lệ.");
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        ServiceResult<Void> result = freezeService.applyFreeze(accountId, subscriptionId, startDate, plannedEndDate, reason);
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        RequestUtil.redirect(request, response, redirectUrl);
    }

    /**
     * Xử lý POST mở lại thẻ trước hạn (hủy bảo lưu sớm tại quầy).
     */
    private void handleCancelFreezeEarly(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int freezeRecordId = RequestUtil.getIntParam(request, "freezeRecordId", 0);
        String q = RequestUtil.getTrimmedParam(request, "q");

        String redirectUrl = "/receptionist/freeze" + (!ValidationUtil.isNullOrEmpty(q)
                ? "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8) : "");

        if (freezeRecordId <= 0) {
            RequestUtil.setFlashToast(request, "error", "Mã phiếu bảo lưu không hợp lệ.");
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        ServiceResult<Void> result = freezeService.cancelFreezeEarly(accountId, freezeRecordId);
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        RequestUtil.redirect(request, response, redirectUrl);
    }

    /**
     * AJAX xem trước danh sách buổi PT bị ảnh hưởng (dùng JsonUtil chuẩn hóa).
     */
    private void handleAjaxPreviewSessions(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        String startDateStr = RequestUtil.getTrimmedParam(request, "startDate");
        String plannedEndDateStr = RequestUtil.getTrimmedParam(request, "plannedEndDate");

        if (customerId <= 0 || ValidationUtil.isNullOrEmpty(startDateStr) || ValidationUtil.isNullOrEmpty(plannedEndDateStr)) {
            JsonUtil.writeJson(response, JsonResponse.success("OK", List.of()));
            return;
        }

        try {
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate plannedEndDate = LocalDate.parse(plannedEndDateStr);
            List<FreezeSessionImpactView> sessions = freezeService.previewAffectedSessions(customerId, startDate, plannedEndDate);
            JsonUtil.writeJson(response, JsonResponse.success("OK", sessions));
        } catch (DateTimeParseException e) {
            JsonUtil.writeJson(response, JsonResponse.success("OK", List.of()));
        }
    }
}
