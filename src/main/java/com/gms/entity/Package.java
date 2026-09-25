package com.gms.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Gói dịch vụ (gói tập, gói PT, buổi PT lẻ), ánh xạ bảng Packages.
 *
 * LƯU Ý TÊN CLASS: Java có sẵn class java.lang.Package (tự import vào mọi file).
 * File nào dùng class này PHẢI ghi rõ: import com.gms.entity.Package;
 * Có dòng import đó thì chữ "Package" trong file sẽ hiểu là class của mình, không phải của Java.
 *
 * Ví dụ 1 dòng seed: 3 | Gói 3 Tháng | Membership | 90 ngày | 2.190.000đ | đang bán
 *
 * @author Le Cong Minh
 */
public class Package {

    /** Khóa chính tự tăng, không bao giờ đổi → final, không có setter. */
    private final int packageId;
    private String packageName;
    private String description;
    private String benefits;
    private Type packageType;
    private Integer durationDays;
    private Integer estimatedSessionCount;
    private int price;
    private boolean active;
    private LocalDateTime createdAt;

    /**
     * Constructor tối thiểu: chỉ nhận Id (final, bắt buộc gán ở đây), các trường còn lại gán bằng setter.
     * Cùng kiểu với SystemConfig, VerificationCode.
     *
     * @param packageId id định danh (khóa chính, bất biến); truyền 0 khi tạo gói mới chưa lưu DB
     */
    public Package(int packageId) {
        this.packageId = packageId;
    }

    // ===== Getter/Setter =====

    public int getPackageId() {
        return packageId;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** Chuỗi gốc trong DB — mỗi dòng 1 quyền lợi, ngăn cách bằng xuống dòng. */
    public String getBenefits() {
        return benefits;
    }

    public void setBenefits(String benefits) {
        this.benefits = benefits;
    }

    /**
     * Tách cột Benefits thành từng dòng để JSP hiện gạch đầu dòng.
     *
     * Ví dụ: "Tập không giới hạn giờ\nMiễn phí nước uống" → ["Tập không giới hạn giờ", "Miễn phí nước uống"]
     * Trong JSP: <c:forEach var="line" items="${pkg.benefitLines}">
     *
     * @return danh sách quyền lợi, bỏ dòng trống; list rỗng nếu cột Benefits trống
     */
    public List<String> getBenefitLines() {
        List<String> lines = new ArrayList<>();
        if (benefits == null) {
            return lines;
        }
        // \\r?\\n: chấp nhận cả xuống dòng kiểu Windows (\r\n) lẫn Linux (\n)
        for (String line : benefits.split("\\r?\\n")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                lines.add(trimmedLine);
            }
        }
        return lines;
    }

    public Type getPackageType() {
        return packageType;
    }

    public void setPackageType(Type packageType) {
        this.packageType = packageType;
    }

    /** Số ngày hiệu lực; null với buổi PT lẻ (SingleSession). */
    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    /** Số buổi ƯỚC TÍNH, chỉ gói PT mới có; null với gói tập. */
    public Integer getEstimatedSessionCount() {
        return estimatedSessionCount;
    }

    public void setEstimatedSessionCount(Integer estimatedSessionCount) {
        this.estimatedSessionCount = estimatedSessionCount;
    }

    /** Giá VND, đã gồm VAT. */
    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    /** true = đang bán (IsActive = 1). JSP đọc bằng ${pkg.active}. */
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

    @Override
    public String toString() {
        return "Package{packageId=" + packageId + ", packageName=" + packageName
                + ", packageType=" + packageType + ", price=" + price + "}";
    }

    /**
     * Loại gói — khớp constraint CK_Packages_Type trong DB.
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

        /**
         * Gói có kèm huấn luyện viên không?
         * Trang chủ dùng để chia 2 bảng giá: gói tập (Membership, DayPass) và gói PT (3 loại còn lại).
         */
        public boolean isPtService() {
            return this == PT_SINGLE || this == PT_COUPLE || this == SINGLE_SESSION;
        }

        public static Type fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }
            for (Type type : values()) {
                if (type.dbValue.equalsIgnoreCase(dbValue)) {
                    return type;
                }
            }
            return null;
        }
    }
}