package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng Customers trong cơ sở dữ liệu.
 * Đại diện cho hồ sơ khách hàng, gắn kết với một Person cụ thể.
 */
public class Customer {

    private int customerId;
    private int personId;
    private String customerCode;
    private String checkInPhoto;
    private String customerType;
    private String guardianName;
    private String guardianRelationship;
    private String guardianPhone;
    private String guardianPhone2;
    private String status;
    private LocalDateTime joinedAt;
    private LocalDateTime deletedAt;

    // Constructor mặc định
    public Customer() {
    }

    // Constructor cho Customer mới
    public Customer(int personId, String customerCode, String customerType, String status) {
        this.personId = personId;
        this.customerCode = customerCode;
        this.customerType = customerType;
        this.status = status;
    }

    // Constructor đầy đủ dùng khi đọc từ DB
    public Customer(int customerId, int personId, String customerCode, String checkInPhoto, String customerType, String guardianName, String guardianRelationship, String guardianPhone, String guardianPhone2, String status, LocalDateTime joinedAt, LocalDateTime deletedAt) {
        this.customerId = customerId;
        this.personId = personId;
        this.customerCode = customerCode;
        this.checkInPhoto = checkInPhoto;
        this.customerType = customerType;
        this.guardianName = guardianName;
        this.guardianRelationship = guardianRelationship;
        this.guardianPhone = guardianPhone;
        this.guardianPhone2 = guardianPhone2;
        this.status = status;
        this.joinedAt = joinedAt;
        this.deletedAt = deletedAt;
    }

    // Lấy customerId
    public int getCustomerId() {
        return customerId;
    }

    // Gán customerId
    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    // Lấy personId
    public int getPersonId() {
        return personId;
    }

    // Gán personId
    public void setPersonId(int personId) {
        this.personId = personId;
    }

    // Lấy mã khách hàng
    public String getCustomerCode() {
        return customerCode;
    }

    // Gán mã khách hàng
    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    // Lấy ảnh check-in
    public String getCheckInPhoto() {
        return checkInPhoto;
    }

    // Gán ảnh check-in
    public void setCheckInPhoto(String checkInPhoto) {
        this.checkInPhoto = checkInPhoto;
    }

    // Lấy loại khách hàng
    public String getCustomerType() {
        return customerType;
    }

    // Gán loại khách hàng
    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    // Lấy tên người giám hộ
    public String getGuardianName() {
        return guardianName;
    }

    // Gán tên người giám hộ
    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }

    // Lấy quan hệ với người giám hộ
    public String getGuardianRelationship() {
        return guardianRelationship;
    }

    // Gán quan hệ với người giám hộ
    public void setGuardianRelationship(String guardianRelationship) {
        this.guardianRelationship = guardianRelationship;
    }

    // Lấy SĐT người giám hộ
    public String getGuardianPhone() {
        return guardianPhone;
    }

    // Gán SĐT người giám hộ
    public void setGuardianPhone(String guardianPhone) {
        this.guardianPhone = guardianPhone;
    }

    // Lấy SĐT người giám hộ 2
    public String getGuardianPhone2() {
        return guardianPhone2;
    }

    // Gán SĐT người giám hộ 2
    public void setGuardianPhone2(String guardianPhone2) {
        this.guardianPhone2 = guardianPhone2;
    }

    // Lấy trạng thái khách hàng
    public String getStatus() {
        return status;
    }

    // Gán trạng thái khách hàng
    public void setStatus(String status) {
        this.status = status;
    }

    // Lấy thời điểm gia nhập
    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    // Gán thời điểm gia nhập
    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    // Lấy thời điểm xóa mềm
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    // Gán thời điểm xóa mềm
    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}