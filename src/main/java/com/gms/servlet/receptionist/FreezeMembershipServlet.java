package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CustomerSearchItem;
import com.gms.dto.FreezeCustomerView;
import com.gms.dto.FreezeRecordItemView;
import com.gms.dto.FreezeSessionImpactView;
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
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Controller màn hình Bảo lưu gói tập của Lễ tân (Màn #34 - UC-18, Workflow BF-09).
 *
 * <p>Mapping URL: {@code @WebServlet("/receptionist/freeze")}</p>
 *
 * <p>Triển khai toàn diện theo nghiệp vụ SRS:
 * <ul>
 *   <li><b>Main Flow (Tạo bảo lưu):</b> Tra cứu khách hàng, kiểm tra điều kiện gói Membership,
 *       nhập kỳ bảo lưu và lý do, xem trước các buổi PT Scheduled bị chuyển sang Missed (CBR-62),
 *       chuyển gói và hồ sơ sang Frozen (BR-20).</li>
 *   <li><b>Alternative Flow (Hủy bảo lưu sớm):</b> Mở lại gói ngay lập tức (Frozen -> Active),
 *       cộng số ngày bảo lưu thực tế vào EndDate của gói (BR-26, DBR-51). Lượt bảo lưu vẫn bị tính (BR-24).</li>
 *   <li><b>Exception Flows:</b> Chặn bảo lưu gói PT / gói ngày (BR-21), gói hết hạn (BR-23),
 *       hết lượt bảo lưu (BR-24, MSG99), vượt quá thời lượng tối đa P-02 (MSG100), hoặc nhân viên
 *       tự thao tác trên hồ sơ của chính mình (CBR-115).</li>
 * </ul>
 * </p>
 *
 * <p>GET: Tra cứu hội viên, nạp thông tin gói tập hiện tại, xem trước buổi PT, xem danh sách bảo lưu gần đây.</p>
 * <p>POST: Tiếp nhận tạo phiếu bảo lưu mới hoặc hủy bảo lưu sớm theo mô hình PRG (Post/Redirect/Get).</p>
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

        // B1: Xử lý yêu cầu AJAX xem trước các buổi PT bị ảnh hưởng khi người dùng thay đổi ngày
        String action = RequestUtil.getTrimmedParam(request, "action");
        if (ACTION_PREVIEW_SESSIONS.equalsIgnoreCase(action)) {
            handleAjaxPreviewSessions(request, response);
            return;
        }

        // B2: Đọc từ khóa tìm kiếm (Mã KH KH-xxxx, SĐT hoặc Tên)
        String query = RequestUtil.getTrimmedParam(request, "q");
        int page = PaginationUtil.getPageNumber(request);
        int pageSize = 10;

        if (!ValidationUtil.isNullOrEmpty(query)) {
            // Tra cứu thông tin chi tiết hội viên và gói tập
            ServiceResult<FreezeCustomerView> lookupResult = freezeService.lookupCustomerForFreeze(query, accountId);

            if (lookupResult.isSuccess()) {
                request.setAttribute("freezeCustomer", lookupResult.getData());
            } else {
                // Nếu tìm chính xác mã/SĐT không thấy, thử tìm theo một phần tên có phân trang
                PageResult<CustomerSearchItem> searchResultsPage = freezeService.searchCustomersPage(query, page, pageSize);
                if (!searchResultsPage.isEmpty()) {
                    request.setAttribute("searchResultsPage", searchResultsPage);
                    request.setAttribute("searchResults", searchResultsPage.getItems());
                } else {
                    request.setAttribute("searchError", lookupResult.getMessage());
                }
            }
            request.setAttribute("query", query);
        }

        // B3: Nạp danh sách các đợt bảo lưu gần đây phục vụ đối soát và quản lý tại quầy
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
     * Xử lý POST tạo mới phiếu bảo lưu gói tập.
     */
    private void handleApplyFreeze(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int subscriptionId = RequestUtil.getIntParam(request, "subscriptionId", 0);
        String startDateStr = RequestUtil.getTrimmedParam(request, "startDate");
        String plannedEndDateStr = RequestUtil.getTrimmedParam(request, "plannedEndDate");
        String reason = RequestUtil.getTrimmedParam(request, "reason");
        String q = RequestUtil.getTrimmedParam(request, "q");

        String redirectUrl = "/receptionist/freeze" + (!ValidationUtil.isNullOrEmpty(q)
                ? "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                : "");

        if (subscriptionId <= 0 || ValidationUtil.isNullOrEmpty(startDateStr) || ValidationUtil.isNullOrEmpty(plannedEndDateStr)) {
            RequestUtil.setFlashToast(request, "error", "Vui lòng nhập đầy đủ thông tin thời gian bảo lưu.");
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

        ServiceResult<Void> result = freezeService.applyFreeze(
                accountId, subscriptionId, startDate, plannedEndDate, reason);

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        } else {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
        }

        RequestUtil.redirect(request, response, redirectUrl);
    }

    /**
     * Xử lý POST hủy bảo lưu sớm cho gói tập.
     */
    private void handleCancelFreezeEarly(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int freezeRecordId = RequestUtil.getIntParam(request, "freezeRecordId", 0);
        String q = RequestUtil.getTrimmedParam(request, "q");

        String redirectUrl = "/receptionist/freeze" + (!ValidationUtil.isNullOrEmpty(q)
                ? "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                : "");

        if (freezeRecordId <= 0) {
            RequestUtil.setFlashToast(request, "error", "Mã đợt bảo lưu không hợp lệ.");
            RequestUtil.redirect(request, response, redirectUrl);
            return;
        }

        ServiceResult<Void> result = freezeService.cancelFreezeEarly(accountId, freezeRecordId);

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        } else {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
        }

        RequestUtil.redirect(request, response, redirectUrl);
    }

    /**
     * Xử lý AJAX trả về danh sách các buổi PT bị ảnh hưởng dưới định dạng JSON.
     */
    private void handleAjaxPreviewSessions(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json;charset=UTF-8");

        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        String startDateStr = RequestUtil.getTrimmedParam(request, "startDate");
        String plannedEndDateStr = RequestUtil.getTrimmedParam(request, "plannedEndDate");

        if (customerId <= 0 || ValidationUtil.isNullOrEmpty(startDateStr) || ValidationUtil.isNullOrEmpty(plannedEndDateStr)) {
            response.getWriter().write("[]");
            return;
        }

        LocalDate startDate;
        LocalDate plannedEndDate;
        try {
            startDate = LocalDate.parse(startDateStr);
            plannedEndDate = LocalDate.parse(plannedEndDateStr);
        } catch (DateTimeParseException e) {
            response.getWriter().write("[]");
            return;
        }

        List<FreezeSessionImpactView> sessions = freezeService.previewAffectedSessions(customerId, startDate, plannedEndDate);

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < sessions.size(); i++) {
            FreezeSessionImpactView s = sessions.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"sessionId\":").append(s.getSessionId()).append(",");
            sb.append("\"slotDate\":\"").append(s.getSlotDate()).append("\",");
            sb.append("\"startTime\":\"").append(s.getStartTime()).append("\",");
            sb.append("\"endTime\":\"").append(s.getEndTime()).append("\",");
            sb.append("\"ptName\":\"").append(escapeJson(s.getPtName())).append("\",");
            sb.append("\"packageType\":\"").append(escapeJson(s.getPackageType())).append("\",");
            sb.append("\"partnerName\":").append(s.getPartnerName() != null ? "\"" + escapeJson(s.getPartnerName()) + "\"" : "null").append(",");
            sb.append("\"willBeMissed\":").append(s.isWillBeMissed()).append(",");
            sb.append("\"impactExplanation\":\"").append(escapeJson(s.getImpactExplanation())).append("\"");
            sb.append("}");
        }
        sb.append("]");

        response.getWriter().write(sb.toString());
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
