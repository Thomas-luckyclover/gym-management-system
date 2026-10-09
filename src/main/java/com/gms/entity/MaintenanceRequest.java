package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Yêu cầu bảo trì cơ sở vật chất từ lúc báo hỏng đến khi đóng (UC-28, UC-29).
 */
public class MaintenanceRequest {

    private int maintenanceRequestId;
    private String requestCode;
    private int facilityId;
    private int reportedByEmployeeId;
    private Integer reviewedByEmployeeId;
    private Integer closedByEmployeeId;
    private Priority priority;
    private boolean warningTagPlaced;
    private String description;
    private Status status;
    private Boolean setUnderMaintenance;
    private String rejectReason;
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
            if (dbValue == null) {
                return null;
            }

            for (Priority priority : values()) {
                if (priority.dbValue.equalsIgnoreCase(dbValue)) {
                    return priority;
                }
            }

            return null;
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