package com.gms.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng Persons trong cơ sở dữ liệu.
 * Chứa thông tin định danh con người dùng chung cho cả khách hàng và nhân viên.
 */
public class Person {

    private int personId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;
    private String avatar;
    private boolean isReleased;
    private LocalDateTime createdAt;

    // Constructor mặc định
    public Person() {
    }

    // Constructor cho Person mới chưa lưu DB
    public Person(String fullName, LocalDate dateOfBirth, String phone, String email) {
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.email = email;
    }

    // Constructor đầy đủ dùng khi đọc từ DB
    public Person(int personId, String fullName, LocalDate dateOfBirth, String phone, String email, String avatar,
            boolean isReleased, LocalDateTime createdAt) {
        this.personId = personId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.email = email;
        this.avatar = avatar;
        this.isReleased = isReleased;
        this.createdAt = createdAt;
    }

    // Lấy personId
    public int getPersonId() {
        return personId;
    }

    // Gán personId
    public void setPersonId(int personId) {
        this.personId = personId;
    }

    // Lấy họ và tên
    public String getFullName() {
        return fullName;
    }

    // Gán họ và tên
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    // Lấy ngày sinh
    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    // Gán ngày sinh
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    // Lấy số điện thoại
    public String getPhone() {
        return phone;
    }

    // Gán số điện thoại
    public void setPhone(String phone) {
        this.phone = phone;
    }

    // Lấy email
    public String getEmail() {
        return email;
    }

    // Gán email
    public void setEmail(String email) {
        this.email = email;
    }

    // Lấy ảnh đại diện
    public String getAvatar() {
        return avatar;
    }

    // Gán ảnh đại diện
    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    // Kiểm tra đã phát hành/thu hồi hồ sơ
    public boolean isReleased() {
        return isReleased;
    }

    // Gán trạng thái phát hành
    public void setReleased(boolean released) {
        isReleased = released;
    }

    // Lấy thời điểm tạo hồ sơ
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Gán thời điểm tạo hồ sơ
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
