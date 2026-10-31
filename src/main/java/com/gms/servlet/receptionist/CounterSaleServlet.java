package com.gms.servlet.receptionist;

import com.gms.constant.SessionKey;
import com.gms.dto.CounterCheckoutInput;
import com.gms.dto.CounterCustomerCreateInput;
import com.gms.dto.CounterCustomerRow;
import com.gms.dto.CounterPackageCard;
import com.gms.dto.CounterPackageView;
import com.gms.dto.CounterSaleReceipt;
import com.gms.dto.CounterVoucherResult;
import com.gms.dto.JsonResponse;
import com.gms.dto.ServiceResult;
import com.gms.service.CounterSaleService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SERVLET MÀN LỄ TÂN BÁN GÓI TẠI QUẦY — /receptionist/sales (SRS #37).
 *
 * ReceptionistAreaFilter kiểm quyền Lễ tân.
 * Áp dụng mô hình PRG (Post/Redirect/Get) sau khi thu tiền để chống submit trùng lặp.
 *
 * GET /receptionist/sales                 Chọn gói → counter-sale-packages.jsp
 * GET /receptionist/sales?package=3       Chọn khách → counter-sale-customers.jsp
 * GET /receptionist/sales/customers?q=mai AJAX tìm khách → JSON tối đa 20 khách
 * GET /receptionist/sales/voucher?package=3&customer=12&code=WELCOME10 AJAX kiểm tra voucher → JSON
 * GET /receptionist/sales?package=3&customer=12 Xác nhận & Thu tiền → counter-sale-next.jsp
 * GET /receptionist/sales?receipt=101     In/xem biên lai thu tiền → counter-sale-receipt.jsp
 * POST /receptionist/sales                Xác nhận thu tiền tại quầy (tiền mặt / chuyển khoản)
 */
@WebServlet(urlPatterns = {"/receptionist/sales", "/receptionist/sales/customers", "/receptionist/sales/voucher"})
public class CounterSaleServlet extends HttpServlet {

    private static final String PAGE_URL = "/receptionist/sales";
    private static final String VIEW_PACKAGES = "receptionist/counter-sale-packages";
    private static final String VIEW_CUSTOMERS = "receptionist/counter-sale-customers";
    private static final String VIEW_NEXT = "receptionist/counter-sale-next";
    private static final String VIEW_RECEIPT = "receptionist/counter-sale-receipt";

    private final CounterSaleService counterSaleService = new CounterSaleService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chưa đăng nhập thì chuyển sang trang đăng nhập, xong quay lại đúng màn này.
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }
        request.setAttribute("activeNav", "sales");

        // B2: URL /receptionist/sales/customers là ô tìm khách gọi AJAX, trả JSON rồi dừng.
        boolean isCustomerSearch = request.getServletPath().endsWith("/customers");
        if (isCustomerSearch) {
            writeCustomers(response, accountId, RequestUtil.getTrimmedParam(request, "q"));
            return;
        }

        // B2.1: URL /receptionist/sales/voucher là ô kiểm tra mã giảm giá gọi AJAX, trả JSON rồi dừng.
        boolean isVoucherCheck = request.getServletPath().endsWith("/voucher");
        if (isVoucherCheck) {
            writeVoucherCheck(request, response, accountId);
            return;
        }

        // B3: xem biên lai thu tiền vừa tạo ?receipt=...
        Integer receiptOrderId = RequestUtil.getPositiveIntParam(request, "receipt");
        if (receiptOrderId != null) {
            showReceipt(request, response);
            return;
        }

        // B4: chưa có ?package=… thì hiện màn Chọn gói.
        Integer packageId = RequestUtil.getPositiveIntParam(request, "package");
        if (packageId == null) {
            showPackages(request, response, accountId);
            return;
        }

        // B5: có gói, kiểm tra gói còn bán rồi tải thẻ gói (cột Tóm tắt đơn).
        ServiceResult<CounterPackageCard> cardResult = counterSaleService.getPackageCard(accountId, packageId);
        if (!cardResult.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", cardResult.getMessage());
            RequestUtil.redirect(request, response, PAGE_URL);
            return;
        }
        CounterPackageCard selectedCard = cardResult.getData();
        request.setAttribute("selectedCard", selectedCard);

        // B6: chưa có ?customer=… thì hiện màn Chọn khách.
        Integer customerId = RequestUtil.getPositiveIntParam(request, "customer");
        if (customerId == null) {
            showCustomers(request, response);
            return;
        }

        // B7: đã chọn khách, sang bước tiếp theo: Xác nhận & Thu tiền.
        showNext(request, response, accountId, customerId, packageId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: kiểm tra đăng nhập và mã CSRF token.
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }
        boolean isValidCsrf = RequestUtil.isValidCsrfToken(request);
        if (!isValidCsrf) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String pathInfo = request.getServletPath();
        String action = RequestUtil.getTrimmedParam(request, "action");

        // Xử lý tạo nhanh hồ sơ khách hàng tại Step 2
        if ("/receptionist/sales/customers".equals(pathInfo) || "quick-create-customer".equals(action)) {
            handleQuickCreateCustomer(request, response, accountId);
            return;
        }

        // B2: đọc và parse các tham số gửi lên từ form.
        Integer packageId = RequestUtil.getPositiveIntParam(request, "packageId");
        Integer customerId = RequestUtil.getPositiveIntParam(request, "customerId");
        String paymentMethod = RequestUtil.getTrimmedParam(request, "paymentMethod");
        String startDateMode = RequestUtil.getTrimmedParam(request, "startDateMode");
        String customStartDateStr = RequestUtil.getTrimmedParam(request, "customStartDate");
        String voucherCode = RequestUtil.getTrimmedParam(request, "voucher");

        boolean noPackage = packageId == null;
        boolean noCustomer = customerId == null;
        if (noPackage || noCustomer) {
            RequestUtil.setFlashToast(request, "error", "Dữ liệu đơn hàng không hợp lệ.");
            RequestUtil.redirect(request, response, PAGE_URL);
            return;
        }

        LocalDate startDate = null;
        boolean isCustomMode = "custom".equals(startDateMode);
        boolean hasDateParam = customStartDateStr != null;
        boolean isNotEmptyDate = hasDateParam && !customStartDateStr.isBlank();
        if (isCustomMode && isNotEmptyDate) {
            startDate = DateTimeUtil.parseHtmlDate(customStartDateStr);
        }

        CounterCheckoutInput input = new CounterCheckoutInput(
                packageId,
                customerId,
                startDate,
                paymentMethod,
                voucherCode
        );

        String receptionistName = "Lễ tân quầy";
        HttpSession session = request.getSession(false);
        boolean hasSession = session != null;
        if (hasSession) {
            Object nameObj = session.getAttribute(SessionKey.DISPLAY_NAME);
            if (nameObj instanceof String) {
                receptionistName = (String) nameObj;
            }
        }

        // B3: gọi CounterSaleService.checkout xử lý nghiệp vụ trong transaction.
        ServiceResult<CounterSaleReceipt> result = counterSaleService.checkout(
                accountId, receptionistName, input, DateTimeUtil.now());

        // B4: mô hình PRG: thành công chuyển sang màn biên lai; thất bại quay lại bước trước kèm thông báo.
        if (result.isSuccess()) {
            CounterSaleReceipt receipt = result.getData();
            if (hasSession) {
                session.setAttribute("lastReceipt", receipt);
            }
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            RequestUtil.redirect(request, response, PAGE_URL + "?receipt=" + receipt.getOrderId());
        } else {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, PAGE_URL + "?package=" + packageId + "&customer=" + customerId);
        }
    }

    /**
     * HÀM PHỤ — màn Chọn gói: Service dựng 3 tab thẻ gói rồi forward JSP.
     * @param request HTTP request
     * @param response HTTP response
     * @param accountId mã tài khoản lễ tân từ session
     */
    private void showPackages(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws ServletException, IOException {
        ServiceResult<CounterPackageView> result = counterSaleService.getPackageView(accountId);
        if (result.isSuccess()) {
            request.setAttribute("packageView", result.getData());
        } else {
            request.setAttribute("pageError", result.getMessage());
        }
        RequestUtil.forward(request, response, VIEW_PACKAGES);
    }

    /**
     * HÀM PHỤ — màn Chọn khách: chỉ vẽ khung, bảng khách do counter-sale.js đổ vào sau mỗi lần gõ.
     * @param request HTTP request
     * @param response HTTP response
     */
    private void showCustomers(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        RequestUtil.forward(request, response, VIEW_CUSTOMERS);
    }

    /**
     * HÀM PHỤ — JSON cho ô tìm khách.
     * @param response HTTP response
     * @param accountId mã tài khoản lễ tân
     * @param keyword từ khóa tìm kiếm
     */
    private void writeCustomers(HttpServletResponse response, int accountId, String keyword) throws IOException {
        ServiceResult<List<CounterCustomerRow>> result = counterSaleService.searchCustomers(accountId, keyword);
        if (!result.isSuccess()) {
            JsonUtil.writeJson(response, JsonResponse.error(result.getMessage()));
            return;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CounterCustomerRow customer : result.getData()) {
            String currentPackageName = "";
            if (customer.getCurrentPackageName() != null) {
                currentPackageName = customer.getCurrentPackageName();
            }
            String currentPackageEndText = customer.getCurrentPackageEndText();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerId", customer.getCustomerId());
            row.put("customerCode", customer.getCustomerCode());
            row.put("fullName", customer.getFullName());
            row.put("phoneText", customer.getPhoneText());
            row.put("isDependent", customer.isDependent());
            row.put("hasAccount", customer.isHasAccount());
            row.put("isOwnProfile", customer.isOwnProfile());
            String statusText = "";
            if (customer.getStatus() != null) {
                statusText = customer.getStatus().getDisplayName();
            }
            row.put("statusText", statusText);
            row.put("statusBadgeClass", customer.getStatusBadgeClass());
            row.put("currentPackageName", currentPackageName);
            row.put("currentPackageEndText", currentPackageEndText);
            rows.add(row);
        }
        JsonUtil.writeJson(response, JsonResponse.success(rows));
    }

    /**
     * HÀM PHỤ — kiểm tra mã giảm giá và trả JSON cho JavaScript cập nhật realtime bảng tính tiền.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param accountId mã tài khoản lễ tân từ session
     */
    private void writeVoucherCheck(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {
        Integer packageId = RequestUtil.getPositiveIntParam(request, "package");
        Integer customerId = RequestUtil.getPositiveIntParam(request, "customer");
        String code = RequestUtil.getTrimmedParam(request, "code");
        if (code == null || code.isEmpty()) {
            code = RequestUtil.getTrimmedParam(request, "voucher");
        }

        if (packageId == null || customerId == null) {
            JsonUtil.writeJson(response, JsonResponse.error("Thiếu thông tin gói hoặc khách hàng."));
            return;
        }

        ServiceResult<CounterVoucherResult> result = counterSaleService.checkVoucher(accountId, packageId, customerId, code);
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }

    /**
     * HÀM PHỤ — bước sau Chọn khách: Xác nhận thông tin & Thu tiền.
     * Tính toán sẵn ngày kích hoạt (hôm nay hoặc nối tiếp) và ngày hết hạn dự kiến.
     * @param request HTTP request
     * @param response HTTP response
     * @param accountId mã tài khoản lễ tân
     * @param customerId mã khách hàng đã chọn
     * @param packageId mã gói tập đã chọn
     */
    private void showNext(HttpServletRequest request, HttpServletResponse response, int accountId,
            int customerId, int packageId) throws ServletException, IOException {
        ServiceResult<CounterCustomerRow> result = counterSaleService.getCustomer(accountId, customerId);
        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, PAGE_URL + "?package=" + packageId);
            return;
        }
        CounterCustomerRow customer = result.getData();
        request.setAttribute("selectedCustomer", customer);

        // Tính ngày bắt đầu và ngày hết hạn dự kiến theo gói & khách
        CounterPackageCard card = (CounterPackageCard) request.getAttribute("selectedCard");
        LocalDate today = DateTimeUtil.today();

        boolean hasPackageEnd = customer.getCurrentPackageEnd() != null;
        boolean isNotExpired = hasPackageEnd && !customer.getCurrentPackageEnd().isBefore(today);
        boolean isChained = isNotExpired;

        LocalDate startDate;
        if (isChained) {
            startDate = customer.getCurrentPackageEnd().plusDays(1);
        } else {
            startDate = today;
        }

        LocalDate endDate;
        boolean hasDuration = card != null && card.getDurationMonths() > 0;
        if (hasDuration) {
            endDate = DateTimeUtil.calculateEndDateByMonths(startDate, card.getDurationMonths());
        } else {
            endDate = startDate;
        }

        LocalDate minStartDate = today;
        LocalDate maxStartDate = today.plusDays(7);

        request.setAttribute("today", today);
        request.setAttribute("todayText", DateTimeUtil.formatDate(today));
        request.setAttribute("isChained", isChained);
        request.setAttribute("defaultStartDate", startDate);
        request.setAttribute("defaultStartDateText", DateTimeUtil.formatDate(startDate));
        request.setAttribute("defaultStartDateIso", startDate.toString());
        request.setAttribute("defaultEndDate", endDate);
        request.setAttribute("defaultEndDateText", DateTimeUtil.formatDate(endDate));
        request.setAttribute("minStartDateIso", minStartDate.toString());
        request.setAttribute("maxStartDateIso", maxStartDate.toString());

        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));

        RequestUtil.forward(request, response, VIEW_NEXT);
    }

    /**
     * HÀM PHỤ — hiển thị biên lai thu tiền sau khi thanh toán thành công.
     * @param request HTTP request
     * @param response HTTP response
     */
    private void showReceipt(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        CounterSaleReceipt receipt = null;
        if (session != null) {
            Object receiptObj = session.getAttribute("lastReceipt");
            if (receiptObj instanceof CounterSaleReceipt) {
                receipt = (CounterSaleReceipt) receiptObj;
            }
        }

        if (receipt == null) {
            RequestUtil.redirect(request, response, PAGE_URL);
            return;
        }
        request.setAttribute("receipt", receipt);
        RequestUtil.forward(request, response, VIEW_RECEIPT);
    }

    /**
     * XỬ LÝ TẠO NHANH HỒ SƠ KHÁCH HÀNG TẠI QUẦY (AJAX POST).
     */
    private void handleQuickCreateCustomer(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {
        String fullName = RequestUtil.getTrimmedParam(request, "fullName");
        String phone = RequestUtil.getTrimmedParam(request, "phone");
        String dobStr = RequestUtil.getTrimmedParam(request, "dateOfBirth");
        String customerType = RequestUtil.getTrimmedParam(request, "customerType");
        String guardianName = RequestUtil.getTrimmedParam(request, "guardianName");
        String guardianRelationship = RequestUtil.getTrimmedParam(request, "guardianRelationship");
        String guardianPhone = RequestUtil.getTrimmedParam(request, "guardianPhone");
        String email = RequestUtil.getTrimmedParam(request, "email");

        LocalDate dob = null;
        if (dobStr != null && !dobStr.isBlank()) {
            dob = DateTimeUtil.parseHtmlDate(dobStr);
        }

        CounterCustomerCreateInput input = new CounterCustomerCreateInput(
                fullName,
                phone,
                dob,
                customerType,
                guardianName,
                guardianRelationship,
                guardianPhone,
                email
        );

        ServiceResult<CounterCustomerRow> result = counterSaleService.quickCreateCustomer(accountId, input);
        // Thành công: JS chuyển sang Bước 3 ngay → câu "đã gửi mật khẩu tạm tới ab***@…" hiện bằng toast ở trang kế tiếp.
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        }
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }

    /**
     * HÀM PHỤ — mã tài khoản trong session; chưa đăng nhập trả null.
     * @param request HTTP request
     * @return mã accountId nếu đã đăng nhập; null nếu chưa
     */
    private Integer getAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object accountId = session.getAttribute(SessionKey.ACCOUNT_ID);
        if (accountId instanceof Integer) {
            return (Integer) accountId;
        }
        return null;
    }
}
