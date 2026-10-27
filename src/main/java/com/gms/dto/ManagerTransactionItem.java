package com.gms.dto;

import java.time.LocalDateTime;

/**
 * DTO đại diện cho một dòng đơn hàng và giao dịch trong màn hình Quản lý giao dịch toàn hệ thống (Màn #41 - UC-26).
 *
 * <p>Nơi tạo: {@link com.gms.dao.TransactionDAO#findManagerTransactionsPage}.</p>
 * <p>Nơi dùng: Hiển thị bảng danh sách giao dịch tại {@code manager/transaction-list.jsp}.</p>
 *
 * @author Le Cong Minh
 */
public class ManagerTransactionItem {

    // DAO: Orders.OrderId, mã định danh đơn hàng trong CSDL.
    // JSP dùng để mở modal chi tiết đơn hoặc link xử lý.
    private final int orderId;

    // DAO: Orders.OrderCode, định dạng DH-yyyy-nnnn (VD: DH-2026-0001).
    // JSP hiển thị tại cột Mã đơn hàng.
    private final String orderCode;

    // DAO: Orders.Channel, kênh bán: Counter hoặc Online (DBR-39, CBR-105).
    // JSP hiển thị badge kênh bán (Tại quầy / Trực tuyến).
    private final String channel;

    // DAO: Orders.CreatedAt, thời điểm tạo đơn hàng.
    // JSP hiển thị định dạng dd/MM/yyyy HH:mm qua gms:formatDateTime.
    private final LocalDateTime createdAt;

    // DAO: Persons.FullName của khách hàng đứng tên đơn (HolderCustomer).
    // JSP hiển thị tại cột Khách hàng.
    private final String customerName;

    // DAO: Persons.Phone của khách hàng.
    // JSP hiển thị số điện thoại phụ để tiện tra cứu.
    private final String customerPhone;

    // DAO: Orders.PackageNameSnapshot, tên gói tập tại thời điểm mua (DBR-50).
    // JSP hiển thị tại cột Gói tập.
    private final String packageName;

    // DAO: Vouchers.VoucherCode, mã giảm giá áp dụng (nếu có, tối đa 1 voucher BR-42).
    // JSP hiển thị dạng badge nhỏ cạnh tên gói nếu có voucher.
    private final String voucherCode;

    // DAO: Orders.UnitPriceSnapshot, đơn giá niêm yết của gói (đã gồm VAT).
    // JSP dùng để hiển thị chi tiết giá hoặc tính toán.
    private final long unitPrice;

    // DAO: Orders.DiscountAmount, số tiền giảm giá từ voucher (VND).
    // JSP hiển thị số tiền được giảm.
    private final long discountAmount;

    // Service: (unitPrice - discountAmount), số tiền thực phải thanh toán (VND, DBR-57).
    // JSP hiển thị tại cột Tổng tiền; nếu đơn bị hủy thì gạch ngang (strikethrough).
    private final long totalAmount;

    // DAO: Orders.VatRateSnapshot, phần trăm thuế VAT lúc tạo đơn (mặc định P-10).
    // JSP hiển thị tỷ lệ VAT.
    private final int vatRate;

    // DAO: Orders.VatAmount, tiền thuế VAT cấu thành trong tổng tiền (DBR-57).
    // JSP hiển thị số tiền VAT tương ứng.
    private final long vatAmount;

    // DAO: Transactions.PaymentMethod (hoặc giao dịch thành công/mới nhất), Cash | Transfer | OnlineGateway.
    // JSP hiển thị badge phương thức thanh toán.
    private final String paymentMethod;

    // DAO: Orders.Status, trạng thái đơn: Paid | Pending | Cancelled (DBR-39).
    // JSP hiển thị badge trạng thái đơn hàng.
    private final String orderStatus;

    // DAO: Orders.CancelType, loại hủy đơn (PaymentTimeout, ReplacedByNewOrder, InvalidPayment, CancelledByReceptionist).
    // JSP hiển thị lý do/loại hủy nếu đơn bị hủy.
    private final String cancelType;

    // DAO: Orders.CancelReason, lý do hủy đơn (bắt buộc khi Lễ tân hủy CBR-11).
    // JSP hiển thị tooltip hoặc trong modal chi tiết.
    private final String cancelReason;

    // DAO: Transactions.TransactionId của giao dịch cần hoàn tiền (nếu có).
    // JSP dùng làm định danh khi Manager mở modal bấm hoàn tiền ngoài hệ thống.
    private final Integer refundableTransactionId;

    // DAO: Transactions.NeedsRefund, cờ đánh dấu cần hoàn tiền ngoài hệ thống (CBR-72).
    // JSP hiển thị badge "Cần hoàn tiền" nếu NeedsRefund = 1 và chưa hoàn.
    private final boolean needsRefund;

    // DAO: Transactions.RefundedAt, thời điểm Manager xác nhận đã hoàn tiền ngoài hệ thống (BR-47).
    // JSP hiển thị badge "Đã hoàn tiền" kèm thời gian khi RefundedAt != null.
    private final LocalDateTime refundedAt;

    // DAO: Persons.FullName của Manager thực hiện xác nhận hoàn tiền.
    // JSP hiển thị tên người duyệt hoàn trong chi tiết.
    private final String refundedByName;

    // DAO: Transactions.RefundNote, ghi chú hoàn tiền (số tài khoản nhận, mã tham chiếu...).
    // JSP hiển thị ghi chú hoàn tiền.
    private final String refundNote;

    public ManagerTransactionItem(int orderId, String orderCode, String channel,
                                  LocalDateTime createdAt, String customerName,
                                  String customerPhone, String packageName,
                                  String voucherCode, long unitPrice,
                                  long discountAmount, long totalAmount,
                                  int vatRate, long vatAmount,
                                  String paymentMethod, String orderStatus,
                                  String cancelType, String cancelReason,
                                  Integer refundableTransactionId,
                                  boolean needsRefund, LocalDateTime refundedAt,
                                  String refundedByName, String refundNote) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.channel = channel;
        this.createdAt = createdAt;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.packageName = packageName;
        this.voucherCode = voucherCode;
        this.unitPrice = unitPrice;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
        this.paymentMethod = paymentMethod;
        this.orderStatus = orderStatus;
        this.cancelType = cancelType;
        this.cancelReason = cancelReason;
        this.refundableTransactionId = refundableTransactionId;
        this.needsRefund = needsRefund;
        this.refundedAt = refundedAt;
        this.refundedByName = refundedByName;
        this.refundNote = refundNote;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getChannel() {
        return channel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerFullName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public int getVatRate() {
        return vatRate;
    }

    public long getVatAmount() {
        return vatAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
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

    public Integer getRefundableTransactionId() {
        return refundableTransactionId;
    }

    public Integer getTransactionId() {
        return refundableTransactionId;
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

    public String getRefundedByEmployeeName() {
        return refundedByName;
    }

    public String getRefundNote() {
        return refundNote;
    }

    // ===== Helper methods phục vụ hiển thị JSP =====

    /** Kiểm tra đơn bán trực tuyến trên website. */
    public boolean isOnline() {
        return "Online".equalsIgnoreCase(channel);
    }

    /** Kiểm tra đơn tạo trực tiếp tại quầy Lễ tân. */
    public boolean isCounter() {
        return "Counter".equalsIgnoreCase(channel);
    }

    /** Kiểm tra đơn đã thanh toán thành công. */
    public boolean isPaid() {
        return "Paid".equalsIgnoreCase(orderStatus);
    }

    /** Kiểm tra đơn đang chờ thanh toán. */
    public boolean isPending() {
        return "Pending".equalsIgnoreCase(orderStatus);
    }

    /** Kiểm tra đơn đã bị hủy. */
    public boolean isCancelled() {
        return "Cancelled".equalsIgnoreCase(orderStatus);
    }

    /**
     * Kiểm tra giao dịch có cần hoàn tiền thủ công và chưa được hoàn (CBR-72).
     */
    public boolean isRefundPending() {
        return needsRefund && refundedAt == null;
    }

    /**
     * Kiểm tra giao dịch đã được Manager xác nhận hoàn tiền thành công (BR-47).
     */
    public boolean isRefundCompleted() {
        return needsRefund && refundedAt != null;
    }
}
