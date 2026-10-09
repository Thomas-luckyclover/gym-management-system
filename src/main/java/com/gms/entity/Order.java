package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Đơn hàng mua gói tập hoặc buổi PT lẻ, ánh xạ bảng Orders trong database
 * (Cluster B).
 *
 * <p>
 * Quy tắc nghiệp vụ:
 * </p>
 * <ul>
 * <li>Mỗi đơn mua đúng 1 gói dịch vụ (Membership, DayPass, PTSingle, PTCouple,
 * SingleSession).</li>
 * <li>Snapshot thông tin gói lúc mua (giá, mô tả, quyền lợi, VAT) để bảo toàn
 * lịch sử doanh thu khi gói đổi (DBR-50, DBR-57).</li>
 * <li>Áp dụng tối đa 1 voucher (BR-42); chỉ giảm giá khi có voucher
 * (CK_Orders_VoucherDiscount).</li>
 * <li>Kênh bán: Online (khách tự mua qua VNPay, có hạn giữ đơn PaymentDueAt
 * P-09) hoặc Counter (Lễ tân lập tại quầy, gắn ShiftId).</li>
 * <li>Vòng đời trạng thái: Pending → Paid, Pending → Cancelled, Paid →
 * Cancelled (chỉ do Lễ tân hủy tại quầy CBR-11). Cancelled là trạng thái cuối
 * cùng (DBR-39, DBR-40).</li>
 * </ul>
 *
 * @author Le Cong Minh
 */
public class Order {

    /** Khóa chính tự tăng, bất biến → final, không có setter. */
    private final int orderId;

    /** Mã đơn định dạng DH-yyyy-nnnn (VD: DH-2026-0001), bất biến. */
    private String orderCode;

    /** Khách hàng đứng tên đơn hàng (FK -> Customers.CustomerId). */
    private int holderCustomerId;

    /**
     * Khách hàng đi kèm (FK -> Customers.CustomerId), CHỈ dùng cho gói PTCouple,
     * còn lại là null.
     */
    private Integer partnerCustomerId;

    /** Gói dịch vụ được mua (FK -> Packages.PackageId). */
    private int packageId;

    /**
     * Mã giảm giá áp dụng (FK -> Vouchers.VoucherId), tối đa 1 voucher hoặc null.
     */
    private Integer voucherId;

    /**
     * Nhân viên lập đơn (FK -> Employees.EmployeeId); null với đơn Online, bắt buộc
     * với đơn Counter.
     */
    private Integer createdByEmployeeId;

    /**
     * Ca trực nhận đơn (FK -> Shifts.ShiftId); null với đơn Online, bắt buộc với
     * đơn Counter.
     */
    private Integer shiftId;

    /** Kênh bán hàng: Counter (tại quầy) hoặc Online (tự phục vụ qua VNPay). */
    private Channel channel;

    /** Tên gói chụp lại tại thời điểm lập đơn. */
    private String packageNameSnapshot;

    /** Mô tả gói chụp lại tại thời điểm lập đơn. */
    private String packageDescriptionSnapshot;

    /** Danh sách quyền lợi gói chụp lại tại thời điểm lập đơn (DBR-50). */
    private String packageBenefitsSnapshot;

    /** Giá niêm yết của gói lúc mua, đã bao gồm VAT (VND). */
    private long unitPriceSnapshot;

    /** Số tiền được giảm từ voucher (VND), mặc định 0. */
    private long discountAmount;

    /** Thuế suất VAT (%) tại thời điểm mua (P-10). */
    private int vatRateSnapshot;

    /**
     * Tiền thuế VAT đã gồm trong đơn: (UnitPrice - Discount) * VAT / (100 + VAT)
     * (DBR-57).
     */
    private long vatAmount;

    /**
     * Trạng thái đơn hàng: Pending (chờ thanh toán), Paid (đã thanh toán),
     * Cancelled (đã hủy).
     */
    private Status status;

    /** Hạn chót thanh toán đơn online (CreatedAt + P-09); null với đơn tại quầy. */
    private LocalDateTime paymentDueAt;

    /** Thời điểm tạo đơn hàng (bất biến sau khi ghi nhận). */
    private LocalDateTime createdAt;

    /** Thời điểm thanh toán thành công; null khi đơn chưa thanh toán. */
    private LocalDateTime paidAt;

    /**
     * Loại lý do hủy đơn: PaymentTimeout, ReplacedByNewOrder, InvalidPayment,
     * CancelledByReceptionist.
     */
    private CancelType cancelType;

    /** Thời điểm hủy đơn; null nếu đơn chưa bị hủy. */
    private LocalDateTime cancelledAt;

    /**
     * Lý do hủy chi tiết (bắt buộc tối thiểu 5 ký tự khi Lễ tân hủy tại quầy
     * GBR-09).
     */
    private String cancelReason;

    /**
     * Constructor tối thiểu chỉ nhận khóa chính orderId (bất biến).
     * Truyền 0 khi tạo đối tượng đơn hàng mới chưa lưu vào cơ sở dữ liệu.
     *
     * @param orderId ID định danh đơn hàng
     */
    public Order(int orderId) {
        this.orderId = orderId;
        this.status = Status.PENDING;
        this.discountAmount = 0L;
    }

    /**
     * Constructor đầy đủ các trường, dùng khi DAO nạp dữ liệu từ bảng Orders.
     */
    public Order(int orderId, String orderCode, int holderCustomerId, Integer partnerCustomerId,
            int packageId, Integer voucherId, Integer createdByEmployeeId, Integer shiftId,
            Channel channel, String packageNameSnapshot, String packageDescriptionSnapshot,
            String packageBenefitsSnapshot, long unitPriceSnapshot, long discountAmount,
            int vatRateSnapshot, long vatAmount, Status status, LocalDateTime paymentDueAt,
            LocalDateTime createdAt, LocalDateTime paidAt, CancelType cancelType,
            LocalDateTime cancelledAt, String cancelReason) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.holderCustomerId = holderCustomerId;
        this.partnerCustomerId = partnerCustomerId;
        this.packageId = packageId;
        this.voucherId = voucherId;
        this.createdByEmployeeId = createdByEmployeeId;
        this.shiftId = shiftId;
        this.channel = channel;
        this.packageNameSnapshot = packageNameSnapshot;
        this.packageDescriptionSnapshot = packageDescriptionSnapshot;
        this.packageBenefitsSnapshot = packageBenefitsSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.discountAmount = discountAmount;
        this.vatRateSnapshot = vatRateSnapshot;
        this.vatAmount = vatAmount;
        this.status = status;
        this.paymentDueAt = paymentDueAt;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
        this.cancelType = cancelType;
        this.cancelledAt = cancelledAt;
        this.cancelReason = cancelReason;
    }

    // ===== Getter / Setter =====

    public int getOrderId() {
        return orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public int getHolderCustomerId() {
        return holderCustomerId;
    }

    public void setHolderCustomerId(int holderCustomerId) {
        this.holderCustomerId = holderCustomerId;
    }

    public Integer getPartnerCustomerId() {
        return partnerCustomerId;
    }

    public void setPartnerCustomerId(Integer partnerCustomerId) {
        this.partnerCustomerId = partnerCustomerId;
    }

    public int getPackageId() {
        return packageId;
    }

    public void setPackageId(int packageId) {
        this.packageId = packageId;
    }

    public Integer getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(Integer voucherId) {
        this.voucherId = voucherId;
    }

    public Integer getCreatedByEmployeeId() {
        return createdByEmployeeId;
    }

    public void setCreatedByEmployeeId(Integer createdByEmployeeId) {
        this.createdByEmployeeId = createdByEmployeeId;
    }

    public Integer getShiftId() {
        return shiftId;
    }

    public void setShiftId(Integer shiftId) {
        this.shiftId = shiftId;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getPackageNameSnapshot() {
        return packageNameSnapshot;
    }

    public void setPackageNameSnapshot(String packageNameSnapshot) {
        this.packageNameSnapshot = packageNameSnapshot;
    }

    public String getPackageDescriptionSnapshot() {
        return packageDescriptionSnapshot;
    }

    public void setPackageDescriptionSnapshot(String packageDescriptionSnapshot) {
        this.packageDescriptionSnapshot = packageDescriptionSnapshot;
    }

    public String getPackageBenefitsSnapshot() {
        return packageBenefitsSnapshot;
    }

    public void setPackageBenefitsSnapshot(String packageBenefitsSnapshot) {
        this.packageBenefitsSnapshot = packageBenefitsSnapshot;
    }

    public long getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public void setUnitPriceSnapshot(long unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(long discountAmount) {
        this.discountAmount = discountAmount;
    }

    public int getVatRateSnapshot() {
        return vatRateSnapshot;
    }

    public void setVatRateSnapshot(int vatRateSnapshot) {
        this.vatRateSnapshot = vatRateSnapshot;
    }

    public long getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(long vatAmount) {
        this.vatAmount = vatAmount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getPaymentDueAt() {
        return paymentDueAt;
    }

    public void setPaymentDueAt(LocalDateTime paymentDueAt) {
        this.paymentDueAt = paymentDueAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public CancelType getCancelType() {
        return cancelType;
    }

    public void setCancelType(CancelType cancelType) {
        this.cancelType = cancelType;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    // ===== Phương thức tính toán & kiểm tra trạng thái tiện ích =====

    /**
     * Tính số tiền thực tế khách cần thanh toán sau khi trừ giảm giá từ voucher
     * (VND).
     * 
     * @return UnitPriceSnapshot - DiscountAmount
     */
    public long getFinalAmount() {
        long finalPrice = unitPriceSnapshot - discountAmount;
        return Math.max(finalPrice, 0L);
    }

    /** Kiểm tra đơn đang ở trạng thái chờ thanh toán. */
    public boolean isPending() {
        return this.status == Status.PENDING;
    }

    /** Kiểm tra đơn đã hoàn tất thanh toán. */
    public boolean isPaid() {
        return this.status == Status.PAID;
    }

    /** Kiểm tra đơn đã bị hủy. */
    public boolean isCancelled() {
        return this.status == Status.CANCELLED;
    }

    /** Kiểm tra đơn được lập qua kênh tự phục vụ online. */
    public boolean isOnline() {
        return this.channel == Channel.ONLINE;
    }

    /** Kiểm tra đơn được lập tại quầy tiếp tân. */
    public boolean isCounter() {
        return this.channel == Channel.COUNTER;
    }

    @Override
    public String toString() {
        return "Order{orderId=" + orderId
                + ", orderCode='" + orderCode + '\''
                + ", holderCustomerId=" + holderCustomerId
                + ", packageId=" + packageId
                + ", channel=" + channel
                + ", finalAmount=" + getFinalAmount()
                + ", status=" + status + '}';
    }

    // ===== Enum lồng theo quy định GBR-01 và ràng buộc CHECK DB =====

    /**
     * Kênh bán hàng (CK_Orders_Channel).
     */
    public enum Channel {

        COUNTER("Counter"),
        ONLINE("Online");

        private final String dbValue;

        Channel(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static Channel fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }
            for (Channel c : values()) {
                if (c.dbValue.equalsIgnoreCase(dbValue)) {
                    return c;
                }
            }
            return null;
        }
    }

    /**
     * Trạng thái đơn hàng (DBR-39, DBR-40, CK_Orders_Status).
     */
    public enum Status {

        PENDING("Pending"),
        PAID("Paid"),
        CANCELLED("Cancelled");

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

    /**
     * Lý do / loại hủy đơn hàng (DBR-43, CK_Orders_CancelType).
     */
    public enum CancelType {

        PAYMENT_TIMEOUT("PaymentTimeout"),
        REPLACED_BY_NEW_ORDER("ReplacedByNewOrder"),
        INVALID_PAYMENT("InvalidPayment"),
        CANCELLED_BY_RECEPTIONIST("CancelledByReceptionist");

        private final String dbValue;

        CancelType(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static CancelType fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }
            for (CancelType ct : values()) {
                if (ct.dbValue.equalsIgnoreCase(dbValue)) {
                    return ct;
                }
            }
            return null;
        }
    }
}
