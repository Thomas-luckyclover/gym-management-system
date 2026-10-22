package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Ánh xạ bảng CustomerDeletionRequests (BF-07, UC-16, UC-17).
 * Lưu trữ các yêu cầu xóa hồ sơ khách hàng gửi từ Lễ tân lên Quản lý phê duyệt.
 *
 * <p>DAO là nơi tạo và đọc đối tượng này từ ResultSet; Service dùng để kiểm tra nghiệp vụ.</p>
 */
public class CustomerDeletionRequest {

    // DAO: CustomerDeletionRequests.CustomerDeletionRequestId, khóa chính tự tăng.
    private int customerDeletionRequestId;

    // DAO: CustomerDeletionRequests.CustomerId, khách hàng được yêu cầu xóa.
    private int customerId;

    // DAO: CustomerDeletionRequests.RequestedByEmployeeId, Lễ tân gửi yêu cầu.
    private int requestedByEmployeeId;

    // DAO: CustomerDeletionRequests.ReviewedByEmployeeId; NULL khi còn Pending.
    private Integer reviewedByEmployeeId;

    // DAO: CustomerDeletionRequests.Reason, lý do xóa hồ sơ (5-500 ký tự).
    private String reason;

    // DAO: CustomerDeletionRequests.Status (Pending | Approved | Rejected).
    private Status status;

    // DAO: CustomerDeletionRequests.RejectReason; chỉ có khi Rejected (5-500 ký tự).
    private String rejectReason;

    // DAO: CustomerDeletionRequests.RequestedAt, thời điểm Lễ tân gửi yêu cầu.
    private LocalDateTime requestedAt;

    // DAO: CustomerDeletionRequests.ReviewedAt; thời điểm Quản lý duyệt hoặc từ chối.
    private LocalDateTime reviewedAt;

    /**
     * Trạng thái của yêu cầu xóa hồ sơ khách hàng.
     * Khớp 1-1 với CHECK constraint CK_DeletionRequests_Review trong CSDL.
     */
    public enum Status {
        PENDING("Pending", "Chờ duyệt"),
        APPROVED("Approved", "Đã duyệt"),
        REJECTED("Rejected", "Từ chối");

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

        public static Status fromDbValue(String value) {
            if (value == null) {
                return null;
            }
            for (Status s : values()) {
                if (s.dbValue.equalsIgnoreCase(value)) {
                    return s;
                }
            }
            return null;
        }
    }

    public CustomerDeletionRequest() {
    }

    public CustomerDeletionRequest(
            int customerDeletionRequestId,
            int customerId,
            int requestedByEmployeeId,
            Integer reviewedByEmployeeId,
            String reason,
            Status status,
            String rejectReason,
            LocalDateTime requestedAt,
            LocalDateTime reviewedAt
    ) {
        this.customerDeletionRequestId = customerDeletionRequestId;
        this.customerId = customerId;
        this.requestedByEmployeeId = requestedByEmployeeId;
        this.reviewedByEmployeeId = reviewedByEmployeeId;
        this.reason = reason;
        this.status = status;
        this.rejectReason = rejectReason;
        this.requestedAt = requestedAt;
        this.reviewedAt = reviewedAt;
    }

    public int getCustomerDeletionRequestId() {
        return customerDeletionRequestId;
    }

    public void setCustomerDeletionRequestId(int customerDeletionRequestId) {
        this.customerDeletionRequestId = customerDeletionRequestId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getRequestedByEmployeeId() {
        return requestedByEmployeeId;
    }

    public void setRequestedByEmployeeId(int requestedByEmployeeId) {
        this.requestedByEmployeeId = requestedByEmployeeId;
    }

    public Integer getReviewedByEmployeeId() {
        return reviewedByEmployeeId;
    }

    public void setReviewedByEmployeeId(Integer reviewedByEmployeeId) {
        this.reviewedByEmployeeId = reviewedByEmployeeId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }
}
