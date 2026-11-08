package com.gms.dto;

import com.gms.entity.Order;
import java.util.Arrays;
import java.util.List;
import com.gms.util.MoneyUtil;

/**
 * PaymentService gom dữ liệu đơn + voucher + hạn thanh toán; CheckoutServlet
 * chuyển tới JSP.
 */
public class CheckoutView {

    // OrderDAO đọc snapshot của đơn; JSP hiện giá/VAT, không đọc giá catalogue mới.
    private final Order order;

    // VoucherDAO đọc Vouchers.VoucherCode theo Orders.VoucherId; JSP hiện mã đã áp dụng.
    private final String voucherCode;

    // Service tính PaymentDueAt - now giờ VN, tối thiểu 0; common.js hiển thị mm:ss.
    private final long secondsLeft;

    // Service kiểm Pending, còn hạn, chưa có transaction Pending; dùng bật/tắt form.
    private final boolean voucherEditable;

    // Service tạo lý do khóa form; JSP in thông báo thay vì chỉ vô hiệu hóa nút.
    private final String voucherLockReason;

    // Chỉ có khi khách mua Membership lúc đang có gói còn hạn (DBR-52): gói mới nối sau gói đó.
    // Ngày dạng dd/MM/yyyy để JSP in thẳng; null nghĩa là gói bắt đầu ngay, không nối tiếp.
    private final String chainCurrentEndText;
    private final String chainStartText;
    private final String chainEndText;

    /**
     * Nhận snapshot đơn và dữ liệu phụ do Service đã chuẩn bị (không nối tiếp).
     */
    public CheckoutView(Order order, String voucherCode, long secondsLeft,
            boolean voucherEditable, String voucherLockReason) {
        this(order, voucherCode, secondsLeft, voucherEditable, voucherLockReason, null, null, null);
    }

    /**
     * Như trên, kèm thông tin gói nối tiếp để báo khách trước khi thanh toán.
     */
    public CheckoutView(Order order, String voucherCode, long secondsLeft,
            boolean voucherEditable, String voucherLockReason,
            String chainCurrentEndText, String chainStartText, String chainEndText) {
        this.order = order;
        this.voucherCode = voucherCode;
        this.secondsLeft = secondsLeft;
        this.voucherEditable = voucherEditable;
        this.voucherLockReason = voucherLockReason;
        this.chainCurrentEndText = chainCurrentEndText;
        this.chainStartText = chainStartText;
        this.chainEndText = chainEndText;
    }

    /** Ngày hết hạn của gói đang dùng. */
    public String getChainCurrentEndText() {
        return chainCurrentEndText;
    }

    /** Ngày gói mới bắt đầu (hôm sau ngày hết hạn gói cũ). */
    public String getChainStartText() {
        return chainStartText;
    }

    /** Ngày gói mới hết hạn. */
    public String getChainEndText() {
        return chainEndText;
    }

    /**
     * Đơn thuộc tài khoản hiện tại để hiển thị.
     */
    public Order getOrder() {
        return order;
    }

    /**
     * Mã đã được giữ lượt trên đơn, không phải mã đang thử nhập.
     */
    public String getVoucherCode() {
        return voucherCode;
    }

    /**
     * Số giây từ server cho bộ đếm dùng chung.
     */
    public long getSecondsLeft() {
        return secondsLeft;
    }

    /**
     * JSP dùng để bật/tắt form; backend vẫn kiểm lại khi POST.
     */
    public boolean isVoucherEditable() {
        return voucherEditable;
    }

    /**
     * Thông báo tiếng Việt cho trạng thái không được đổi voucher.
     */
    public String getVoucherLockReason() {
        return voucherLockReason;
    }

    /**
     * Quyền lợi là snapshot từ Orders, không gán nội dung cố định theo tên gói.
     */
    // GETTER TỰ TÍNH — không có field/setter "benefitLines"; = tách snapshot quyền lợi gói theo từng dòng; JSP: customer/checkout.jsp:56
    public List<String> getBenefitLines() {
        String benefits = order.getPackageBenefitsSnapshot();
        if (benefits == null || benefits.isBlank()) {
            return List.of();
        }
        return Arrays.asList(benefits.split("[\\r\\n]+"));
    }

    /**
     * JSP dùng cho thông báo đã áp dụng; tổng giảm ở bảng giá, không lặp lại
     * dưới ô.
     */
    // GETTER TỰ TÍNH — không có field/setter "voucherMessage"; = "Đã áp dụng mã …", chưa có mã → ""; JSP: customer/checkout.jsp:103
    public String getVoucherMessage() {
        return voucherCode == null ? "" : "Đã áp dụng mã " + voucherCode;
    }

    /**
     * Các chuỗi tiền do MoneyUtil định dạng để AJAX và JSP cùng hiển thị.
     */
    // GETTER TỰ TÍNH — không có field/setter "priceText"; = UnitPriceSnapshot định dạng tiền; JSP: customer/checkout.jsp:74
    public String getPriceText() {
        return MoneyUtil.formatMoney(order.getUnitPriceSnapshot());
    }

    // GETTER TỰ TÍNH — không có field/setter "discountText"; = DiscountAmount định dạng tiền; JSP: customer/checkout.jsp:79
    public String getDiscountText() {
        return MoneyUtil.formatMoney(order.getDiscountAmount());
    }

    // GETTER TỰ TÍNH — không có field/setter "totalText"; = FinalAmount định dạng tiền; JSP: customer/checkout.jsp:111
    public String getTotalText() {
        return MoneyUtil.formatMoney(order.getFinalAmount());
    }

    // GETTER TỰ TÍNH — không có field/setter "vatText"; = VatAmount định dạng tiền; JSP: customer/checkout.jsp:113
    public String getVatText() {
        return MoneyUtil.formatMoney(order.getVatAmount());
    }

    /**
     * Tiền trước thuế = tổng tiền sau giảm - phần VAT đã bao gồm.
     */
    // GETTER TỰ TÍNH — không có field/setter "preTaxText"; = FinalAmount − VatAmount định dạng tiền; JSP: không dùng (chỉ Java/test)
    public String getPreTaxText() {
        long preTaxAmount = order.getFinalAmount() - order.getVatAmount();
        return MoneyUtil.formatMoney(preTaxAmount);
    }
}
