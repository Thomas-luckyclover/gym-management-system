package com.gms.dto;

import java.time.LocalDate;

/**
 * DTO nhận dữ liệu tạo nhanh hồ sơ khách hàng tại quầy lễ tân (Step 2 - Counter Sales).
 */
public class CounterCustomerCreateInput {

    private final String fullName;
    private final String phone;
    private final LocalDate dateOfBirth;
    private final String customerType; // Self | Dependent
    private final String guardianName;
    private final String guardianRelationship;
    private final String guardianPhone;
    // Form tạo nhanh (counter-sale-customers.jsp, ô qcEmail). Bắt buộc với hồ sơ Self: dùng tạo tài khoản + nhận mật khẩu tạm.
    // Hồ sơ Dependent không có tài khoản (CBR-21) → bỏ qua.
    private final String email;

    public CounterCustomerCreateInput(
            String fullName,
            String phone,
            LocalDate dateOfBirth,
            String customerType,
            String guardianName,
            String guardianRelationship,
            String guardianPhone,
            String email) {
        this.fullName = fullName;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.customerType = customerType;
        this.guardianName = guardianName;
        this.guardianRelationship = guardianRelationship;
        this.guardianPhone = guardianPhone;
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getCustomerType() {
        return customerType;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public String getGuardianRelationship() {
        return guardianRelationship;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public String getEmail() {
        return email;
    }
}
