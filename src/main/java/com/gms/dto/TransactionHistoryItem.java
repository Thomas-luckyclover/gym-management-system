package com.gms.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TransactionHistoryItem {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
            Integer subscriptionId) {

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

    private String formatTime(LocalDateTime value) {
        return value == null ? "—" : value.format(DISPLAY_TIME);
    }
}