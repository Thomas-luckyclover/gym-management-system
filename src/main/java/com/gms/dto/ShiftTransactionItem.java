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
    private boolean canCancel;

    // Service: Tính toán xem đơn này có phải chuyển khoản đang chờ duyệt không (CBR-77).
    private boolean canConfirmTransfer;

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

    public void setCanCancel(boolean canCancel) {
        this.canCancel = canCancel;
    }

    public boolean isCanConfirmTransfer() {
        return canConfirmTransfer;
    }

    public void setCanConfirmTransfer(boolean canConfirmTransfer) {
        this.canConfirmTransfer = canConfirmTransfer;
    }

    // GETTER TỰ TÍNH — không có field/setter "paid"; = orderStatus == "Paid"; JSP: receptionist/shift.jsp:341
    public boolean isPaid() {
        return "Paid".equalsIgnoreCase(orderStatus);
    }

    // GETTER TỰ TÍNH — không có field/setter "pending"; = orderStatus == "Pending"; JSP: receptionist/shift.jsp:345
    public boolean isPending() {
        return "Pending".equalsIgnoreCase(orderStatus);
    }

    // GETTER TỰ TÍNH — không có field/setter "cancelled"; = orderStatus == "Cancelled"; JSP: receptionist/shift.jsp:328 …
    public boolean isCancelled() {
        return "Cancelled".equalsIgnoreCase(orderStatus);
    }

    // GETTER TỰ TÍNH — không có field/setter "cash"; = paymentMethod == "Cash"; JSP: receptionist/shift.jsp:359
    public boolean isCash() {
        return "Cash".equalsIgnoreCase(paymentMethod);
    }

    // GETTER TỰ TÍNH — không có field/setter "transfer"; = paymentMethod == "Transfer"; JSP: không dùng (chỉ Java/test)
    public boolean isTransfer() {
        return "Transfer".equalsIgnoreCase(paymentMethod);
    }

    /**
     * Tên hiển thị tiếng Việt của trạng thái đơn hàng.
     */
    // GETTER TỰ TÍNH — không có field/setter "statusDisplayName"; = orderStatus đổi sang chữ tiếng Việt; JSP: không dùng (chỉ Java/test)
    public String getStatusDisplayName() {
        if (isPaid()) return "Đã thanh toán";
        if (isPending()) return "Chờ xác nhận";
        if (isCancelled()) return "Đã hủy";
        return orderStatus != null ? orderStatus : "Chưa rõ";
    }

    /**
     * Tên hiển thị tiếng Việt của phương thức thanh toán.
     */
    // GETTER TỰ TÍNH — không có field/setter "paymentMethodDisplayName"; = paymentMethod đổi sang chữ tiếng Việt; JSP: không dùng (chỉ Java/test)
    public String getPaymentMethodDisplayName() {
        if (isCash()) return "Tiền mặt";
        if (isTransfer()) return "Chuyển khoản";
        return paymentMethod != null ? paymentMethod : "Khác";
    }

    /**
     * Class CSS tương ứng cho badge trạng thái theo Design System GBR-28.
     */
    // GETTER TỰ TÍNH — không có field/setter "statusBadgeClass"; = orderStatus đổi sang class huy hiệu màu; JSP: không dùng (chỉ Java/test)
    public String getStatusBadgeClass() {
        if (isPaid()) return "badge-ok";
        if (isPending()) return "badge-warn";
        if (isCancelled()) return "badge-danger";
        return "badge-muted";
    }

    /**
     * Kiểm tra đơn có phải bị hủy bởi Lễ tân tại quầy hay không (CBR-11).
     */
    // GETTER TỰ TÍNH — không có field/setter "cancelledByReceptionist"; = cancelType == "CancelledByReceptionist"; JSP: không dùng (chỉ Java/test)
    public boolean isCancelledByReceptionist() {
        return "CancelledByReceptionist".equalsIgnoreCase(cancelType);
    }
}
