package com.gms.entity;

/**
 * Thực thể đại diện cho từng cá thể thiết bị vật lý trong phòng gym.
 */
public class EquipmentItem {

    private int equipmentItemId;
    private int facilityId;
    private String itemCode;
    private Status status;

    // Các trường bổ trợ hiển thị (không lưu trực tiếp bảng EquipmentItems)
    private String facilityName;
    private String location;
    private String activeRequestCode;
    private Integer activeRequestId;

    public EquipmentItem() {
    }

    public EquipmentItem(int equipmentItemId, int facilityId, String itemCode, Status status) {
        this.equipmentItemId = equipmentItemId;
        this.facilityId = facilityId;
        this.itemCode = itemCode;
        this.status = status;
    }

    public int getEquipmentItemId() {
        return equipmentItemId;
    }

    public void setEquipmentItemId(int equipmentItemId) {
        this.equipmentItemId = equipmentItemId;
    }

    public int getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(int facilityId) {
        this.facilityId = facilityId;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getActiveRequestCode() {
        return activeRequestCode;
    }

    public void setActiveRequestCode(String activeRequestCode) {
        this.activeRequestCode = activeRequestCode;
    }

    public Integer getActiveRequestId() {
        return activeRequestId;
    }

    public void setActiveRequestId(Integer activeRequestId) {
        this.activeRequestId = activeRequestId;
    }

    public boolean isAvailable() {
        return status == Status.AVAILABLE && activeRequestCode == null;
    }

    public boolean isUnderMaintenance() {
        return status == Status.UNDER_MAINTENANCE;
    }

    public boolean isRetired() {
        return status == Status.RETIRED;
    }

    public String getBadgeClass() {
        if (status == Status.RETIRED) return "badge-neutral";
        if (status == Status.UNDER_MAINTENANCE) return "badge-warning";
        if (status == Status.BROKEN || activeRequestCode != null) return "badge-danger";
        return "badge-success";
    }

    public enum Status {
        AVAILABLE("Available", "Khả dụng"),
        UNDER_MAINTENANCE("UnderMaintenance", "Đang bảo trì"),
        BROKEN("Broken", "Hỏng hóc"),
        RETIRED("Retired", "Ngừng sử dụng");

        private final String dbValue;
        private final String displayName;

        Status(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Status fromDbValue(String dbValue) {
            if (dbValue == null) return null;
            for (Status s : values()) {
                if (s.dbValue.equalsIgnoreCase(dbValue)) {
                    return s;
                }
            }
            return null;
        }
    }
}
