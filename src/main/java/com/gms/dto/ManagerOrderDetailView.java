package com.gms.dto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * DTO đại diện cho thông tin chi tiết đầy đủ của một đơn hàng trong modal chi tiết (Màn #41 - UC-26).
 *
 * <p>Nơi tạo: {@link com.gms.dao.TransactionDAO#findOrderDetailForManager}.</p>
 * <p>Nơi dùng: Render thông tin đơn hàng và các lần thử thanh toán trong modal chi tiết tại {@code manager/transaction-list.jsp}.</p>
 *
 * @author Le Cong Minh
 */
public class ManagerOrderDetailView {

    // DAO: Orders.OrderId
    private final int orderId;

    // DAO: Orders.OrderCode
    private final String orderCode;

    // DAO: Orders.Channel (Counter | Online)
    private final String channel;

    // DAO: Orders.CreatedAt
    private final LocalDateTime createdAt;

    // DAO: Orders.PaymentDueAt
    private final LocalDateTime paymentDueAt;

    // DAO: Orders.PaidAt
    private final LocalDateTime paidAt;

    // DAO: Orders.Status
    private final String status;

    // DAO: Orders.CancelType
    private final String cancelType;

    // DAO: Orders.CancelledAt
    private final LocalDateTime cancelledAt;

    // DAO: Orders.CancelReason
    private final String cancelReason;

    // DAO: Persons.FullName của Lễ tân tạo đơn (nếu Counter)
    private final String createdByEmployeeName;

    // DAO: Shifts.ShiftId
    private final Integer shiftId;

    // DAO: Persons.FullName của khách hàng chính
    private final String customerName;

    // DAO: Persons.Phone
    private final String customerPhone;

    // DAO: Persons.Email
    private final String customerEmail;

    // DAO: Persons.FullName của người đi kèm (nếu gói couple)
    private final String partnerName;

    // DAO: Orders.PackageNameSnapshot
    private final String packageName;

    // DAO: Orders.PackageDescriptionSnapshot
    private final String packageDescription;

    // DAO: Orders.PackageBenefitsSnapshot
    private final String packageBenefits;

    // DAO: Orders.UnitPriceSnapshot
    private final long unitPrice;

    // DAO: Orders.DiscountAmount
    private final long discountAmount;

    // DAO: Vouchers.VoucherCode
    private final String voucherCode;

    // DAO: Orders.VatRateSnapshot
    private final int vatRate;

    // DAO: Orders.VatAmount
    private final long vatAmount;

    // Service: (unitPrice - discountAmount)
    private final long totalPayable;

    // Service: (totalPayable - vatAmount)
    private final long preTaxAmount;

    // DAO: Danh sách các lần thử thanh toán của đơn
    private final List<TransactionAttemptView> attempts;

    public ManagerOrderDetailView(int orderId, String orderCode, String channel,
                                  LocalDateTime createdAt, LocalDateTime paymentDueAt,
                                  LocalDateTime paidAt, String status,
                                  String cancelType, LocalDateTime cancelledAt,
                                  String cancelReason, String createdByEmployeeName,
                                  Integer shiftId, String customerName,
                                  String customerPhone, String customerEmail,
                                  String partnerName, String packageName,
                                  String packageDescription, String packageBenefits,
                                  long unitPrice, long discountAmount,
                                  String voucherCode, int vatRate, long vatAmount,
                                  long totalPayable, long preTaxAmount,
                                  List<TransactionAttemptView> attempts) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.channel = channel;
        this.createdAt = createdAt;
        this.paymentDueAt = paymentDueAt;
        this.paidAt = paidAt;
        this.status = status;
        this.cancelType = cancelType;
        this.cancelledAt = cancelledAt;
        this.cancelReason = cancelReason;
        this.createdByEmployeeName = createdByEmployeeName;
        this.shiftId = shiftId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerEmail = customerEmail;
        this.partnerName = partnerName;
        this.packageName = packageName;
        this.packageDescription = packageDescription;
        this.packageBenefits = packageBenefits;
        this.unitPrice = unitPrice;
        this.discountAmount = discountAmount;
        this.voucherCode = voucherCode;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
        this.totalPayable = totalPayable;
        this.preTaxAmount = preTaxAmount;
        this.attempts = attempts != null ? Collections.unmodifiableList(attempts) : Collections.emptyList();
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

    public LocalDateTime getPaymentDueAt() {
        return paymentDueAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getStatus() {
        return status;
    }

    public String getCancelType() {
        return cancelType;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public String getCreatedByEmployeeName() {
        return createdByEmployeeName;
    }

    public Integer getShiftId() {
        return shiftId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getPackageDescription() {
        return packageDescription;
    }

    public String getPackageBenefits() {
        return packageBenefits;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public int getVatRate() {
        return vatRate;
    }

    public long getVatAmount() {
        return vatAmount;
    }

    public long getTotalPayable() {
        return totalPayable;
    }

    public long getPreTaxAmount() {
        return preTaxAmount;
    }

    public List<TransactionAttemptView> getAttempts() {
        return attempts;
    }

    /** Tìm giao dịch cần hoàn tiền mà chưa hoàn (nếu có). */
    public TransactionAttemptView getPendingRefundAttempt() {
        for (TransactionAttemptView attempt : attempts) {
            if (attempt.isNeedsRefund() && attempt.getRefundedAt() == null) {
                return attempt;
            }
        }
        return null;
    }

    /** Tìm giao dịch đã được hoàn tiền (nếu có). */
    public TransactionAttemptView getCompletedRefundAttempt() {
        for (TransactionAttemptView attempt : attempts) {
            if (attempt.isNeedsRefund() && attempt.getRefundedAt() != null) {
                return attempt;
            }
        }
        return null;
    }
}
