package com.gms.dto;

import java.time.LocalDate;

/**
 * MỘT KHÁCH trên màn Bán gói PT (người đứng tên, hoặc người tập cùng của gói đôi).
 *
 *   Ai tạo?   PtSaleDAO.findCustomer (tìm theo mã KH-xxxx hoặc số điện thoại).
 *   Ai xét?   PtSaleService.whyCannotBuy: còn gói thành viên dùng được, đủ 10 tuổi, không phải chính lễ tân / PT.
 *   Ai in?    pt-sales.jsp thẻ khách (tên, mã, SĐT, gói thành viên tới ngày nào, lý do không bán được).
 */
public class PtSaleCustomer {

    private int customerId;
    private int personId;              // so với lễ tân / PT: không bán cho chính mình, không tự làm PT của mình
    private String customerCode;       // KH-0001
    private String fullName;
    private String phone;              // hồ sơ phụ thuộc có thể null
    private LocalDate dateOfBirth;
    private String customerType;       // Self | Dependent
    private LocalDate membershipEnd;   // ngày cuối của gói thành viên đang dùng được; null = không có
    private boolean frozen;            // hôm nay đang bảo lưu → không tính là dùng được (CBR-59)
    private String blockReason;        // Service điền: null = bán được

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getPersonId() { return personId; }
    public void setPersonId(int personId) { this.personId = personId; }

    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public LocalDate getMembershipEnd() { return membershipEnd; }
    public void setMembershipEnd(LocalDate membershipEnd) { this.membershipEnd = membershipEnd; }

    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    /** JSP viết ${c.dependent}: hồ sơ phụ thuộc (không có SĐT riêng). */
    public boolean isDependent() { return "Dependent".equals(customerType); }
}
