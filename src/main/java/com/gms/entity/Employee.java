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

    // Constructor đầy đủ khởi tạo Employee
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

    // Lấy employeeId (bất biến)
    public int getEmployeeId() {
        return employeeId;
    }

    // Lấy personId (bất biến)
    public int getPersonId() {
        return personId;
    }

    // Lấy mã nhân viên (bất biến)
    public String getEmployeeCode() {
        return employeeCode;
    }

    // Lấy chức vụ
    public Role getPosition() {
        return position;
    }

    // Gán chức vụ
    public void setPosition(Role position) {
        this.position = position;
    }

    // Lấy ngày vào làm
    public LocalDate getHireDate() {
        return hireDate;
    }

    // Gán ngày vào làm
    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    // Lấy tỉ lệ hoa hồng
    public Integer getCommissionRate() {
        return commissionRate;
    }

    // Gán tỉ lệ hoa hồng
    public void setCommissionRate(Integer commissionRate) {
        this.commissionRate = commissionRate;
    }

    // Lấy chuyên môn
    public String getSpecialty() {
        return specialty;
    }

    // Gán chuyên môn
    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    // Lấy số năm kinh nghiệm
    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    // Gán số năm kinh nghiệm
    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    // Lấy trạng thái làm việc
    public Status getStatus() {
        return status;
    }

    // Gán trạng thái làm việc
    public void setStatus(Status status) {
        this.status = status;
    }

    // Lấy thời điểm xóa mềm
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    // Gán thời điểm xóa mềm
    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    // Lấy lý do nghỉ việc
    public String getResignationReason() {
        return resignationReason;
    }

    // Gán lý do nghỉ việc
    public void setResignationReason(String resignationReason) {
        this.resignationReason = resignationReason;
    }

    // Enum trạng thái nhân viên
    public enum Status {
        // Đang làm việc
        WORKING("Working"),
        // Đã nghỉ việc
        RESIGNED("Resigned");

        // Giá trị tương ứng trong DB
        private final String dbValue;

        // Gán giá trị DB cho enum
        Status(String dbValue) {
            this.dbValue = dbValue;
        }

        // Lấy giá trị chuỗi trong DB
        public String getDbValue() {
            return dbValue;
        }

        // Chuyển chuỗi DB sang enum Status
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