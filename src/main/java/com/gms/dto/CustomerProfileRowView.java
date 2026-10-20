package com.gms.dto;

import java.time.LocalDateTime;

/**
 * DTO đại diện cho một dòng hiển thị hồ sơ khách hàng
 * dùng chung cho Màn #28 (Lễ tân) và Màn #30 (Quản lý) - UC-16, UC-41, BF-07.
 */
public class CustomerProfileRowView {

    // DAO: Customers.CustomerId, mã khách hàng số nguyên.
    private final int customerId;

    // DAO: Customers.CustomerCode, mã hiển thị ví dụ KH-0013.
    private final String customerCode;

    // DAO: Persons.PersonId, mã định danh cá nhân.
    private final int personId;

    // DAO: Persons.FullName, họ và tên khách hàng.
    private final String fullName;

    // DAO: Persons.Phone, số điện thoại liên hệ (được ẩn hoặc nhả theo IsReleased).
    private final String phone;

    // DAO: Persons.Email, thư điện tử khách hàng.
    private final String email;

    // DAO: Customers.CustomerType (Self | Dependent).
    private final String customerType;

    // DAO: Customers.Status (New | Active | Frozen | Expired | Inactive).
    private final String status;

    // DAO: Subscriptions.PackageName mới nhất còn hạn, NULL nếu chưa có gói.
    private final String currentPackageName;

    // DAO/Service: true nếu khách hàng còn ít nhất 1 gói đang chạy hoặc 1 buổi PT Scheduled.
    private final boolean hasActivePackage;

    // Service: true nếu khách hàng đang có 1 yêu cầu xóa ở trạng thái 'Pending'.
    // JSP hiển thị huy hiệu cảnh báo "Chờ xóa" cạnh tên khách hàng.
    private final boolean hasPendingDeletion;

    // DAO: CustomerDeletionRequests.CustomerDeletionRequestId đang Pending, NULL nếu không có.
    private final Integer pendingRequestId;

    // DAO: Customers.JoinedAt, ngày giờ tham gia hệ thống.
    private final LocalDateTime joinedAt;

    // DAO: Customers.DeletedAt, thời điểm xóa (chỉ có khi Inactive).
    private final LocalDateTime deletedAt;

    public CustomerProfileRowView(
            int customerId,
            String customerCode,
            int personId,
            String fullName,
            String phone,
            String email,
            String customerType,
            String status,
            String currentPackageName,
            boolean hasActivePackage,
            boolean hasPendingDeletion,
            Integer pendingRequestId,
            LocalDateTime joinedAt,
            LocalDateTime deletedAt
    ) {
        this.customerId = customerId;
        this.customerCode = customerCode;
        this.personId = personId;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.customerType = customerType;
        this.status = status;
        this.currentPackageName = currentPackageName;
        this.hasActivePackage = hasActivePackage;
        this.hasPendingDeletion = hasPendingDeletion;
        this.pendingRequestId = pendingRequestId;
        this.joinedAt = joinedAt;
        this.deletedAt = deletedAt;
    }

    public int getCustomerId() { return customerId; }
    public String getCustomerCode() { return customerCode; }
    public int getPersonId() { return personId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getCustomerType() { return customerType; }
    public String getStatus() { return status; }
    public String getCurrentPackageName() { return currentPackageName; }
    public boolean isHasActivePackage() { return hasActivePackage; }
    public boolean isHasPendingDeletion() { return hasPendingDeletion; }
    public Integer getPendingRequestId() { return pendingRequestId; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }

    public boolean isInactive() {
        return "Inactive".equalsIgnoreCase(status);
    }
}
