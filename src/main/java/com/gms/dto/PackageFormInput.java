package com.gms.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * DTO chứa dữ liệu submit từ biểu mẫu thêm mới / chỉnh sửa gói dịch vụ (#18, #20).
 * Lưu dạng chuỗi gốc để tầng Service thực hiện validate định dạng chuẩn chỉ (DBR-06, DBR-11, DBR-15, P-04, P-21).
 */
public class PackageFormInput {

    private Integer packageId;
    private String name;
    private String type;
    private String price;
    private String durationValue;
    private String durationUnit;
    private String estimatedSessionCount;
    private boolean unlocksExerciseLibrary;
    private String description;
    private String benefits;
    private boolean active = true;
    private String rowVersion;

    public PackageFormInput() {
    }

    public Integer getPackageId() {
        return packageId;
    }

    public void setPackageId(Integer packageId) {
        this.packageId = packageId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type.trim() : null;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price != null ? price.trim() : null;
    }

    public String getDurationValue() {
        return durationValue;
    }

    public void setDurationValue(String durationValue) {
        this.durationValue = durationValue != null ? durationValue.trim() : null;
    }

    public String getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(String durationUnit) {
        this.durationUnit = durationUnit != null ? durationUnit.trim() : null;
    }

    public String getEstimatedSessionCount() {
        return estimatedSessionCount;
    }

    public void setEstimatedSessionCount(String estimatedSessionCount) {
        this.estimatedSessionCount = estimatedSessionCount != null ? estimatedSessionCount.trim() : null;
    }

    public boolean isUnlocksExerciseLibrary() {
        return unlocksExerciseLibrary;
    }

    public void setUnlocksExerciseLibrary(boolean unlocksExerciseLibrary) {
        this.unlocksExerciseLibrary = unlocksExerciseLibrary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : null;
    }

    public String getBenefits() {
        return benefits;
    }

    public void setBenefits(String benefits) {
        this.benefits = benefits != null ? benefits.trim() : null;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(String rowVersion) {
        this.rowVersion = rowVersion != null ? rowVersion.trim() : null;
    }

    // ===== Tiện ích bóc tách dữ liệu =====

    /**
     * Bóc tách danh sách dòng quyền lợi từ textarea.
     */
    public List<String> getBenefitLines() {
        List<String> lines = new ArrayList<>();
        if (benefits == null || benefits.isBlank()) {
            return lines;
        }
        for (String line : benefits.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    /**
     * Chuyển đổi giá từ chuỗi sang BigDecimal (loại bỏ dấu phân cách nếu có).
     */
    public BigDecimal parsePrice() {
        if (price == null || price.isBlank()) {
            return null;
        }
        String clean = price.replace(".", "").replace(",", "").replace(" ", "").replace("₫", "");
        return new BigDecimal(clean);
    }

    /**
     * Chuyển đổi durationValue sang Integer.
     */
    public Integer parseDurationValue() {
        if (durationValue == null || durationValue.isBlank()) {
            return null;
        }
        return Integer.valueOf(durationValue.trim());
    }

    /**
     * Chuyển đổi estimatedSessionCount sang Integer.
     */
    public Integer parseEstimatedSessionCount() {
        if (estimatedSessionCount == null || estimatedSessionCount.isBlank()) {
            return null;
        }
        return Integer.valueOf(estimatedSessionCount.trim());
    }

    /**
     * Giải mã chuỗi RowVer Base64 sang mảng byte cho optimistic concurrency.
     */
    public byte[] decodeRowVersion() {
        if (rowVersion == null || rowVersion.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(rowVersion.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
