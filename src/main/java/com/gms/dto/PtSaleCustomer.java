package com.gms.dto;

import java.time.LocalDate;

/**
 * 1 KHÁCH trên màn Bán gói PT — DAO tạo: PtSaleDAO.searchCustomers / findCustomer; Service điền blockReason (whyCannotBuy).
 * Hiện ở: ô gợi ý tìm khách (pt-sales.js nhận JSON), danh sách kết quả, thẻ khách _sale-customer.jspf (biến cust).
 */
public class PtSaleCustomer {
    public PtSaleCustomer(int customerId, int personId, String customerCode, String customerType, String fullName,
            String phone, LocalDate dateOfBirth, LocalDate membershipEnd, boolean frozen) {
        this.customerId = customerId;
        this.personId = personId;
        this.customerCode = customerCode;
        this.customerType = customerType;
        this.fullName = fullName;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.membershipEnd = membershipEnd;
        this.frozen = frozen;
    }

    // ===== IN RA MÀN =====
    // ← Persons.FullName → tên in đậm, chữ cái avatar
    private final String fullName;
    // ← Customers.CustomerCode → "KH-2010"
    private final String customerCode;
    // ← Persons.Phone (null = hồ sơ phụ thuộc) → SĐT
    private final String phone;
    // ← Persons.DateOfBirth → ngày sinh trên thẻ, tính tuổi (whyCannotBuy)
    private final LocalDate dateOfBirth;
    // ← MAX(EndDate) gói Membership phủ hôm nay → "Gói thành viên tới dd/MM/yyyy" (null = không có gói)
    private final LocalDate membershipEnd;
    // ← whyCannotBuy: null = bán được (thẻ xanh) · có chữ = lý do đỏ, khóa nút Giữ lịch
    private String blockReason;

    // ===== DÙNG ĐỂ KIỂM (không in) =====
    // ← Customers.CustomerId → ?customer=<id>
    private final int customerId;
    // ← Persons.PersonId → chặn lễ tân tự bán cho mình, chặn PT trùng khách
    private final int personId;
    // ← Customers.CustomerType
    private final String customerType;
    // ← có FreezeRecords phủ hôm nay → "Đang bảo lưu gói thành viên"
    private final boolean frozen;

    public int getCustomerId() { return customerId; }
    public int getPersonId() { return personId; }
    public String getCustomerCode() { return customerCode; }
    public String getCustomerType() { return customerType; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public LocalDate getMembershipEnd() { return membershipEnd; }
    public boolean isFrozen() { return frozen; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public boolean isDependent() { return "Dependent".equals(customerType); }
}
