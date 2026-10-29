package com.gms.servlet.receptionist;

import com.gms.constant.SessionKey;
import com.gms.dto.CounterCustomerRow;
import com.gms.dto.CounterPackageCard;
import com.gms.dto.CounterPackageView;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SERVLET MÀN LỄ TÂN BÁN GÓI TẠI QUẦY — /receptionist/sales (SRS #37, đợt 1).
 * ReceptionistAreaFilter kiểm quyền. Chỉ có GET: đợt này chưa ghi dữ liệu nào,
 * nên chưa cần CSRF.
 *
 * GET /receptionist/sales Chọn gói → counter-sale-packages.jsp GET
 * /receptionist/sales?package=3 Chọn khách → counter-sale-customers.jsp GET
 * /receptionist/sales/customers?q=mai ô tìm khách (counter-sale.js gọi AJAX) →
 * JSON tối đa 20 khách GET /receptionist/sales?package=3&customer=12 bước
 * Voucher → counter-sale-next.jsp (tạm)
 *
 * Gói PT đi sang /receptionist/pt-sales (PtSaleServlet), buổi PT lẻ chưa có màn
 * nên thẻ bị khóa ở JSP.
 */
@WebServlet(urlPatterns = {"/receptionist/sales", "/receptionist/sales/customers"})
public class CounterSaleServlet extends HttpServlet {

    private static final String PAGE_URL = "/receptionist/sales";
    private static final String VIEW_PACKAGES = "receptionist/counter-sale-packages";
    private static final String VIEW_CUSTOMERS = "receptionist/counter-sale-customers";
    private static final String VIEW_NEXT = "receptionist/counter-sale-next";

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

        // B3: chưa có ?package=… thì hiện màn Chọn gói.
        Integer packageId = RequestUtil.getPositiveIntParam(request, "package");
        if (packageId == null) {
            showPackages(request, response, accountId);
            return;
        }

        // B4: có gói, kiểm tra gói còn bán rồi tải thẻ gói (cột Tóm tắt đơn).
        ServiceResult<CounterPackageCard> cardResult = counterSaleService.getPackageCard(accountId, packageId);
        if (!cardResult.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", cardResult.getMessage());
            RequestUtil.redirect(request, response, PAGE_URL);
            return;
        }
        CounterPackageCard selectedCard = cardResult.getData();
        request.setAttribute("selectedCard", selectedCard);

        // B5: chưa có ?customer=… thì hiện màn Chọn khách.
        Integer customerId = RequestUtil.getPositiveIntParam(request, "customer");
        if (customerId == null) {
            showCustomers(request, response);
            return;
        }

        // B6: đã chọn khách, sang bước tiếp theo (đang làm).
        showNext(request, response, accountId, customerId, packageId);
    }

    /**
     * HÀM PHỤ — màn Chọn gói: Service dựng 3 tab thẻ gói rồi forward JSP.
     */
    private void showPackages(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws ServletException, IOException {
        ServiceResult<CounterPackageView> result = counterSaleService.getPackageView(accountId);
        if (result.isSuccess()) {
            request.setAttribute("packageView", result.getData());   // → counter-sale-packages.jsp, 3 tab
        } else {
            request.setAttribute("pageError", result.getMessage());  // → khung báo lỗi của JSP
        }
        RequestUtil.forward(request, response, VIEW_PACKAGES);
    }

    /**
     * HÀM PHỤ — màn Chọn khách: chỉ vẽ khung, bảng khách do counter-sale.js đổ
     * vào sau mỗi lần gõ.
     */
    private void showCustomers(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        RequestUtil.forward(request, response, VIEW_CUSTOMERS);
    }

    /**
     * HÀM PHỤ — JSON cho ô tìm khách. Mỗi khách là một object: {customerId,
     * customerCode, fullName, phoneText, isDependent, hasAccount, statusText,
     * statusBadgeClass, currentPackageName, currentPackageEndText}. Không trả
     * ngày sinh, mã Person hay SĐT thô. currentPackageName và
     * currentPackageEndText là chuỗi rỗng khi khách chưa có gói tập.
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
            String currentPackageEndText = "";
            if (customer.getCurrentPackageEnd() != null) {
                currentPackageEndText = DateTimeUtil.formatDate(customer.getCurrentPackageEnd());
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerId", customer.getCustomerId());
            row.put("customerCode", customer.getCustomerCode());
            row.put("fullName", customer.getFullName());
            row.put("phoneText", customer.getPhoneText());
            row.put("isDependent", customer.isDependent());
            row.put("hasAccount", customer.isHasAccount());
            row.put("hasAccount", customer.isHasAccount());
            row.put("isOwnProfile", customer.isOwnProfile());
            row.put("statusText", customer.getStatus().getDisplayName());
            row.put("statusBadgeClass", customer.getStatusBadgeClass());
            row.put("currentPackageName", currentPackageName);
            row.put("currentPackageEndText", currentPackageEndText);
            rows.add(row);
        }
        JsonUtil.writeJson(response, JsonResponse.success(rows));
    }

    /**
     * HÀM PHỤ — bước sau Chọn khách (Voucher, chưa làm): hiện tóm tắt gói +
     * khách đã chọn.
     */
    private void showNext(HttpServletRequest request, HttpServletResponse response, int accountId,
            int customerId, int packageId) throws ServletException, IOException {
        ServiceResult<CounterCustomerRow> result = counterSaleService.getCustomer(accountId, customerId);
        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, PAGE_URL + "?package=" + packageId);
            return;
        }
        request.setAttribute("selectedCustomer", result.getData());  // → counter-sale-next.jsp
        RequestUtil.forward(request, response, VIEW_NEXT);
    }

    /**
     * HÀM PHỤ — mã tài khoản trong session; chưa đăng nhập trả null.
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
