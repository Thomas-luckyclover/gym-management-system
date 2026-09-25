package com.gms.entity;

import com.gms.constant.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Hồ sơ nhân viên, ánh xạ bảng Employees.
 */
public class Employee {

    private final int employeeId;
    private final int personId;
    private final String employeeCode;
    private Role position;
    private LocalDate hireDate;
    private Integer commissionRate;
    private String specialty;
    private Integer yearsOfExperience;
    private Status status;
    private LocalDateTime deletedAt;
    private String resignationReason;

    public Employee(int employeeId, int personId, String employeeCode,
            Role position, LocalDate hireDate, Integer commissionRate,
            String specialty, Integer yearsOfExperience,
            Status status, LocalDateTime deletedAt,
            String resignationReason) {

        this.employeeId = employeeId;
        this.personId = personId;
        this.employeeCode = employeeCode;
        this.position = position;
        this.hireDate = hireDate;
        this.commissionRate = commissionRate;
        this.specialty = specialty;
        this.yearsOfExperience = yearsOfExperience;
        this.status = status;
        this.deletedAt = deletedAt;
        this.resignationReason = resignationReason;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public int getPersonId() {
        return personId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public Role getPosition() {
        return position;
    }

    public void setPosition(Role position) {
        this.position = position;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public Integer getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(Integer commissionRate) {
        this.commissionRate = commissionRate;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getResignationReason() {
        return resignationReason;
    }

    public void setResignationReason(String resignationReason) {
        this.resignationReason = resignationReason;
    }

    public enum Status {

        WORKING("Working"),
        RESIGNED("Resigned");

        private final String dbValue;

        Status(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
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