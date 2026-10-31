package com.gms.dto;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Base64;
import java.util.Locale;

/**
 * DTO đại diện cho một dòng gói dịch vụ trên màn hình danh mục gói của Quản lý (#17, #19).
 * Hỗ trợ hiển thị trạng thái, số đơn đang chạy (running orders), nhãn thời hạn, và rowVersion Base64.
 */
public class ManagerPackageRowView {

    private int packageId;
    private String name;
    private String type;
    private String typeDisplay;
    private BigDecimal price;
    private Integer durationValue;
    private String durationUnit;
    private Integer estimatedSessionCount;
    private boolean unlocksExerciseLibrary;
    private boolean active;
    private int runningOrdersCount;
    private String description;
    private String benefits;
    private byte[] rowVersion;

    public ManagerPackageRowView() {
    }

    public int getPackageId() {
        return packageId;
    }

    public void setPackageId(int packageId) {
        this.packageId = packageId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeDisplay() {
        if (typeDisplay != null && !typeDisplay.isBlank()) {
            return typeDisplay;
        }
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "Membership" -> "Hội viên";
            case "DayPass" -> "Vé ngày";
            case "PTSingle" -> "PT 1:1";
            case "PTCouple" -> "PT 1:2";
            case "SingleSession" -> "Buổi PT lẻ";
            default -> type;
        };
    }

    public void setTypeDisplay(String typeDisplay) {
        this.typeDisplay = typeDisplay;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getFormattedPrice() {
        if (price == null) {
            return "0 ₫";
        }
        NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
        return nf.format(price) + " ₫";
    }

    public Integer getDurationValue() {
        return durationValue;
    }

    public void setDurationValue(Integer durationValue) {
        this.durationValue = durationValue;
    }

    public String getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(String durationUnit) {
        this.durationUnit = durationUnit;
    }

    public String getDurationDisplay() {
        if ("SingleSession".equalsIgnoreCase(type)) {
            return "Theo buổi";
        }
        if (durationValue == null || durationUnit == null) {
            return "-";
        }
        if ("Day".equalsIgnoreCase(durationUnit)) {
            return durationValue + " ngày";
        }
        if ("Month".equalsIgnoreCase(durationUnit)) {
            return durationValue + " tháng";
        }
        return durationValue + " " + durationUnit;
    }

    public Integer getEstimatedSessionCount() {
        return estimatedSessionCount;
    }

    public void setEstimatedSessionCount(Integer estimatedSessionCount) {
        this.estimatedSessionCount = estimatedSessionCount;
    }

    public boolean isUnlocksExerciseLibrary() {
        return unlocksExerciseLibrary;
    }

    public void setUnlocksExerciseLibrary(boolean unlocksExerciseLibrary) {
        this.unlocksExerciseLibrary = unlocksExerciseLibrary;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getRunningOrdersCount() {
        return runningOrdersCount;
    }

    public void setRunningOrdersCount(int runningOrdersCount) {
        this.runningOrdersCount = runningOrdersCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBenefits() {
        return benefits;
    }

    public void setBenefits(String benefits) {
        this.benefits = benefits;
    }

    public byte[] getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(byte[] rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getRowVersionBase64() {
        if (rowVersion == null || rowVersion.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(rowVersion);
    }
}
