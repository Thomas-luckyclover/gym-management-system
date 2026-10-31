package com.gms.dto;

/**
 * KẾT QUẢ KIỂM TRA MÃ GIẢM GIÁ TẠI QUẦY (/receptionist/sales/voucher), SRS #37.
 *
 * Ai tạo: CounterSaleService.checkVoucher khi lễ tân nhập mã voucher tại bước 3.
 * Ai dùng: CounterSaleServlet trả JSON về cho counter-sale.js cập nhật realtime bảng tính tiền.
 * Chỉ đọc: tạo bằng constructor, không có setter.
 */
public class CounterVoucherResult {

    // Trạng thái mã hợp lệ hay không.
    private final boolean valid;

    // Khóa chính VoucherId trong DB (null nếu không hợp lệ).
    private final Integer voucherId;

    // Mã voucher viết hoa chuẩn (VD: WELCOME10).
    private final String voucherCode;

    // Số tiền giảm trừ thực tế (đồng).
    private final long discountAmount;

    // Chuỗi hiển thị số tiền giảm (VD: 120.000 đ).
    private final String discountText;

    // Tiền trước thuế sau khi đã trừ giảm giá.
    private final long preTaxAmount;

    // Chuỗi hiển thị tiền trước thuế.
    private final String preTaxText;

    // Tiền thuế VAT sau khi đã trừ giảm giá.
    private final long vatAmount;

    // Chuỗi hiển thị tiền VAT.
    private final String vatText;

    // Tổng số tiền khách cần trả sau khi trừ giảm giá.
    private final long finalAmount;

    // Chuỗi hiển thị tổng tiền khách cần trả.
    private final String finalText;

    // Thông điệp phản hồi (thành công hoặc thông báo chi tiết).
    private final String message;

    public CounterVoucherResult(boolean valid, Integer voucherId, String voucherCode,
            long discountAmount, String discountText, long preTaxAmount, String preTaxText,
            long vatAmount, String vatText, long finalAmount, String finalText, String message) {
        this.valid = valid;
        this.voucherId = voucherId;
        this.voucherCode = voucherCode;
        this.discountAmount = discountAmount;
        this.discountText = discountText;
        this.preTaxAmount = preTaxAmount;
        this.preTaxText = preTaxText;
        this.vatAmount = vatAmount;
        this.vatText = vatText;
        this.finalAmount = finalAmount;
        this.finalText = finalText;
        this.message = message;
    }

    public boolean isValid() {
        return valid;
    }

    public Integer getVoucherId() {
        return voucherId;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public String getDiscountText() {
        return discountText;
    }

    public long getPreTaxAmount() {
        return preTaxAmount;
    }

    public String getPreTaxText() {
        return preTaxText;
    }

    public long getVatAmount() {
        return vatAmount;
    }

    public String getVatText() {
        return vatText;
    }

    public long getFinalAmount() {
        return finalAmount;
    }

    public String getFinalText() {
        return finalText;
    }

    public String getMessage() {
        return message;
    }
}

