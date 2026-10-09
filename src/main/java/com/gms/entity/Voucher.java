package com.gms.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mã giảm giá áp dụng khi mua gói tập hoặc vé ngày, ánh xạ bảng Vouchers trong
 * database (Cluster B).
 *
 * <p>
 * Quy tắc nghiệp vụ:
 * </p>
 * <ul>
 * <li>Mỗi đơn hàng chỉ được dùng tối đa 1 voucher (BR-42).</li>
 * <li>Voucher KHÔNG áp dụng cho gói PT (PTSingle, PTCouple) và buổi PT lẻ
 * (SingleSession) (CBR-76).</li>
 * <li>Điều kiện hợp lệ (CBR-73): Trong thời hạn (StartDate đến EndDate), còn
 * lượt dùng (UsedCount &lt; MaxUsage),
 * giá đơn trước giảm &gt;= MinOrderValue, đúng phạm vi áp dụng và khách chưa
 * vượt hạn mức cá nhân (CBR-74).</li>
 * <li>Khi áp dụng thành công: UsedCount tăng 1 (BR-43); khi đơn bị hủy hoặc gỡ
 * bỏ: hoàn lại 1 lượt dùng (BR-44, BR-45).</li>
 * <li>Voucher đã sử dụng ít nhất 1 lần thì chỉ được tắt (IsActive = 0), không
 * được xóa khỏi hệ thống (CBR-98).</li>
 * </ul>
 *
 * @author Le Cong Minh
 */
public class Voucher {

    /** Khóa chính tự tăng, bất biến → final, không có setter. */
    private final int voucherId;

    /** Mã voucher duy nhất, 4–20 ký tự chữ hoa và số A-Z 0-9 (DBR-10). */
    private String voucherCode;

    /** Loại giảm giá: Percent (% giá trị gói) hoặc Amount (số tiền cố định VND). */
    private DiscountType discountType;

    /** Giá trị giảm: 1–100 nếu là Percent; số tiền VND (&gt; 0) nếu là Amount. */
    private int discountValue;

    /** Ngày bắt đầu có hiệu lực. */
    private LocalDate startDate;

    /**
     * Ngày cuối cùng còn hiệu lực (EndDate &gt;= StartDate, GBR-03 tính cả ngày
     * này).
     */
    private LocalDate endDate;

    /** Tổng số lượt sử dụng tối đa của toàn hệ thống (&gt; 0). */
    private int maxUsage;

    /**
     * Số lượt dùng tối đa cho mỗi khách hàng (null = không giới hạn cá nhân,
     * CBR-74).
     */
    private Integer maxUsagePerCustomer;

    /** Số lượt đã sử dụng (0 &lt;= UsedCount &lt;= MaxUsage, DBR-14). */
    private int usedCount;

    /**
     * Giá trị đơn hàng tối thiểu để được áp dụng mã (so với giá gói TRƯỚC khi giảm,
     * CBR-73).
     */
    private long minOrderValue;

    /** Phạm vi áp dụng: MembershipAndDayPass hoặc MembershipOnly (CBR-76). */
    private ApplicableScope applicableScope;

    /**
     * Trạng thái kích hoạt (true = đang hoạt động, false = ngừng áp dụng, CBR-98).
     */
    private boolean active;

    /** Thời điểm tạo mã giảm giá. */
    private LocalDateTime createdAt;

    /**
     * Constructor tối thiểu chỉ nhận khóa chính voucherId (bất biến).
     * Truyền 0 khi tạo mới voucher chưa lưu vào database.
     *
     * @param voucherId ID định danh voucher
     */
    public Voucher(int voucherId) {
        this.voucherId = voucherId;
        this.usedCount = 0;
        this.minOrderValue = 0L;
        this.applicableScope = ApplicableScope.MEMBERSHIP_AND_DAY_PASS;
        this.active = true;
    }

    /**
     * Constructor đầy đủ các trường, dùng khi DAO nạp dữ liệu từ bảng Vouchers.
     */
    public Voucher(int voucherId, String voucherCode, DiscountType discountType, int discountValue,
            LocalDate startDate, LocalDate endDate, int maxUsage, Integer maxUsagePerCustomer,
            int usedCount, long minOrderValue, ApplicableScope applicableScope, boolean active,
            LocalDateTime createdAt) {
        this.voucherId = voucherId;
        this.voucherCode = voucherCode;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxUsage = maxUsage;
        this.maxUsagePerCustomer = maxUsagePerCustomer;
        this.usedCount = usedCount;
        this.minOrderValue = minOrderValue;
        this.applicableScope = applicableScope;
        this.active = active;
        this.createdAt = createdAt;
    }

    // ===== Getter / Setter =====

    public int getVoucherId() {
        return voucherId;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public void setVoucherCode(String voucherCode) {
        this.voucherCode = voucherCode;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public int getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(int discountValue) {
        this.discountValue = discountValue;
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

    public int getMaxUsage() {
        return maxUsage;
    }

    public void setMaxUsage(int maxUsage) {
        this.maxUsage = maxUsage;
    }

    public Integer getMaxUsagePerCustomer() {
        return maxUsagePerCustomer;
    }

    public void setMaxUsagePerCustomer(Integer maxUsagePerCustomer) {
        this.maxUsagePerCustomer = maxUsagePerCustomer;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public long getMinOrderValue() {
        return minOrderValue;
    }

    public void setMinOrderValue(long minOrderValue) {
        this.minOrderValue = minOrderValue;
    }

    public ApplicableScope getApplicableScope() {
        return applicableScope;
    }

    public void setApplicableScope(ApplicableScope applicableScope) {
        this.applicableScope = applicableScope;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ===== Phương thức tính toán & kiểm tra nghiệp vụ tiện ích =====

    /**
     * Tính số tiền được giảm giá thực tế dựa trên giá trị gói trước giảm giá (VND).
     *
     * @param originalPrice giá niêm yết của gói trước giảm
     * @return số tiền giảm (VND), không vượt quá giá trị gói
     */
    public long calculateDiscountAmount(long originalPrice) {
        if (originalPrice <= 0 || originalPrice < minOrderValue) {
            return 0L;
        }

        long discount;
        if (this.discountType == DiscountType.PERCENT) {
            discount = (originalPrice * this.discountValue) / 100L;
        } else {
            discount = this.discountValue;
        }

        return Math.min(discount, originalPrice);
    }

    /**
     * Kiểm tra voucher có còn lượt sử dụng trong toàn hệ thống hay không.
     */
    public boolean hasRemainingUsage() {
        return this.usedCount < this.maxUsage;
    }

    /**
     * Lấy số lượt sử dụng còn lại của voucher trên toàn hệ thống.
     */
    public int getRemainingUsage() {
        return Math.max(0, this.maxUsage - this.usedCount);
    }

    /**
     * Kiểm tra ngày chỉ định có nằm trong khoảng thời gian có hiệu lực của voucher
     * không.
     *
     * @param targetDate ngày cần kiểm tra
     * @return true nếu startDate &lt;= targetDate &lt;= endDate
     */
    public boolean isValidOnDate(LocalDate targetDate) {
        if (targetDate == null || startDate == null || endDate == null) {
            return false;
        }
        return !targetDate.isBefore(startDate) && !targetDate.isAfter(endDate);
    }

    /**
     * Kiểm tra loại gói dịch vụ có thuộc phạm vi áp dụng của voucher không
     * (CBR-76).
     * Voucher không bao giờ áp dụng cho dịch vụ PT.
     *
     * @param packageType loại gói dịch vụ
     * @return true nếu loại gói hợp lệ với phạm vi áp dụng
     */
    public boolean isApplicableFor(Package.Type packageType) {
        if (packageType == null) {
            return false;
        }

        if (this.applicableScope == ApplicableScope.MEMBERSHIP_ONLY) {
            return packageType == Package.Type.MEMBERSHIP;
        }

        if (this.applicableScope == ApplicableScope.MEMBERSHIP_AND_DAY_PASS) {
            return packageType == Package.Type.MEMBERSHIP || packageType == Package.Type.DAY_PASS;
        }

        return false;
    }

    @Override
    public String toString() {
        return "Voucher{voucherId=" + voucherId
                + ", code='" + voucherCode + '\''
                + ", type=" + discountType
                + ", value=" + discountValue
                + ", used=" + usedCount + "/" + maxUsage
                + ", active=" + active + '}';
    }

    // ===== Enum lồng theo chuẩn GBR-01 & Ràng buộc CHECK DB =====

    /**
     * Loại giảm giá (CK_Vouchers_Type).
     */
    public enum DiscountType {

        PERCENT("Percent"),
        AMOUNT("Amount");

        private final String dbValue;

        DiscountType(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static DiscountType fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }
            for (DiscountType dt : values()) {
                if (dt.dbValue.equalsIgnoreCase(dbValue)) {
                    return dt;
                }
            }
            return null;
        }
    }

    /**
     * Phạm vi áp dụng của mã giảm giá (CK_Vouchers_Scope).
     */
    public enum ApplicableScope {

        MEMBERSHIP_AND_DAY_PASS("MembershipAndDayPass"),
        MEMBERSHIP_ONLY("MembershipOnly");

        private final String dbValue;

        ApplicableScope(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static ApplicableScope fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }
            for (ApplicableScope as : values()) {
                if (as.dbValue.equalsIgnoreCase(dbValue)) {
                    return as;
                }
            }
            return null;
        }
    }
}
