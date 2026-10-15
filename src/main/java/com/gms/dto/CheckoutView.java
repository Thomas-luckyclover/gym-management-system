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

    /**
     * Nhận snapshot đơn và dữ liệu phụ do Service đã chuẩn bị.
     */
    public CheckoutView(Order order, String voucherCode, long secondsLeft,
            boolean voucherEditable, String voucherLockReason) {
        this.order = order;
        this.voucherCode = voucherCode;
        this.secondsLeft = secondsLeft;
        this.voucherEditable = voucherEditable;
        this.voucherLockReason = voucherLockReason;
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
    public String getVoucherMessage() {
        return voucherCode == null ? "" : "Đã áp dụng mã " + voucherCode;
    }

    /**
     * Các chuỗi tiền do MoneyUtil định dạng để AJAX và JSP cùng hiển thị.
     */
    public String getPriceText() {
        return MoneyUtil.formatMoney(order.getUnitPriceSnapshot());
    }

    public String getDiscountText() {
        return MoneyUtil.formatMoney(order.getDiscountAmount());
    }

    public String getTotalText() {
        return MoneyUtil.formatMoney(order.getFinalAmount());
    }

    public String getVatText() {
        return MoneyUtil.formatMoney(order.getVatAmount());
    }

    /**
     * Tiền trước thuế = tổng tiền sau giảm - phần VAT đã bao gồm.
     */
    public String getPreTaxText() {
        long preTaxAmount = order.getFinalAmount() - order.getVatAmount();
        return MoneyUtil.formatMoney(preTaxAmount);
    }
}
