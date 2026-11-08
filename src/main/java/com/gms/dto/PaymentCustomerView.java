package com.gms.dto;

/** DAO tạo từ Customers + Accounts; PaymentService dùng để xác định người mua và chặn Frozen. */
public class PaymentCustomerView {

    // DAO: Customers.CustomerId; dùng làm HolderCustomerId, không nhận từ form.
    private final int customerId;

    // DAO: Customers.Status = Frozen; Service chặn tạo đơn, không in boolean ra JSP.
    private final boolean frozen;

    /** Nhận mã hồ sơ và kết quả kiểm tra Frozen từ DAO. */
    public PaymentCustomerView(int customerId, boolean frozen) {
        this.customerId = customerId;
        this.frozen = frozen;
    }

    /** Mã khách đứng tên đơn, khác mã tài khoản đăng nhập. */
    public int getCustomerId() {
        return customerId;
    }

    /** Hồ sơ đang đóng băng nên chưa được mua gói. */
    public boolean isFrozen() {
        return frozen;
    }
}
