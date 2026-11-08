package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Yêu cầu bảo trì cơ sở vật chất từ lúc báo hỏng đến khi đóng (UC-28, UC-29).
 */
public class MaintenanceRequest {

    private int maintenanceRequestId;
    private String requestCode;
    private int equipmentItemId;
    private int facilityId; // Thuộc tính dẫn xuất qua EquipmentItem để tiện hiển thị
    private int reportedByEmployeeId;
    private Integer reviewedByEmployeeId;
    private Integer closedByEmployeeId;
    private Priority priority;
    private IncidentType incidentType = IncidentType.NOT_WORKING;
    private String affectedItemsNote;
    private boolean warningTagPlaced;
    private String description;
    private String attachedImages;
    private Status status;
    private Boolean setUnderMaintenance;
    private String rejectReason;
    private String managerNote;
    private String acceptanceNote;
    private LocalDateTime reportedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime closedAt;

    public MaintenanceRequest() {
    }

    public int getMaintenanceRequestId() {
        return maintenanceRequestId;
    }

    public void setMaintenanceRequestId(int maintenanceRequestId) {
        this.maintenanceRequestId = maintenanceRequestId;
    }

    public String getRequestCode() {
        return requestCode;
    }

    public void setRequestCode(String requestCode) {
        this.requestCode = requestCode;
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

    public int getReportedByEmployeeId() {
        return reportedByEmployeeId;
    }

    public void setReportedByEmployeeId(int reportedByEmployeeId) {
        this.reportedByEmployeeId = reportedByEmployeeId;
    }

    public Integer getReviewedByEmployeeId() {
        return reviewedByEmployeeId;
    }

    public void setReviewedByEmployeeId(Integer reviewedByEmployeeId) {
        this.reviewedByEmployeeId = reviewedByEmployeeId;
    }

    public Integer getClosedByEmployeeId() {
        return closedByEmployeeId;
    }

    public void setClosedByEmployeeId(Integer closedByEmployeeId) {
        this.closedByEmployeeId = closedByEmployeeId;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public IncidentType getIncidentType() {
        return incidentType;
    }

    public void setIncidentType(IncidentType incidentType) {
        this.incidentType = incidentType != null ? incidentType : IncidentType.NOT_WORKING;
    }

    public String getAffectedItemsNote() {
        return affectedItemsNote;
    }

    public void setAffectedItemsNote(String affectedItemsNote) {
        this.affectedItemsNote = affectedItemsNote;
    }

    @Deprecated
    public DetectedBy getDetectedBy() {
        return DetectedBy.RECEPTIONIST;
    }

    @Deprecated
    public void setDetectedBy(DetectedBy detectedBy) {
        // Deprecated: field removed from database schema
    }

    public boolean isWarningTagPlaced() {
        return warningTagPlaced;
    }

    public void setWarningTagPlaced(boolean warningTagPlaced) {
        this.warningTagPlaced = warningTagPlaced;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAttachedImages() {
        return attachedImages;
    }

    public void setAttachedImages(String attachedImages) {
        this.attachedImages = attachedImages;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Boolean getSetUnderMaintenance() {
        return setUnderMaintenance;
    }

    public void setSetUnderMaintenance(Boolean setUnderMaintenance) {
        this.setUnderMaintenance = setUnderMaintenance;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getManagerNote() {
        return managerNote;
    }

    public void setManagerNote(String managerNote) {
        this.managerNote = managerNote;
    }

    public String getAcceptanceNote() {
        return acceptanceNote;
    }

    public void setAcceptanceNote(String acceptanceNote) {
        this.acceptanceNote = acceptanceNote;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(LocalDateTime reportedAt) {
        this.reportedAt = reportedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public boolean isInProgress() {
        return status == Status.IN_PROGRESS;
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }

    public boolean isClosed() {
        return status == Status.CLOSED;
    }

    public enum Priority {
        URGENT("Urgent", "Khẩn"),
        NORMAL("Normal", "Thường"),
        LOW("Low", "Thấp");

        private final String dbValue;
        private final String displayName;

        Priority(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Priority fromDbValue(String dbValue) {
            if (dbValue == null) return null;
            for (Priority priority : values()) {
                if (priority.dbValue.equalsIgnoreCase(dbValue)) {
                    return priority;
                }
            }
            return null;
        }
    }

    public enum IncidentType {
        NOT_WORKING("NotWorking", "Không hoạt động"),
        STRANGE_NOISE("StrangeNoise", "Kêu / rung lạ"),
        BROKEN_OR_WORN("BrokenOrWorn", "Gãy vỡ, hao mòn"),
        SAFETY_HAZARD("SafetyHazard", "Mất an toàn"),
        HYGIENE("Hygiene", "Vệ sinh");

        private final String dbValue;
        private final String displayName;

        IncidentType(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static IncidentType fromDbValue(String dbValue) {
            if (dbValue == null) return NOT_WORKING;
            for (IncidentType type : values()) {
                if (type.dbValue.equalsIgnoreCase(dbValue)) {
                    return type;
                }
            }
            return NOT_WORKING;
        }
    }

    public enum DetectedBy {
        RECEPTIONIST("Receptionist", "Lễ tân tự phát hiện"),
        CUSTOMER("Customer", "Khách hàng báo"),
        TECHNICIAN("Technician", "Kỹ thuật viên báo"),
        PT("PT", "PT báo");

        private final String dbValue;
        private final String displayName;

        DetectedBy(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static DetectedBy fromDbValue(String dbValue) {
            if (dbValue == null) return RECEPTIONIST;
            for (DetectedBy d : values()) {
                if (d.dbValue.equalsIgnoreCase(dbValue)) {
                    return d;
                }
            }
            return RECEPTIONIST;
        }
    }

    public enum Status {
        OPEN("Open", "Chờ duyệt"),
        IN_PROGRESS("InProgress", "Đang xử lý"),
        REJECTED("Rejected", "Từ chối"),
        CLOSED("Closed", "Đã đóng");

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
            for (Status status : values()) {
                if (status.dbValue.equalsIgnoreCase(dbValue)) {
                    return status;
                }
            }
            return null;
        }
    }
}