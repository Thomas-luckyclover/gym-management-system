package com.gms.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TransactionHistoryItem {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DISPLAY_MONTH =
            DateTimeFormatter.ofPattern("MM/yyyy");

    private final int transactionId;
    private final String orderCode;
    private final String packageName;
    private final long amount;
    private final String paymentMethod;
    private final String status;
    private final String gatewayReference;
    private final LocalDateTime createdAt;
    private final LocalDateTime completedAt;
    private final boolean needsRefund;
    private final LocalDateTime refundedAt;
    private final Integer subscriptionId;
    private final String packageType;
    private final long originalPrice;
    private final long discountAmount;
    private final String voucherCode;
    private final int vatRate;
    private final long vatAmount;

    public TransactionHistoryItem(
            int transactionId,
            String orderCode,
            String packageName,
            long amount,
            String paymentMethod,
            String status,
            String gatewayReference,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            boolean needsRefund,
            LocalDateTime refundedAt,
            Integer subscriptionId,
            String packageType,
            long originalPrice,
            long discountAmount,
            String voucherCode,
            int vatRate,
            long vatAmount) {

        this.transactionId = transactionId;
        this.orderCode = orderCode;
        this.packageName = packageName;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.gatewayReference = gatewayReference;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.needsRefund = needsRefund;
        this.refundedAt = refundedAt;
        this.subscriptionId = subscriptionId;
        this.packageType = packageType;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.voucherCode = voucherCode;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public Integer getSubscriptionId() {
        return subscriptionId;
    }

    public boolean isOnlineGateway() {
        return "OnlineGateway".equals(paymentMethod);
    }

    public boolean isCanViewPackage() {
        return subscriptionId != null
                && "Success".equals(status)
                && !needsRefund
                && refundedAt == null;
    }

    /** Chỉ có 2 hình thức hiển thị cho khách: VNPay (online) hoặc Tại quầy (tiền mặt/chuyển khoản do lễ tân thu). */
    public String getPaymentMethodLabel() {
        return "OnlineGateway".equals(paymentMethod) ? "VNPay" : "Tại quầy";
    }

    public String getStatusLabel() {
        if ("Success".equals(status)) {
            return "Thanh toán thành công";
        }
        if ("Failed".equals(status)) {
            return "Thanh toán thất bại";
        }
        if ("Expired".equals(status)) {
            return "Hết hạn lần thanh toán";
        }
        return "Đang chờ kết quả";
    }

    public String getRefundLabel() {
        if (refundedAt != null) {
            return "Đã hoàn tiền";
        }
        return needsRefund ? "Cần hoàn tiền" : "—";
    }

    public String getCreatedAtText() {
        return formatTime(createdAt);
    }

    public String getCompletedAtText() {
        return formatTime(completedAt);
    }

    // ===== Dùng cho giao diện danh sách + popup chi tiết =====

    public long getOriginalPrice() {
        return originalPrice;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    /** % VAT lúc mua (đã nằm trong giá, không cộng thêm). */
    public int getVatRate() {
        return vatRate;
    }

    public long getVatAmount() {
        return vatAmount;
    }

    public boolean isHasCompleted() {
        return completedAt != null;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    /** Đơn có giảm giá thì popup hiện thêm dòng Giảm giá. */
    public boolean isHasDiscount() {
        return discountAmount > 0;
    }

    /** Tên màu/ảnh của banner popup (khớp data-tone ở trang Mua gói). */
    public String getTone() {
        if (packageType == null) {
            return "membership";
        }
        switch (packageType) {
            case "DayPass":       return "day-pass";
            case "PTSingle":      return "pt-single";
            case "PTCouple":      return "pt-couple";
            case "SingleSession": return "single-session";
            default:              return "membership";
        }
    }

    /** Trạng thái để tô màu: đã/đang chờ hoàn tiền thì là "Refunded", còn lại theo Status. */
    public String getDisplayStatus() {
        if (refundedAt != null || needsRefund) {
            return "Refunded";
        }
        if ("Success".equals(status) || "Failed".equals(status)
                || "Expired".equals(status)) {
            return status;
        }
        return "Pending";
    }

    /** Nhãn ngắn của trạng thái. */
    public String getShortStatusLabel() {
        if (refundedAt != null) {
            return "Đã hoàn tiền";
        }
        if (needsRefund) {
            return "Chờ hoàn tiền";
        }
        switch (getDisplayStatus()) {
            case "Success": return "Thành công";
            case "Failed":  return "Thất bại";
            case "Expired": return "Hết hạn";
            default:        return "Đang chờ";
        }
    }

    /** Dòng "Hoàn tiền" trong popup; null thì JSP không in. */
    public String getRefundRowText() {
        if (refundedAt != null) {
            return refundedAt.format(DISPLAY_DATE);
        }
        return needsRefund ? "Đang chờ hoàn tiền" : null;
    }

    /** Thất bại hoặc hết hạn (chưa hoàn tiền) thì cho mua lại. */
    public boolean isCanBuyAgain() {
        return ("Failed".equals(status) || "Expired".equals(status))
                && refundedAt == null && !needsRefund;
    }

    public String getCreatedDateText() {
        return createdAt == null ? "—" : createdAt.format(DISPLAY_DATE);
    }

    /** Tiêu đề nhóm: "Tháng 10/2026". */
    public String getMonthLabel() {
        return createdAt == null ? "" : "Tháng " + createdAt.format(DISPLAY_MONTH);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "—" : value.format(DISPLAY_TIME);
    }
}