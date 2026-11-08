package com.gms.dto;

/**
 * DTO hiển thị mỗi dòng trong danh sách kết quả tìm kiếm khách hàng
 * tại màn hình Check-in Lễ tân (UC-19).
 * Dùng khi tìm theo tên trả về nhiều kết quả để Lễ tân chọn đúng người.
 */
public class CustomerSearchItem {

    private int customerId;
    private String customerCode;
    private String fullName;
    private String phone;
    private String customerStatus;
    private String checkInPhoto;

    public CustomerSearchItem() {
    }

    public CustomerSearchItem(int customerId, String customerCode, String fullName,
                              String phone, String customerStatus, String checkInPhoto) {
        this.customerId = customerId;
        this.customerCode = customerCode;
        this.fullName = fullName;
        this.phone = phone;
        this.customerStatus = customerStatus;
        this.checkInPhoto = checkInPhoto;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCustomerStatus() {
        return customerStatus;
    }

    public void setCustomerStatus(String customerStatus) {
        this.customerStatus = customerStatus;
    }

    public String getCheckInPhoto() {
        return checkInPhoto;
    }

    public void setCheckInPhoto(String checkInPhoto) {
        this.checkInPhoto = checkInPhoto;
    }
}
