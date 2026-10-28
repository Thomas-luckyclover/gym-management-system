package com.gms.dto;

import java.time.LocalDate;
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
    private final LocalDate validFrom;
    private final LocalDate validTo;

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
            LocalDate validFrom,
            LocalDate validTo) {

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
        this.validFrom = validFrom;
        this.validTo = validTo;
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

    public String getPaymentMethodLabel() {
        if ("OnlineGateway".equals(paymentMethod)) {
            return "VNPay";
        }
        if ("Cash".equals(paymentMethod)) {
            return "Tiền mặt";
        }
        if ("Transfer".equals(paymentMethod)) {
            return "Chuyển khoản";
        }
        return paymentMethod;
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

    /** Tên màu/ảnh của thẻ gói (khớp data-tone ở trang Mua gói). */
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

    /** Nhãn ngắn dưới số tiền. */
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

    /** Dòng "Hoàn tiền" trong popup; null nếu không liên quan thì JSP không in. */
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

    /** Chỉ giao dịch thành công có gói còn hợp lệ mới hiện hiệu lực. */
    public boolean isHasValidity() {
        return isCanViewPackage() && validFrom != null && validTo != null;
    }

    public String getValidUntilText() {
        return validTo == null ? "" : validTo.format(DISPLAY_DATE);
    }

    public String getValidityText() {
        if (validFrom == null || validTo == null) {
            return "";
        }
        return validFrom.format(DISPLAY_DATE) + " – " + validTo.format(DISPLAY_DATE);
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