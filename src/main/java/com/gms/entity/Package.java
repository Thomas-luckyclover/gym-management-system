package com.gms.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Gói dịch vụ (gói tập, gói PT, buổi PT lẻ), ánh xạ bảng Packages.
 * LƯU Ý TÊN CLASS: Java có sẵn class java.lang.Package (tự import vào mọi file).
 * File nào dùng class này PHẢI ghi rõ: import com.gms.entity.Package;

 */
public class Package {

    /** Khóa chính tự tăng, không bao giờ đổi → final, không có setter. */
    private final int packageId;
    private String packageName;
    private String description;
    private String benefits;
    private Type packageType;
    private String durationUnit;
    private Integer durationValue;
    private Integer estimatedSessionCount;
    private int price;
    private boolean active;
    // DAO: Packages.UnlocksExerciseLibrary (27/10/2026). true = gói này mở khóa Thư viện bài tập khi còn hiệu lực.
    // getBenefitLines tự thêm 1 dòng quyền lợi, quản lý không phải gõ tay vào ô Benefits.
    private boolean unlocksExerciseLibrary;
    private LocalDateTime createdAt;
    // DAO: Packages.RowVer (ROWVERSION, 8 byte). Chỉ PackageDAO.findById đọc cột này;
    // màn sửa gói (UC-10) gửi lại giá trị cũ để UPDATE ... WHERE RowVer = ? chặn 2 Quản lý ghi đè nhau (MSG22).
    // Các câu SELECT khác (trang chủ, Mua gói) không đọc → để null.
    private byte[] rowVersion;

    /**
     * Constructor tối thiểu: chỉ nhận Id (final, bắt buộc gán ở đây), các trường còn lại gán bằng setter.
     * Cùng kiểu với SystemConfig, VerificationCode.
     *
     * @param packageId id định danh (khóa chính, bất biến); truyền 0 khi tạo gói mới chưa lưu DB
     */
    // Constructor chỉ nhận packageId (final), các trường khác gán bằng setter
    public Package(int packageId) {
        this.packageId = packageId;
    }

    // ===== Getter/Setter =====

    // Lấy packageId (bất biến)
    public int getPackageId() {
        return packageId;
    }

    // Lấy tên gói
    public String getPackageName() {
        return packageName;
    }

    // Gán tên gói
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    // Lấy mô tả
    public String getDescription() {
        return description;
    }

    // Gán mô tả
    public void setDescription(String description) {
        this.description = description;
    }

    // Lấy chuỗi quyền lợi thô
    public String getBenefits() {
        return benefits;
    }

    // Gán chuỗi quyền lợi thô
    public void setBenefits(String benefits) {
        this.benefits = benefits;
    }

    // Dòng quyền lợi thêm cho gói có đặc quyền Thư viện bài tập (in ở trang chủ và màn Mua gói).
    public static final String EXERCISE_LIBRARY_BENEFIT = "Mở khóa Thư viện bài tập (hơn 800 bài có hình minh họa)";

    // Tách chuỗi benefits thành danh sách dòng để JSP hiển thị; gói có đặc quyền thì thêm dòng Thư viện bài tập ở cuối
    public List<String> getBenefitLines() {
        List<String> lines = new ArrayList<>();

        // Duyệt từng dòng sau khi split, bỏ qua dòng trống
        if (benefits != null) {
            for (String line : benefits.split("\\r?\\n")) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty()) {
                    lines.add(trimmedLine);
                }
            }
        }

        // Quyền lợi lấy từ cột UnlocksExerciseLibrary, không phụ thuộc chữ quản lý gõ
        if (unlocksExerciseLibrary) {
            lines.add(EXERCISE_LIBRARY_BENEFIT);
        }
        return lines;
    }

    // Gói này có mở khóa Thư viện bài tập không (Packages.UnlocksExerciseLibrary)
    public boolean isUnlocksExerciseLibrary() {
        return unlocksExerciseLibrary;
    }

    // Gán cờ đặc quyền Thư viện bài tập (PackageDAO đọc từ DB)
    public void setUnlocksExerciseLibrary(boolean unlocksExerciseLibrary) {
        this.unlocksExerciseLibrary = unlocksExerciseLibrary;
    }

    // Lấy loại gói
    public Type getPackageType() {
        return packageType;
    }

    // Gán loại gói
    public void setPackageType(Type packageType) {
        this.packageType = packageType;
    }

    // Lấy đơn vị thời hạn (Day/Month)
    public String getDurationUnit() {
        return durationUnit;
    }

    // Gán đơn vị thời hạn
    public void setDurationUnit(String durationUnit) {
        this.durationUnit = durationUnit;
    }

    // Lấy giá trị thời hạn
    public Integer getDurationValue() {
        return durationValue;
    }

    // Gán giá trị thời hạn
    public void setDurationValue(Integer durationValue) {
        this.durationValue = durationValue;
    }

    // Tính số tháng của gói (0 nếu không phải gói tháng)
    public int getDurationMonths() {
        boolean isMonthly = "Month".equals(durationUnit) && durationValue != null;
        return isMonthly ? durationValue : 0;
    }

    // Lấy số buổi ước tính của gói PT
    public Integer getEstimatedSessionCount() {
        return estimatedSessionCount;
    }

    // Gán số buổi ước tính
    public void setEstimatedSessionCount(Integer estimatedSessionCount) {
        this.estimatedSessionCount = estimatedSessionCount;
    }

    // Lấy giá VND
    public int getPrice() {
        return price;
    }

    // Gán giá VND
    public void setPrice(int price) {
        this.price = price;
    }

    // Kiểm tra đang bán hay không
    public boolean isActive() {
        return active;
    }

    // Gán trạng thái đang bán
    public void setActive(boolean active) {
        this.active = active;
    }

    // Lấy thời điểm tạo gói
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Gán thời điểm tạo gói
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Phiên bản dòng (RowVer) lúc đọc — màn sửa gói dùng để phát hiện người khác vừa lưu trước
    public byte[] getRowVersion() {
        return rowVersion;
    }

    // Gán phiên bản dòng (PackageDAO.findById đọc từ cột RowVer)
    public void setRowVersion(byte[] rowVersion) {
        this.rowVersion = rowVersion;
    }

    // Chuỗi mô tả ngắn gọn cho debug/log
    @Override
    public String toString() {
        return "Package{packageId=" + packageId + ", packageName=" + packageName
                + ", packageType=" + packageType + ", price=" + price + "}";
    }

    // Loại gói — enum khớp constraint DB
    public enum Type {

        MEMBERSHIP("Membership"),
        DAY_PASS("DayPass"),
        PT_SINGLE("PTSingle"),
        PT_COUPLE("PTCouple"),
        SINGLE_SESSION("SingleSession");

        private final String dbValue;

        // Gán giá trị DB cho enum
        Type(String dbValue) {
            this.dbValue = dbValue;
        }

        // Lấy giá trị chuỗi trong DB
        public String getDbValue() {
            return dbValue;
        }

        // Kiểm tra gói có kèm PT hay không
        public boolean isPtService() {
            return this == PT_SINGLE || this == PT_COUPLE || this == SINGLE_SESSION;
        }

        // Chuyển chuỗi DB sang enum Type
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