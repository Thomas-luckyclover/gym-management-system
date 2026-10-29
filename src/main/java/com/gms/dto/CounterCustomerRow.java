package com.gms.dto;

import com.gms.entity.Customer;
import java.time.LocalDate;

/**
 * MỘT DÒNG KHÁCH trong bảng "Chọn khách" (/receptionist/sales?package=…).
 *
 * Ai tạo: CounterSaleDAO.searchCustomers / findCustomer, đọc từng cột ngay
 * trong vòng while. Ai dùng: counter-sale-customers.jsp (bảng),
 * counter-sale-next.jsp (khách đã chọn).
 */
public class CounterCustomerRow {

    // DAO: Customers.CustomerId. Gắn vào ?customer=… của nút "Chọn"; không in thành chữ.
    private final int customerId;

    // DAO: Customers.CustomerCode, dạng KH-0102. In dưới tên khách.
    private final String customerCode;

    // DAO: Persons.FullName. In đậm ở cột "Khách hàng".
    private final String fullName;

    // DAO: Persons.Phone với hồ sơ Self; Customers.GuardianPhone với hồ sơ Dependent (vì trẻ không có SĐT riêng).
    private final String phone;

    // DAO: CustomerType = 'Dependent'. JSP hiện thẻ "Hồ sơ phụ thuộc" và nhãn "SĐT người giám hộ".
    private final boolean isDependent;

    // DAO: có dòng trong Accounts cho Persons này hay không. false thì hiện thẻ "Chưa có tài khoản" (vẫn mua được).
    private final boolean hasAccount;

    // DAO: Customers.Status đổi sang enum. JSP in getDisplayName() trong thẻ màu.
    private final Customer.Status status;

    // DAO: tên gói tập còn hạn (Orders.PackageNameSnapshot của Subscriptions Membership). null = chưa có gói.
    private final String currentPackageName;

    // DAO: Subscriptions.EndDate của gói tập đó (ngày cuối còn dùng). null khi chưa có gói.
    private final LocalDate currentPackageEnd;
  

    public CounterCustomerRow(int customerId, String customerCode, String fullName, String phone,
            boolean isDependent, boolean hasAccount, Customer.Status status,
            String currentPackageName, LocalDate currentPackageEnd) {
        this.customerId = customerId;
        this.customerCode = customerCode;
        this.fullName = fullName;
        this.phone = phone;
        this.isDependent = isDependent;
        this.hasAccount = hasAccount;
        this.status = status;
        this.currentPackageName = currentPackageName;
        this.currentPackageEnd = currentPackageEnd;
    }

    // Mã khách trong DB, dùng trên URL.
    public int getCustomerId() {
        return customerId;
    }

    // Mã KH-nnnn in ra màn hình.
    public String getCustomerCode() {
        return customerCode;
    }

    // Họ tên in ra màn hình.
    public String getFullName() {
        return fullName;
    }

    // SĐT thô, 10 số.
    public String getPhone() {
        return phone;
    }

    /**
     * SĐT chia nhóm cho dễ đọc: 0912345678 thành "0912 345 678". Chỉ để hiển
     * thị; không dùng để so sánh hay lưu. Số không đủ 10 chữ số thì trả nguyên
     * chuỗi.
     */
    public String getPhoneText() {
        boolean isTenDigits = phone != null && phone.length() == 10;
        if (!isTenDigits) {
            return phone;
        }
        String firstPart = phone.substring(0, 4);
        String secondPart = phone.substring(4, 7);
        String thirdPart = phone.substring(7);
        return firstPart + " " + secondPart + " " + thirdPart;
    }

    // JSP đọc ${row.dependent}.
    public boolean isDependent() {
        return isDependent;
    }

    // JSP đọc ${row.hasAccount}.
    public boolean isHasAccount() {
        return hasAccount;
    }

    // Trạng thái hồ sơ (enum).
    public Customer.Status getStatus() {
        return status;
    }

    /**
     * Class màu của thẻ trạng thái (GBR-28): Active xanh lá, New xanh dương,
     * Frozen vàng, còn lại xám. JSP ghép vào class="badge …" nên không phải so
     * sánh chữ trạng thái trong JSP.
     */
    public String getStatusBadgeClass() {
        if (status == Customer.Status.ACTIVE) {
            return "badge-success";
        }
        if (status == Customer.Status.NEW) {
            return "badge-info";
        }
        if (status == Customer.Status.FROZEN) {
            return "badge-warning";
        }
        return "badge-neutral";
    }

    // Tên gói tập còn hạn, có thể null.
    public String getCurrentPackageName() {
        return currentPackageName;
    }

    // Ngày cuối của gói tập còn hạn, có thể null.
    public LocalDate getCurrentPackageEnd() {
        return currentPackageEnd;
    }
}
