package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng SystemConfigs — 20 tham số P-01…P-20 lưu dạng key-value (agents.md Phần 13).
 * Không hard-code giá trị tham số ở nơi khác, luôn đọc qua SystemConfigService.
 *
 * @author Le Cong Minh
 */
public class SystemConfig {
    private final int systemConfigId;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;
    private Integer updatedByEmployeeId;

    /**
     * @param systemConfigId id định danh (khóa chính, bất biến)
     */
    public SystemConfig(int systemConfigId) {
        this.systemConfigId = systemConfigId;
    }

    // ===== Getter/Setter =====


    public int getSystemConfigId() {
        return systemConfigId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getUpdatedByEmployeeId() {
        return updatedByEmployeeId;
    }

    public void setUpdatedByEmployeeId(Integer updatedByEmployeeId) {
        this.updatedByEmployeeId = updatedByEmployeeId;
    }
}
