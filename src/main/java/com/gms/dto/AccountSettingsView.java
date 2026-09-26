package com.gms.dto;

import com.gms.constant.Role;
import com.gms.entity.Employee;
import java.time.LocalDate;

/**
 * Dữ liệu hiển thị trên màn Account Settings.
 *
 * CBR-108: với Staff (Admin/Manager/PT/Receptionist), các field từ Employee
 * (position, specialty, yearsOfExperience, hireDate, commissionRate, employmentStatus)
 * chỉ để HIỂN THỊ READ-ONLY — không có setter, không được sửa qua màn này (chỉ Manager sửa qua UC-13).
 * Với Customer, các field này luôn null (không có Employee).
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
    private final Role position;
    private final String specialty;
    private final Integer yearsOfExperience;
    private final LocalDate hireDate;
    private final Integer commissionRate;
    private final Employee.Status employmentStatus;

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
            Role position,
            String specialty,
            Integer yearsOfExperience,
            LocalDate hireDate,
            Integer commissionRate,
            Employee.Status employmentStatus
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
        this.position = position;
        this.specialty = specialty;
        this.yearsOfExperience = yearsOfExperience;
        this.hireDate = hireDate;
        this.commissionRate = commissionRate;
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

    public Role getPosition() {
        return position;
    }

    public String getSpecialty() {
        return specialty;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public Integer getCommissionRate() {
        return commissionRate;
    }

    public Employee.Status getEmploymentStatus() {
        return employmentStatus;
    }
}