package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Giao dịch thanh toán (lần thử thanh toán cho đơn hàng), ánh xạ bảng Transactions trong database (Cluster B).
 *
 * <p>Quy tắc nghiệp vụ:</p>
 * <ul>
 *   <li>Mỗi lần thử thanh toán là 1 bản ghi Transaction (DBR-41). Một Order có thể có nhiều Transaction nhưng tối đa 1 Pending và 1 Success (DBR-42).</li>
 *   <li>Phương thức thanh toán: Cash (tiền mặt), Transfer (chuyển khoản tại quầy), OnlineGateway (cổng VNPay).</li>
 *   <li>Cổng Online bắt buộc có hạn ExpiresAt (5 phút, không vượt hạn đơn CBR-69, P-08, CK_Transactions_Online).</li>
 *   <li>Thanh toán tại quầy thành công phải ghi nhận Lễ tân thu tiền (ReceivedByEmployeeId, CBR-77).</li>
 *   <li>Vòng đời trạng thái: Pending → Success, Failed, hoặc Expired. CompletedAt bắt buộc khi Success/Failed (CK_Transactions_Completed).</li>
 *   <li>Trường hợp tiền về muộn khi đơn đã hủy (CBR-72): Transaction vẫn là Success nhưng NeedsRefund = 1 để Manager hoàn tiền ngoài hệ thống (BR-47).</li>
 * </ul>
 *
 * @author Le Cong Minh
 */
public class Transaction {

    /** Khóa chính tự tăng, bất biến → final, không có setter. */
    private final int transactionId;

    /** Đơn hàng tương ứng với giao dịch (FK -> Orders.OrderId). */
    private int orderId;

    /** Số tiền phải trả tại thời điểm tạo giao dịch (VND, Amount > 0). */
    private long amount;

    /** Phương thức thanh toán: Cash, Transfer, OnlineGateway. */
    private PaymentMethod paymentMethod;

    /** Mã tham chiếu từ cổng thanh toán (vnp_TransactionNo / vnp_BankTranNo) hoặc mã chuyển khoản ngân hàng. */
    private String gatewayReference;

    /** Nhân viên Lễ tân nhận tiền mặt hoặc xác nhận chuyển khoản tại quầy (null với OnlineGateway). */
    private Integer receivedByEmployeeId;

    /** Trạng thái giao dịch: Pending, Success, Failed, Expired. */
    private Status status;

    /** Lý do giao dịch thất bại (mã lỗi VNPay, lỗi thẻ, hoặc lý do hủy). */
    private String failureReason;

    /** Cờ đánh dấu cần hoàn tiền (1 = tiền về cho đơn đã hủy hoặc lệch tiền theo CBR-72). */
    private boolean needsRefund;

    /** Thời điểm Manager xác nhận đã hoàn tiền ngoài hệ thống (BR-47). */
    private LocalDateTime refundedAt;

    /** Nhân viên Manager thực hiện đánh dấu hoàn tiền (FK -> Employees.EmployeeId). */
    private Integer refundedByEmployeeId;

    /** Ghi chú hoàn tiền (số tài khoản nhận, mã giao dịch hoàn, lý do...). */
    private String refundNote;

    /** Thời điểm khởi tạo giao dịch. */
    private LocalDateTime createdAt;

    /** Hạn chót của phiên thanh toán online (5 phút, không vượt hạn đơn P-09). */
    private LocalDateTime expiresAt;

    /** Thời điểm giao dịch hoàn tất (thành công hoặc thất bại); null khi Pending/Expired. */
    private LocalDateTime completedAt;

    /**
     * Constructor tối thiểu chỉ nhận khóa chính transactionId (bất biến).
     * Truyền 0 khi tạo giao dịch mới chưa lưu vào cơ sở dữ liệu.
     *
     * @param transactionId ID định danh giao dịch
     */
    public Transaction(int transactionId) {
        this.transactionId = transactionId;
        this.status = Status.PENDING;
        this.needsRefund = false;
    }

    /**
     * Constructor khởi tạo nhanh cho một lần thử thanh toán mới.
     */
    public Transaction(int transactionId, int orderId, long amount, PaymentMethod paymentMethod,
            Status status, LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.createdAt = createdAt;
        this.needsRefund = false;
    }

    /**
     * Constructor đầy đủ các trường, dùng khi DAO nạp dữ liệu từ bảng Transactions.
     */
    public Transaction(int transactionId, int orderId, long amount, PaymentMethod paymentMethod,
            String gatewayReference, Integer receivedByEmployeeId, Status status,
            String failureReason, boolean needsRefund, LocalDateTime refundedAt,
            Integer refundedByEmployeeId, String refundNote, LocalDateTime createdAt,
            LocalDateTime expiresAt, LocalDateTime completedAt) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.gatewayReference = gatewayReference;
        this.receivedByEmployeeId = receivedByEmployeeId;
        this.status = status;
        this.failureReason = failureReason;
        this.needsRefund = needsRefund;
        this.refundedAt = refundedAt;
        this.refundedByEmployeeId = refundedByEmployeeId;
        this.refundNote = refundNote;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.completedAt = completedAt;
    }

    // ===== Getter / Setter =====

    public int getTransactionId() {
        return transactionId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public void setGatewayReference(String gatewayReference) {
        this.gatewayReference = gatewayReference;
    }

    public Integer getReceivedByEmployeeId() {
        return receivedByEmployeeId;
    }

    public void setReceivedByEmployeeId(Integer receivedByEmployeeId) {
        this.receivedByEmployeeId = receivedByEmployeeId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isNeedsRefund() {
        return needsRefund;
    }

    public void setNeedsRefund(boolean needsRefund) {
        this.needsRefund = needsRefund;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }

    public Integer getRefundedByEmployeeId() {
        return refundedByEmployeeId;
    }

    public void setRefundedByEmployeeId(Integer refundedByEmployeeId) {
        this.refundedByEmployeeId = refundedByEmployeeId;
    }

    public String getRefundNote() {
        return refundNote;
    }

    public void setRefundNote(String refundNote) {
        this.refundNote = refundNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    // ===== Phương thức kiểm tra trạng thái tiện ích =====

    /** Kiểm tra giao dịch đang chờ xử lý. */
    public boolean isPending() {
        return this.status == Status.PENDING;
    }

    /** Kiểm tra giao dịch đã thanh toán thành công. */
    public boolean isSuccess() {
        return this.status == Status.SUCCESS;
    }

    /** Kiểm tra giao dịch thất bại. */
    public boolean isFailed() {
        return this.status == Status.FAILED;
    }

    /** Kiểm tra giao dịch đã hết hạn phiên thanh toán. */
    public boolean isExpired() {
        return this.status == Status.EXPIRED;
    }

    /** Kiểm tra giao dịch qua cổng online VNPay. */
    public boolean isOnlineGateway() {
        return this.paymentMethod == PaymentMethod.ONLINE_GATEWAY;
    }

    /** Kiểm tra giao dịch tiền mặt tại quầy. */
    public boolean isCash() {
        return this.paymentMethod == PaymentMethod.CASH;
    }

    /** Kiểm tra giao dịch chuyển khoản tại quầy. */
    public boolean isTransfer() {
        return this.paymentMethod == PaymentMethod.TRANSFER;
    }

    /** Kiểm tra giao dịch đã được Manager hoàn tiền ngoài hệ thống. */
    public boolean isRefundCompleted() {
        return this.needsRefund && this.refundedAt != null;
    }

    @Override
    public String toString() {
        return "Transaction{transactionId=" + transactionId
                + ", orderId=" + orderId
                + ", amount=" + amount
                + ", method=" + paymentMethod
                + ", status=" + status
                + ", needsRefund=" + needsRefund + '}';
    }

    // ===== Enum lồng theo quy định GBR-01 và ràng buộc CHECK DB =====

    /**
     * Phương thức thanh toán (CK_Transactions_Method).
     */
    public enum PaymentMethod {

        CASH("Cash"),
        TRANSFER("Transfer"),
        ONLINE_GATEWAY("OnlineGateway");

        private final String dbValue;

        PaymentMethod(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static PaymentMethod fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }
            for (PaymentMethod pm : values()) {
                if (pm.dbValue.equalsIgnoreCase(dbValue)) {
                    return pm;
                }
            }
            return null;
        }
    }

    /**
     * Trạng thái giao dịch (CK_Transactions_Status).
     */
    public enum Status {

        PENDING("Pending"),
        SUCCESS("Success"),
        FAILED("Failed"),
        EXPIRED("Expired");

        private final String dbValue;

        Status(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static Status fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }
            for (Status s : values()) {
                if (s.dbValue.equalsIgnoreCase(dbValue)) {
                    return s;
                }
            }
            return null;
        }
    }
}
