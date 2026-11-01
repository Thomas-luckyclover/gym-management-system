package com.gms.servlet.manager;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.AccountSettingsView;
import com.gms.dto.ClosedShiftHistoryItem;
import com.gms.dto.JsonResponse;
import com.gms.dto.ManagerOrderDetailView;
import com.gms.dto.ManagerTransactionItem;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.service.AccountSettingsService;
import com.gms.service.ManagerTransactionService;
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
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Controller màn hình Giao dịch toàn hệ thống cho Quản lý (Màn #41 - UC-26, SRS 7.4.2).
 *
 * <p>Các tính năng hỗ trợ:</p>
 * <ul>
 *   <li>Tab 1: Đơn hàng & Giao dịch - Tra cứu, lọc theo kênh/phương thức/trạng thái/ngày/hoàn tiền.</li>
 *   <li>Tab 2: Lịch sử ca làm việc đã đóng - Đối soát tiền mặt & chênh lệch ca.</li>
 *   <li>Chi tiết đơn hàng & lịch sử lần thanh toán (AJAX modal - ?action=detail).</li>
 *   <li>Xuất file Excel danh sách giao dịch (.xlsx - ?action=export).</li>
 *   <li>Ghi nhận xác nhận hoàn tiền offline (POST action=refund).</li>
 * </ul>
 *
 * @author Le Cong Minh
 */
@WebServlet("/manager/transactions")
public class SystemTransactionServlet extends HttpServlet {

    private static final String TAB_ORDERS = "orders";
    private static final String TAB_SHIFTS = "shifts";
    private static final String ACTION_EXPORT = "export";
    private static final String ACTION_DETAIL = "detail";
    private static final String ACTION_REFUND = "refund";

    private final ManagerTransactionService managerTransactionService = new ManagerTransactionService();
    private final AccountSettingsService accountSettingsService = new AccountSettingsService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = RequestUtil.getTrimmedParam(request, "action");

        // B1: Xử lý xuất Excel (.xlsx) nếu action=export
        if (ACTION_EXPORT.equalsIgnoreCase(action)) {
            handleExportExcel(request, response);
            return;
        }

        // B2: Xử lý xem chi tiết đơn hàng dạng JSON cho modal AJAX
        if (ACTION_DETAIL.equalsIgnoreCase(action)) {
            handleOrderDetailAjax(request, response);
            return;
        }

        // B3: Hiển thị giao diện danh sách theo Tab
        renderTransactionPage(request, response);
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
        if (RequestUtil.rejectInvalidCsrf(request, response, "/manager/transactions")) {
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action");

        // B1: Xử lý xác nhận hoàn tiền offline (BR-47, CBR-72)
        if (ACTION_REFUND.equalsIgnoreCase(action)) {
            handleOfflineRefund(request, response, accountId);
            return;
        }

        // Action không hợp lệ -> redirect về trang giao dịch
        RequestUtil.redirect(request, response, "/manager/transactions");
    }

    /**
     * Dựng giao diện trang Giao dịch với 2 Tabs: Đơn hàng & Lịch sử ca.
     */
    private void renderTransactionPage(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        Integer accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        // B1: Lấy thông tin tài khoản cho topbar header
        ServiceResult<AccountSettingsView> me = accountSettingsService.getAccountSettings(accountId, Role.MANAGER);
        if (me.isSuccess()) {
            request.setAttribute("accountSettings", me.getData());
            request.setAttribute("currentRole", Role.MANAGER);
        }

        // B2: Đếm số lượng giao dịch cần hoàn tiền hiển thị badge nổi bật
        int pendingRefundCount = managerTransactionService.countPendingRefunds();
        request.setAttribute("pendingRefundCount", pendingRefundCount);

        // B3: Xác định tab hiện tại (mặc định tab 'orders')
        String tab = RequestUtil.getTrimmedParam(request, "tab");
        if (!TAB_SHIFTS.equalsIgnoreCase(tab)) {
            tab = TAB_ORDERS;
        }
        request.setAttribute("currentTab", tab);

        // B4: Tải dữ liệu tương ứng theo tab
        if (TAB_SHIFTS.equals(tab)) {
            renderClosedShiftsTab(request);
        } else {
            renderOrdersTab(request);
        }

        // B5: Thiết lập layout và forward về JSP
        request.setAttribute("activeNav", "transactions");
        request.setAttribute("themeClass", "theme-manager");
        RequestUtil.forward(request, response, "manager/transaction-list");
    }

    /**
     * Tải dữ liệu phân trang cho Tab 1: Đơn hàng & Giao dịch.
     */
    private void renderOrdersTab(HttpServletRequest request) {
        String keyword = RequestUtil.getTrimmedParam(request, "search");
        String paymentMethod = RequestUtil.getTrimmedParam(request, "method");
        String orderStatus = RequestUtil.getTrimmedParam(request, "status");

        String refundParam = RequestUtil.getTrimmedParam(request, "needsRefund");
        Boolean needsRefundOnly = "1".equals(refundParam) ? Boolean.TRUE : null;

        LocalDate from = DateTimeUtil.parseHtmlDate(request.getParameter("from"));
        LocalDate to = DateTimeUtil.parseHtmlDate(request.getParameter("to"));
        int page = RequestUtil.getIntParam(request, "page", 1);
        int pageSize = RequestUtil.getIntParam(request, "pageSize", 10);

        ServiceResult<PageResult<ManagerTransactionItem>> result = managerTransactionService.getTransactionPage(
                keyword, paymentMethod, orderStatus, needsRefundOnly, from, to, page, pageSize);

        if (result.isSuccess()) {
            request.setAttribute("orderPage", result.getData());
        } else {
            request.setAttribute("orderPage", new PageResult<ManagerTransactionItem>(java.util.Collections.emptyList(), page, pageSize, 0));
            request.setAttribute("pageError", result.getMessage());
        }

        request.setAttribute("searchKeyword", keyword);
        request.setAttribute("filterMethod", paymentMethod);
        request.setAttribute("filterStatus", orderStatus);
        request.setAttribute("filterNeedsRefund", "1".equals(refundParam) ? 1 : null);
        request.setAttribute("fromDate", from);
        request.setAttribute("toDate", to);
    }

    /**
     * Tải dữ liệu phân trang cho Tab 2: Lịch sử ca làm việc đã đóng.
     */
    private void renderClosedShiftsTab(HttpServletRequest request) {
        LocalDate from = DateTimeUtil.parseHtmlDate(request.getParameter("from"));
        LocalDate to = DateTimeUtil.parseHtmlDate(request.getParameter("to"));
        int page = RequestUtil.getIntParam(request, "page", 1);
        int pageSize = RequestUtil.getIntParam(request, "pageSize", 10);

        ServiceResult<PageResult<ClosedShiftHistoryItem>> shiftResult = managerTransactionService.getClosedShiftsPage(
                from, to, page, pageSize);

        if (shiftResult.isSuccess()) {
            request.setAttribute("shiftPage", shiftResult.getData());
        } else {
            request.setAttribute("shiftPage", new PageResult<ClosedShiftHistoryItem>(java.util.Collections.emptyList(), page, pageSize, 0));
            request.setAttribute("pageError", shiftResult.getMessage());
        }

        request.setAttribute("shiftFromDate", from);
        request.setAttribute("shiftToDate", to);
    }

    /**
     * Xử lý AJAX lấy thông tin chi tiết đơn hàng và các lần thanh toán.
     */
    private void handleOrderDetailAjax(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int orderId = RequestUtil.getIntParam(request, "orderId", 0);
        if (orderId <= 0) {
            JsonUtil.writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    JsonResponse.error(Messages.MSG170_ORDER_CODE_INVALID_FULL));
            return;
        }

        ServiceResult<ManagerOrderDetailView> result = managerTransactionService.getOrderDetail(orderId);
        if (result.isSuccess()) {
            JsonUtil.writeJson(response, HttpServletResponse.SC_OK, JsonResponse.success(result.getData()));
        } else {
            JsonUtil.writeJson(response, HttpServletResponse.SC_NOT_FOUND,
                    JsonResponse.error(result.getMessage()));
        }
    }

    /**
     * Xử lý ghi nhận hoàn tiền ngoài hệ thống (BR-47, CBR-72).
     */
    private void handleOfflineRefund(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int transactionId = RequestUtil.getIntParam(request, "transactionId", 0);
        String refundNote = RequestUtil.getTrimmedParam(request, "refundNote");

        ServiceResult<Void> result = managerTransactionService.recordRefund(accountId, transactionId, refundNote);

        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
        if (isAjax) {
            if (result.isSuccess()) {
                JsonUtil.writeJson(response, HttpServletResponse.SC_OK,
                        JsonResponse.success("Đã ghi nhận hoàn tiền thành công."));
            } else {
                JsonUtil.writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        JsonResponse.error(result.getMessage()));
            }
            return;
        }

        // Xử lý submit form thông thường (Fallback)
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", Messages.MSG174_REFUND_CONFIRMED);
        } else {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
        }
        RequestUtil.redirect(request, response, "/manager/transactions");
    }

    /**
     * Xử lý xuất danh sách giao dịch ra file Excel (.xlsx).
     */
    private void handleExportExcel(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String keyword = RequestUtil.getTrimmedParam(request, "search");
        String paymentMethod = RequestUtil.getTrimmedParam(request, "method");
        String orderStatus = RequestUtil.getTrimmedParam(request, "status");

        String refundParam = RequestUtil.getTrimmedParam(request, "needsRefund");
        Boolean needsRefundOnly = "1".equals(refundParam) ? Boolean.TRUE : null;

        LocalDate from = DateTimeUtil.parseHtmlDate(request.getParameter("from"));
        LocalDate to = DateTimeUtil.parseHtmlDate(request.getParameter("to"));

        // Định dạng tên file: Danh_sach_giao_dich_yyyyMMdd_HHmmss.xlsx
        String timeStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(DateTimeUtil.now());
        String fileName = "Danh_sach_giao_dich_" + timeStamp + ".xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        try (OutputStream out = response.getOutputStream()) {
            managerTransactionService.exportToExcel(keyword, paymentMethod, orderStatus, needsRefundOnly, from, to, out);
            out.flush();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi truy vấn cơ sở dữ liệu khi xuất Excel.");
        }
    }
}
