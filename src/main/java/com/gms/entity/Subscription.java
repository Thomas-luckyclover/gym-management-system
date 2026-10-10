package com.gms.entity;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Quyền sử dụng dịch vụ / Thẻ hội viên của khách hàng, ánh xạ bảng
 * Subscriptions trong database (Cluster B).
 *
 * <p>
 * Quy tắc nghiệp vụ:
 * </p>
 * <ul>
 * <li>Sinh ra khi đơn hàng được thanh toán thành công (Order.Status = 'Paid')
 * theo quan hệ 1-1 (UQ_Subscriptions_OrderId).</li>
 * <li>Mọi hoạt động sử dụng thực tế (check-in quẹt thẻ vào cửa, đặt buổi PT)
 * đều tiêu thụ Subscription, không tiêu thụ Order.</li>
 * <li>Phân loại: Membership (tập gym theo hạn), DayPass (tập 1 ngày), PTSingle
 * (gói PT cá nhân), PTCouple (gói PT cặp đôi), SingleSession (buổi PT lẻ).</li>
 * <li>Vòng đời trạng thái: Active, Frozen (chỉ Membership), Used (chỉ
 * DayPass/SingleSession), Expired, Cancelled (CBR-55).</li>
 * <li>Snapshot thông số lúc bán: MaxSwapsSnapshot (P-08 × số tháng gói PT), MaxFreezesSnapshot
 * (P-01) để không bị ảnh hưởng nếu Manager đổi cấu hình sau này (GBR-04).</li>
 * </ul>
 *
 * @author Le Cong Minh
 */
public class Subscription {

    /** Khóa chính tự tăng, bất biến → final, không có setter. */
    private final int subscriptionId;

    /**
     * Đơn hàng đã thanh toán sinh ra quyền dùng này (FK -> Orders.OrderId, duy
     * nhất).
     */
    private int orderId;

    /** Khách hàng đứng tên sở hữu quyền dùng (FK -> Customers.CustomerId). */
    private int customerId;

    /** Loại quyền dùng: Membership, DayPass, PTSingle, PTCouple, SingleSession. */
    private Type subscriptionType;

    /**
     * Huấn luyện viên cá nhân phụ trách (FK -> Employees.EmployeeId); chỉ có với
     * gói PT hoặc buổi lẻ.
     */
    private Integer ptEmployeeId;

    /** Ngày bắt đầu có hiệu lực sử dụng (GBR-03). */
    private LocalDate startDate;

    /** Ngày cuối cùng còn hiệu lực sử dụng (GBR-03, EndDate >= StartDate). */
    private LocalDate endDate;

    /**
     * Khung giờ bắt đầu cố định của gói PT (chỉ áp dụng cho PTSingle / PTCouple,
     * CBR-43, CBR-52).
     */
    private LocalTime fixedStartTime;

    /**
     * Số buổi tập thực tế được tạo ra cho gói PT (chốt khi đơn Paid, BR-40, DBR-53;
     * buổi lẻ = 1).
     */
    private Integer generatedSessionCount;

    /** Số lần đổi lịch buổi tập đã sử dụng (mặc định 0). */
    private int swapsUsed;

    /** Hạn mức số lần đổi lịch tối đa chụp tại thời điểm bán = P-08 × số tháng của gói PT. */
    private Integer maxSwapsSnapshot;

    /** Số lần bảo lưu thẻ đã sử dụng (mặc định 0, chỉ áp dụng cho Membership). */
    private int freezeUsed;

    /** Hạn mức số lần bảo lưu tối đa chụp tại thời điểm bán (P-01). */
    private Integer maxFreezesSnapshot;

    /** Trạng thái quyền dùng: Active, Frozen, Used, Expired, Cancelled. */
    private Status status;

    /**
     * Constructor tối thiểu chỉ nhận khóa chính subscriptionId (bất biến).
     * Truyền 0 khi tạo mới trước khi lưu vào database.
     *
     * @param subscriptionId ID định danh quyền dùng
     */
    public Subscription(int subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.swapsUsed = 0;
        this.freezeUsed = 0;
        this.status = Status.ACTIVE;
    }

    /**
     * Constructor đầy đủ các trường, dùng khi DAO nạp dữ liệu từ bảng
     * Subscriptions.
     */
    public Subscription(int subscriptionId, int orderId, int customerId, Type subscriptionType,
            Integer ptEmployeeId, LocalDate startDate, LocalDate endDate, LocalTime fixedStartTime,
            Integer generatedSessionCount, int swapsUsed, Integer maxSwapsSnapshot, int freezeUsed,
            Integer maxFreezesSnapshot, Status status) {
        this.subscriptionId = subscriptionId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.subscriptionType = subscriptionType;
        this.ptEmployeeId = ptEmployeeId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.fixedStartTime = fixedStartTime;
        this.generatedSessionCount = generatedSessionCount;
        this.swapsUsed = swapsUsed;
        this.maxSwapsSnapshot = maxSwapsSnapshot;
        this.freezeUsed = freezeUsed;
        this.maxFreezesSnapshot = maxFreezesSnapshot;
        this.status = status;
    }

    // ===== Getter / Setter =====

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public Type getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(Type subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public Integer getPtEmployeeId() {
        return ptEmployeeId;
    }

    public void setPtEmployeeId(Integer ptEmployeeId) {
        this.ptEmployeeId = ptEmployeeId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalTime getFixedStartTime() {
        return fixedStartTime;
    }

    public void setFixedStartTime(LocalTime fixedStartTime) {
        this.fixedStartTime = fixedStartTime;
    }

    public Integer getGeneratedSessionCount() {
        return generatedSessionCount;
    }

    public void setGeneratedSessionCount(Integer generatedSessionCount) {
        this.generatedSessionCount = generatedSessionCount;
    }

    public int getSwapsUsed() {
        return swapsUsed;
    }

    public void setSwapsUsed(int swapsUsed) {
        this.swapsUsed = swapsUsed;
    }

    public Integer getMaxSwapsSnapshot() {
        return maxSwapsSnapshot;
    }

    public void setMaxSwapsSnapshot(Integer maxSwapsSnapshot) {
        this.maxSwapsSnapshot = maxSwapsSnapshot;
    }

    public int getFreezeUsed() {
        return freezeUsed;
    }

    public void setFreezeUsed(int freezeUsed) {
        this.freezeUsed = freezeUsed;
    }

    public Integer getMaxFreezesSnapshot() {
        return maxFreezesSnapshot;
    }

    public void setMaxFreezesSnapshot(Integer maxFreezesSnapshot) {
        this.maxFreezesSnapshot = maxFreezesSnapshot;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    // ===== Phương thức kiểm tra nghiệp vụ tiện ích =====

    /** Kiểm tra quyền dùng đang có hiệu lực hoạt động. */
    public boolean isActive() {
        return this.status == Status.ACTIVE;
    }

    /** Kiểm tra gói tập đang trong giai đoạn bảo lưu. */
    public boolean isFrozen() {
        return this.status == Status.FROZEN;
    }

    /** Kiểm tra gói đã sử dụng hết lượt (DayPass / SingleSession). */
    public boolean isUsed() {
        return this.status == Status.USED;
    }

    /** Kiểm tra quyền dùng đã hết hạn thời gian. */
    public boolean isExpired() {
        return this.status == Status.EXPIRED;
    }

    /** Kiểm tra quyền dùng đã bị hủy bỏ. */
    public boolean isCancelled() {
        return this.status == Status.CANCELLED;
    }

    /** Kiểm tra đây có phải dịch vụ kèm PT (PTSingle, PTCouple, SingleSession). */
    public boolean isPtService() {
        return this.subscriptionType == Type.PT_SINGLE
                || this.subscriptionType == Type.PT_COUPLE
                || this.subscriptionType == Type.SINGLE_SESSION;
    }

    /**
     * Kiểm tra thẻ tập còn lượt bảo lưu và đủ điều kiện bảo lưu không (BR-21,
     * BR-24).
     */
    public boolean canFreeze() {
        boolean isMembership = this.subscriptionType == Type.MEMBERSHIP;
        boolean hasSnapshot = this.maxFreezesSnapshot != null;
        boolean hasRemainingTimes = hasSnapshot && this.freezeUsed < this.maxFreezesSnapshot;
        return isMembership && isActive() && hasRemainingTimes;
    }

    /**
     * Kiểm tra gói PT còn lượt đổi lịch hợp lệ không (BR-73).
     */
    public boolean canSwap() {
        boolean isPtPackage = this.subscriptionType == Type.PT_SINGLE
                || this.subscriptionType == Type.PT_COUPLE;
        boolean hasSnapshot = this.maxSwapsSnapshot != null;
        boolean hasRemainingTimes = hasSnapshot && this.swapsUsed < this.maxSwapsSnapshot;
        return isPtPackage && isActive() && hasRemainingTimes;
    }

    /**
     * Lấy số lượt đổi lịch còn lại của gói PT.
     */
    public int getRemainingSwaps() {
        if (maxSwapsSnapshot == null) {
            return 0;
        }
        return Math.max(0, maxSwapsSnapshot - swapsUsed);
    }

    /**
     * Lấy số lượt bảo lưu còn lại của thẻ Membership.
     */
    public int getRemainingFreezes() {
        if (maxFreezesSnapshot == null) {
            return 0;
        }
        return Math.max(0, maxFreezesSnapshot - freezeUsed);
    }

    @Override
    public String toString() {
        return "Subscription{subscriptionId=" + subscriptionId
                + ", customerId=" + customerId
                + ", type=" + subscriptionType
                + ", startDate=" + startDate
                + ", endDate=" + endDate
                + ", status=" + status + '}';
    }

    // ===== Enum lồng theo chuẩn GBR-01 & Ràng buộc CHECK DB =====

    /**
     * Loại quyền sử dụng (CK_Subscriptions_Type).
     */
    public enum Type {

        MEMBERSHIP("Membership"),
        DAY_PASS("DayPass"),
        PT_SINGLE("PTSingle"),
        PT_COUPLE("PTCouple"),
        SINGLE_SESSION("SingleSession");

        private final String dbValue;

        Type(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static Type fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }
            for (Type t : values()) {
                if (t.dbValue.equalsIgnoreCase(dbValue)) {
                    return t;
                }
            }
            return null;
        }
    }

    /**
     * Trạng thái quyền sử dụng (CK_Subscriptions_Status,
     * CK_Subscriptions_StatusByType).
     */
    public enum Status {

        ACTIVE("Active"),
        FROZEN("Frozen"),
        USED("Used"),
        EXPIRED("Expired"),
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
}
