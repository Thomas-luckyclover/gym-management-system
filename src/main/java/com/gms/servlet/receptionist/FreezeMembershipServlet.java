package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CustomerMembershipRow;
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

        // B2: Nếu là yêu cầu AJAX xem trước buổi PT bị ảnh hưởng thì trả JSON về luôn
        String action = RequestUtil.getTrimmedParam(request, "action");
        if (ACTION_PREVIEW_SESSIONS.equalsIgnoreCase(action)) {
            handleAjaxPreviewSessions(request, response);
            return;
        }

        // B3: Lấy từ khóa q và bộ lọc filter người dùng chọn
        String query = RequestUtil.getTrimmedParam(request, "q");
        String filter = RequestUtil.getTrimmedParam(request, "filter");
        if (ValidationUtil.isNullOrEmpty(filter)) {
            filter = "ALL";
        }
        int page = PaginationUtil.getPageNumber(request); // Lấy số trang hiện tại phục vụ phân trang
        int pageSize = 10; // Phân trang 10 khách mỗi trang để vừa vặn giao diện

        if (!ValidationUtil.isNullOrEmpty(query)) { // Nếu từ khóa tìm kiếm không rỗng
            // Gọi hàm lookupCustomerForFreeze để tra cứu khách hàng và gói tập
            ServiceResult<FreezeCustomerView> lookupResult = freezeService.lookupCustomerForFreeze(query, accountId);

            if (lookupResult.isSuccess()) { // Nếu tìm thấy chính xác theo mã hoặc SĐT
                request.setAttribute("freezeCustomer", lookupResult.getData());
            } else {
                // Nếu tìm chính xác không ra, tìm theo một phần tên có phân trang
                PageResult<CustomerSearchItem> searchResultsPage = freezeService.searchCustomersPage(query, page,
                        pageSize);
                if (!searchResultsPage.isEmpty()) {
                    request.setAttribute("searchResultsPage", searchResultsPage);
                    request.setAttribute("searchResults", searchResultsPage.getItems());
                } else {
                    request.setAttribute("searchError", lookupResult.getMessage());
                }
            }
            request.setAttribute("query", query); // Gửi lại từ khóa ra JSP
        }
        request.setAttribute("currentFilter", filter);

        // B4: Lấy danh sách hội viên & hợp đồng hiển thị ở bảng phong cách GymMaster (có hỗ trợ lọc theo trạng thái)
        PageResult<CustomerMembershipRow> membershipsPage = freezeService.getCustomerMembershipsPage(query, filter, page, pageSize);
        request.setAttribute("membershipsPage", membershipsPage);
        request.setAttribute("customerMemberships", membershipsPage.getItems());

        // B5: Lấy toàn bộ lịch sử các đợt bảo lưu hiển thị ở tab "Lịch sử bảo lưu"
        List<FreezeRecordItemView> freezeHistoryList = freezeService.getRecentFreezes(50);
        request.setAttribute("freezeHistoryList", freezeHistoryList);
        request.setAttribute("recentFreezes", freezeHistoryList); // tương thích ngược
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
            RequestUtil.setFlashToast(request, "error", "Vui lòng chọn đầy đủ thời gian bảo lưu.");
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        if (ValidationUtil.isNullOrEmpty(reason) || reason.length() < 5) {
            RequestUtil.setFlashToast(request, "error", "Lý do bảo lưu phải có ít nhất 5 ký tự.");
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        if (reason.length() > 500) {
            RequestUtil.setFlashToast(request, "error", "Lý do bảo lưu không được vượt quá 500 ký tự.");
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
            RequestUtil.setFlashToast(request, "error", "Mã đợt bảo lưu không hợp lệ.");
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        // Thực hiện mở lại thẻ trước hạn thông qua service
        ServiceResult<Void> result = freezeService.cancelFreezeEarly(accountId, freezeRecordId);
        RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
        RequestUtil.redirect(request, response, redirectUrl);
    }

    /**
     * AJAX xem trước danh sách buổi PT bị ảnh hưởng (dùng JsonUtil).
     */
    private void handleAjaxPreviewSessions(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        String startDateStr = RequestUtil.getTrimmedParam(request, "startDate");
        String plannedEndDateStr = RequestUtil.getTrimmedParam(request, "plannedEndDate");

        if (customerId <= 0 || ValidationUtil.isNullOrEmpty(startDateStr)
                || ValidationUtil.isNullOrEmpty(plannedEndDateStr)) {
            JsonUtil.writeJson(response, JsonResponse.success(List.of()));
            return;
        }

        try {
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate plannedEndDate = LocalDate.parse(plannedEndDateStr);
            List<FreezeSessionImpactView> sessions = freezeService.previewAffectedSessions(customerId, startDate,
                    plannedEndDate);
            JsonUtil.writeJson(response, JsonResponse.success(sessions));
        } catch (DateTimeParseException e) {
            JsonUtil.writeJson(response, JsonResponse.success(List.of()));
        }
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
