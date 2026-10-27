package com.gms.dto;

import java.time.LocalDateTime;

/**
 * DTO đại diện cho một lần thử thanh toán của đơn hàng (Màn #41 - UC-26).
 *
 * <p>Nơi tạo: {@link com.gms.dao.TransactionDAO#findAttemptsByOrderId}.</p>
 * <p>Nơi dùng: Hiển thị bảng các lần thử thanh toán trong modal chi tiết đơn hàng.</p>
 *
 * @author Le Cong Minh
 */
public class TransactionAttemptView {

    // DAO: Transactions.TransactionId, khóa chính của lần thử thanh toán.
    // JSP dùng để làm id dòng hoặc nút bấm hoàn tiền nếu cần hoàn.
    private final int transactionId;

    // DAO: Transactions.OrderId, mã đơn hàng tương ứng.
    // JSP dùng để tham chiếu đơn.
    private final int orderId;

    // DAO: Transactions.Amount, số tiền của giao dịch (VND).
    // JSP hiển thị định dạng qua gms:formatMoney.
    private final long amount;

    // DAO: Transactions.PaymentMethod, Cash | Transfer | OnlineGateway.
    // JSP hiển thị badge phương thức thanh toán.
    private final String paymentMethod;

    // DAO: Transactions.GatewayReference, mã giao dịch VNPay hoặc mã chuyển khoản ngân hàng.
    // JSP hiển thị mã tham chiếu để đối soát.
    private final String gatewayReference;

    // DAO: Persons.FullName của Lễ tân nhận tiền/xác nhận chuyển khoản tại quầy.
    // JSP hiển thị người nhận tiền tại quầy.
    private final String receiverName;

    // DAO: Transactions.Status, Pending | Success | Failed | Expired.
    // JSP hiển thị badge trạng thái giao dịch.
    private final String status;

    // DAO: Transactions.FailureReason, lý do thất bại từ cổng hoặc ngân hàng.
    // JSP hiển thị nguyên nhân khi giao dịch Failed.
    private final String failureReason;

    // DAO: Transactions.NeedsRefund, cờ đánh dấu cần hoàn tiền (CBR-72).
    // JSP hiển thị cờ cảnh báo hoàn tiền.
    private final boolean needsRefund;

    // DAO: Transactions.RefundedAt, thời điểm Manager hoàn tiền ngoài hệ thống.
    // JSP hiển thị ngày giờ hoàn tiền.
    private final LocalDateTime refundedAt;

    // DAO: Persons.FullName của Manager đã duyệt hoàn tiền.
    // JSP hiển thị người thực hiện hoàn tiền.
    private final String refundedByName;

    // DAO: Transactions.RefundNote, ghi chú hoàn tiền.
    // JSP hiển thị thông tin số tài khoản / mã chuyển hoàn.
    private final String refundNote;

    // DAO: Transactions.CreatedAt, thời điểm khởi tạo lần thử.
    // JSP hiển thị qua gms:formatDateTime.
    private final LocalDateTime createdAt;

    // DAO: Transactions.ExpiresAt, hạn chót phiên thanh toán online (5 phút).
    // JSP hiển thị thời hạn nếu có.
    private final LocalDateTime expiresAt;

    // DAO: Transactions.CompletedAt, thời điểm giao dịch kết thúc.
    // JSP hiển thị thời gian hoàn tất.
    private final LocalDateTime completedAt;

    public TransactionAttemptView(int transactionId, int orderId, long amount,
                                  String paymentMethod, String gatewayReference,
                                  String receiverName, String status,
                                  String failureReason, boolean needsRefund,
                                  LocalDateTime refundedAt, String refundedByName,
                                  String refundNote, LocalDateTime createdAt,
                                  LocalDateTime expiresAt, LocalDateTime completedAt) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.gatewayReference = gatewayReference;
        this.receiverName = receiverName;
        this.status = status;
        this.failureReason = failureReason;
        this.needsRefund = needsRefund;
        this.refundedAt = refundedAt;
        this.refundedByName = refundedByName;
        this.refundNote = refundNote;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.completedAt = completedAt;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public int getOrderId() {
        return orderId;
    }

    public long getAmount() {
        return amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public boolean isNeedsRefund() {
        return needsRefund;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public String getRefundedByName() {
        return refundedByName;
    }

    public String getRefundNote() {
        return refundNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public boolean isSuccess() {
        return "Success".equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return "Pending".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "Failed".equalsIgnoreCase(status);
    }

    public boolean isExpired() {
        return "Expired".equalsIgnoreCase(status);
    }
}
