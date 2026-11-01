package com.gms.dto;

import java.time.LocalDate;

/**
 * THAM SỐ ĐẦU VÀO CHO LỄ TÂN THU TIỀN TẠI QUẦY (/receptionist/sales), SRS #37.
 *
 * Ai tạo: CounterSaleServlet.doPost đọc từ form thanh toán gửi lên.
 * Ai dùng: CounterSaleService.checkout nhận để kiểm tra luật và ghi nhận đơn hàng.
 * Chỉ đọc: tạo bằng constructor, không có setter.
 */
public class CounterCheckoutInput {

    // Request: form POST name="packageId", mã gói đang bán.
    // Ý nghĩa: xác định gói tập hoặc vé ngày khách chọn mua.
    // Cách dùng: Service dùng tìm thông tin gói trong Packages và kiểm tra IsActive.
    private final int packageId;

    // Request: form POST name="customerId", mã khách hàng trong hệ thống.
    // Ý nghĩa: xác định hồ sơ hội viên thụ hưởng gói.
    // Cách dùng: Service dùng kiểm tra hồ sơ hợp lệ và gắn vào Orders.HolderCustomerId.
    private final int customerId;

    // Request: form POST name="customStartDate" (khi chọn hẹn ngày khác), null nếu kích hoạt hôm nay.
    // Ý nghĩa: ngày bắt đầu hiệu lực gói mà lễ tân và khách đã thống nhất.
    // Cách dùng: Service kiểm tra nằm trong hôm nay đến hôm nay + 7 ngày rồi gán vào Subscriptions.StartDate.
    private final LocalDate startDate;

    // Request: form POST name="paymentMethod", giá trị "Cash" hoặc "Transfer".
    // Ý nghĩa: phương thức lễ tân đã kiểm tra thu tiền thực tế tại quầy (tiền mặt hay chuyển khoản/POS).
    // Cách dùng: Service ghi vào Transactions.PaymentMethod để đối soát sổ sách cuối ca trực.
    private final String paymentMethod;

    // Request: form POST name="voucher", mã giảm giá khách cung cấp (nếu có).
    // Ý nghĩa: mã ưu đãi áp dụng để giảm trừ tiền đơn hàng.
    // Cách dùng: Service dùng kiểm tra điều kiện voucher (nếu có nhập) hoặc bỏ qua nếu để trống.
    private final String voucherCode;

    public CounterCheckoutInput(int packageId, int customerId, LocalDate startDate,
            String paymentMethod, String voucherCode) {
        this.packageId = packageId;
        this.customerId = customerId;
        this.startDate = startDate;
        this.paymentMethod = paymentMethod;
        this.voucherCode = voucherCode;
    }

    // Mã gói đang mua, CounterSaleService dùng để đọc thông tin gói từ Packages.
    public int getPackageId() {
        return packageId;
    }

    // Mã khách hàng thụ hưởng, CounterSaleService dùng để gắn vào đơn hàng và thẻ tập.
    public int getCustomerId() {
        return customerId;
    }

    // Ngày bắt đầu hẹn trước (null nếu chọn hôm nay), CounterSaleService dùng gán Subscriptions.StartDate.
    public LocalDate getStartDate() {
        return startDate;
    }

    // Hình thức thu tiền ("Cash" hoặc "Transfer"), CounterSaleService dùng ghi Transactions.PaymentMethod.
    public String getPaymentMethod() {
        return paymentMethod;
    }

    // Mã voucher nhập vào (có thể null hoặc rỗng), CounterSaleService dùng để kiểm tra giảm giá nếu có.
    public String getVoucherCode() {
        return voucherCode;
    }
}
