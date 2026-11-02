package com.gms.dto;

import java.time.LocalDate;

/**
 * MỘT KHÁCH trên màn Bán gói PT — người đứng tên, hoặc người tập cùng của gói đôi.
 *
 *   PHẦN 1  DAO đọc từ DB → truyền vào CONSTRUCTOR (PtSaleDAO.readCustomer, dùng cho findCustomer + searchCustomers).
 *   PHẦN 2  Service tính thêm → SETTER (PtSaleService.whyCannotBuy → setBlockReason).
 *
 *   Ai in?  _sale-customer.jspf (thẻ khách ở cột "Đơn bán") · pt-sales.jsp (danh sách kết quả khi tắt JS)
 *           · PtSaleServlet.writeSuggestions đổi sang JSON cho ô gợi ý (pt-sales.js).
 *   Cột "In ra màn?" ở mỗi field: CÓ = người dùng nhìn thấy chữ / số này; KHÔNG = chỉ dùng để tính.
 */
public class PtSaleCustomer {

    /** DAO tạo từ 1 dòng Customers JOIN Persons (+ 2 cột tính sẵn MembershipEnd, IsFrozen). blockReason để trống. */
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


    // ---------------- PHẦN 1: DAO đọc (PtSaleDAO.readCustomer) ----------------                  In ra màn?
    private final int customerId;          // Customers.CustomerId → ?customer=12 trên URL, id trong JSON gợi ý      KHÔNG
    private final int personId;            // Customers.PersonId → so với lễ tân / PT: không bán cho chính mình      KHÔNG
    private final String customerCode;     // Customers.CustomerCode "KH-1001"                                      CÓ (thẻ khách, gợi ý)
    private final String customerType;     // Customers.CustomerType: Self | Dependent                              KHÔNG (qua isDependent)
    private final String fullName;         // Persons.FullName                                                      CÓ
    private final String phone;            // Persons.Phone; hồ sơ phụ thuộc = null → in "Hồ sơ phụ thuộc"          CÓ
    private final LocalDate dateOfBirth;   // Persons.DateOfBirth → whyCannotBuy tính tuổi (P-05)                   CÓ (thẻ khách)
    private final LocalDate membershipEnd; // SQL tính: MAX(EndDate) gói thành viên Active/Frozen phủ hôm nay; null = không có
                                           //   → "Gói thành viên tới 21/01/2027"                                  CÓ
    private final boolean frozen;          // SQL tính: hôm nay nằm trong 1 FreezeRecords → "Đang bảo lưu"          KHÔNG (qua blockReason)

    // ---------------- PHẦN 2: Service điền (PtSaleService.whyCannotBuy) ----------------
    private String blockReason;            // null = bán được; khác null = câu chữ đỏ ("Chưa đủ 10 tuổi…")          CÓ


    // ---------------- Getter (JSP đọc bằng ${cust.fullName} …) ----------------
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

    /** JSP viết ${c.dependent}: hồ sơ phụ thuộc (không có SĐT riêng). */
    public boolean isDependent() { return "Dependent".equals(customerType); }
}
