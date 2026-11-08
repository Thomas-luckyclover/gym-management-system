package com.gms.dto;

import java.time.LocalDate;

/**
 * Dòng dữ liệu thô của 1 hồ sơ Dependent lấy từ DAO, Service dùng để dựng DependentCustomerView.
 */
public class DependentCustomerRow {

    private final String fullName;
    private final String customerCode;
    private final LocalDate dateOfBirth;
    private final String packageName;
    private final LocalDate packageEndDate;

    public DependentCustomerRow(
            String fullName,
            String customerCode,
            LocalDate dateOfBirth,
            String packageName,
            LocalDate packageEndDate
    ) {

        this.fullName = fullName;
        this.customerCode = customerCode;
        this.dateOfBirth = dateOfBirth;
        this.packageName = packageName;
        this.packageEndDate = packageEndDate;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getPackageName() {
        return packageName;
    }

    public LocalDate getPackageEndDate() {
        return packageEndDate;
    }
}
