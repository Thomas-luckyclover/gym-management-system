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

    public Customer() {
    }

    public Customer(int personId, String customerCode, String customerType, String status) {
        this.personId = personId;
        this.customerCode = customerCode;
        this.customerType = customerType;
        this.status = status;
    }

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

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getPersonId() {
        return personId;
    }

    public void setPersonId(int personId) {
        this.personId = personId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCheckInPhoto() {
        return checkInPhoto;
    }

    public void setCheckInPhoto(String checkInPhoto) {
        this.checkInPhoto = checkInPhoto;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }

    public String getGuardianRelationship() {
        return guardianRelationship;
    }

    public void setGuardianRelationship(String guardianRelationship) {
        this.guardianRelationship = guardianRelationship;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public void setGuardianPhone(String guardianPhone) {
        this.guardianPhone = guardianPhone;
    }

    public String getGuardianPhone2() {
        return guardianPhone2;
    }

    public void setGuardianPhone2(String guardianPhone2) {
        this.guardianPhone2 = guardianPhone2;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}