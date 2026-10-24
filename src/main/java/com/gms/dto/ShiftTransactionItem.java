package com.gms.dto;

import java.time.LocalDateTime;

/**
 * DTO đại diện cho 1 dòng giao dịch trong ca làm việc của Lễ tân (Màn #39 - UC-25).
 *
 * <p>Nơi tạo: {@link com.gms.dao.ShiftDAO#findTransactionsPage}.</p>
 * <p>Nơi dùng: Render danh sách giao dịch trong thẻ table/data-list tại {@code receptionist/shift.jsp}.</p>
 */
public class ShiftTransactionItem {

    // DAO: Orders.OrderId, mã định danh đơn hàng trong cơ sở dữ liệu.
    // JSP dùng để truyền vào form/modal xác nhận hủy hoặc xác nhận chuyển khoản.
    private final int orderId;

    // DAO: Orders.OrderCode, định dạng DH-yyyy-nnnn (VD: DH-2026-0032).
    // JSP hiển thị ở cột Mã hóa đơn.
    private final String orderCode;

    // DAO: Persons.FullName của khách hàng đứng tên đơn (HolderCustomer).
    // JSP hiển thị tên khách hàng trên dòng giao dịch.
    private final String customerName;

    // DAO: Persons.Phone của khách hàng.
    // JSP hiển thị số điện thoại phụ để Lễ tân đối chiếu.
    private final String customerPhone;

    // DAO: Orders.PackageNameSnapshot, tên gói tập lúc mua (DBR-50).
    // JSP hiển thị tên gói kèm badge hình thức tập.
    private final String packageName;

    // DAO: Orders.CreatedAt, thời điểm tạo đơn.
    // JSP định dạng HH:mm bằng gms:formatTime hoặc gms:formatDateTime.
    private final LocalDateTime createdAt;

    // DAO: Transactions.PaymentMethod, Cash (Tiền mặt) hoặc Transfer (Chuyển khoản).
    // JSP hiển thị badge phương thức thanh toán.
    private final String paymentMethod;

    // DAO: UnitPriceSnapshot - DiscountAmount, số tiền thực thu của đơn (VND).
    // JSP định dạng tiền tệ qua gms:formatMoney; nếu đơn hủy thì gạch ngang (strikethrough).
    private final long amount;

    // DAO: Orders.Status, Paid, Pending hoặc Cancelled.
    // JSP hiển thị badge trạng thái (Thành công, Chờ xác nhận, Đã hủy).
    private final String orderStatus;

    // DAO: Orders.CancelType, CancelledByReceptionist, PaymentTimeout...
    // JSP dùng để phân biệt loại hủy nếu đơn bị hủy.
    private final String cancelType;

    // DAO: Orders.CancelReason, lý do hủy đơn (tối thiểu 5 ký tự khi Lễ tân hủy).
    // JSP hiển thị giải trình khi đơn ở trạng thái Đã hủy.
    private final String cancelReason;

    // DAO: Orders.CancelledAt, thời điểm hủy đơn.
    // JSP hiển thị thời gian hủy nếu đơn bị hủy.
    private final LocalDateTime cancelledAt;

    // Service: Tính toán xem Lễ tân hiện tại có quyền hủy đơn này không (CBR-11, CBR-115).
    // True nếu: ca đang Open, đơn tại quầy, chưa hủy, và không phải hồ sơ của chính lễ tân.
    private final boolean canCancel;

    // Service: Tính toán xem đơn này có phải chuyển khoản đang chờ duyệt không (CBR-77).
    private final boolean canConfirmTransfer;

    public ShiftTransactionItem(int orderId, String orderCode, String customerName,
                                String customerPhone, String packageName,
                                LocalDateTime createdAt, String paymentMethod,
                                long amount, String orderStatus, String cancelType,
                                String cancelReason, LocalDateTime cancelledAt,
                                boolean canCancel, boolean canConfirmTransfer) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.packageName = packageName;
        this.createdAt = createdAt;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.orderStatus = orderStatus;
        this.cancelType = cancelType;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
        this.canCancel = canCancel;
        this.canConfirmTransfer = canConfirmTransfer;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getPackageName() {
        return packageName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public long getAmount() {
        return amount;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public String getCancelType() {
        return cancelType;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public boolean isCanCancel() {
        return canCancel;
    }

    public boolean isCanConfirmTransfer() {
        return canConfirmTransfer;
    }

    public boolean isPaid() {
        return "Paid".equalsIgnoreCase(orderStatus);
    }

    public boolean isPending() {
        return "Pending".equalsIgnoreCase(orderStatus);
    }

    public boolean isCancelled() {
        return "Cancelled".equalsIgnoreCase(orderStatus);
    }

    public boolean isCash() {
        return "Cash".equalsIgnoreCase(paymentMethod);
    }

    public boolean isTransfer() {
        return "Transfer".equalsIgnoreCase(paymentMethod);
    }
}
