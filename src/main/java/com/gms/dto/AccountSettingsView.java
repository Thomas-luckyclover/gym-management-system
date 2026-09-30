package com.gms.dto;

import com.gms.constant.Role;
import java.time.LocalDate;

/**
 * Dữ liệu hiển thị trên màn Account Settings.
 */
public class AccountSettingsView {

    private final int accountId;
    private final int personId;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final String phone;
    private final String email;
    private final String avatar;
    private final Role role;
    private final String employeeCode;
    private final LocalDate hireDate;
    private final String employmentStatus;

    public AccountSettingsView(
            int accountId,
            int personId,
            String fullName,
            LocalDate dateOfBirth,
            String phone,
            String email,
            String avatar,
            Role role,
            String employeeCode,
            LocalDate hireDate,
            String employmentStatus

    ) {

        this.accountId = accountId;
        this.personId = personId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.email = email;
        this.avatar = avatar;
        this.role = role;
        this.employeeCode = employeeCode;
        this.hireDate = hireDate;
        this.employmentStatus = employmentStatus;
    }

    public int getAccountId() {
        return accountId;
    }

    public int getPersonId() {
        return personId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatar() {
        return avatar;
    }

    public Role getRole() {
        return role;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }
    public LocalDate getHireDate() {
        return hireDate;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }
}
