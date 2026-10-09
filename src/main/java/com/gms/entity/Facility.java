package com.gms.entity;

/**
 * Cơ sở vật chất có thể được báo hỏng và bảo trì (UC-27, UC-28).
 */
public class Facility {

    private int facilityId;
    private String facilityName;
    private Category category;
    private String location;
    private int quantity;
    private Status status;

    public Facility() {
    }

    public Facility(
            int facilityId,
            String facilityName,
            Category category,
            String location,
            int quantity,
            Status status
    ) {
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.category = category;
        this.location = location;
        this.quantity = quantity;
        this.status = status;
    }

    public int getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(int facilityId) {
        this.facilityId = facilityId;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public enum Category {

        CARDIO("Cardio", "Cardio"),
        FREE_WEIGHTS("FreeWeights", "Tạ tự do"),
        MACHINES("Machines", "Máy tập"),
        AMENITIES("Amenities", "Tiện ích");

        private final String dbValue;
        private final String displayName;

        Category(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Category fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }

            for (Category category : values()) {
                if (category.dbValue.equalsIgnoreCase(dbValue)) {
                    return category;
                }
            }

            return null;
        }
    }

    public enum Status {

        AVAILABLE("Available", "Có thể sử dụng"),
        UNDER_MAINTENANCE("UnderMaintenance", "Đang bảo trì"),
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
            if (dbValue == null) {
                return null;
            }

            for (Status status : values()) {
                if (status.dbValue.equalsIgnoreCase(dbValue)) {
                    return status;
                }
            }

            return null;
        }
    }
}