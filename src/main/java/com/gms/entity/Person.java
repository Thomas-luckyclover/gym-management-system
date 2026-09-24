package com.gms.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Thông tin chung của một người trong hệ thống, ánh xạ bảng Persons.
 */
public class Person {

    private final int personId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;
    private String avatar;
    private boolean released;
    private final LocalDateTime createdAt;

    public Person(int personId, String fullName, LocalDate dateOfBirth,
            String phone, String email, String avatar,
            boolean released, LocalDateTime createdAt) {
        this.personId = personId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.email = email;
        this.avatar = avatar;
        this.released = released;
        this.createdAt = createdAt;
    }

    public int getPersonId() {
        return personId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public boolean isReleased() {
        return released;
    }

    public void setReleased(boolean released) {
        this.released = released;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}