package com.gms.dto;

/**
 * Dữ liệu 1 hồ sơ phụ thuộc (Dependent) hiển thị ở tab "Hồ sơ phụ thuộc"
 * của Account Settings (chỉ xem, người giám hộ không sửa được - CBR-30).
 *
 * Dùng với tab-dependents.jspf: fullName, customerCode, ageLabel, underTen,
 * activePackageLabel.
 */
public class DependentCustomerView {

    private final String fullName;
    private final String customerCode;
    private final String ageLabel;
    private final boolean underTen;
    private final String activePackageLabel;

    public DependentCustomerView(
            String fullName,
            String customerCode,
            String ageLabel,
            boolean underTen,
            String activePackageLabel
    ) {

        this.fullName = fullName;
        this.customerCode = customerCode;
        this.ageLabel = ageLabel;
        this.underTen = underTen;
        this.activePackageLabel = activePackageLabel;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public String getAgeLabel() {
        return ageLabel;
    }

    public boolean isUnderTen() {
        return underTen;
    }

    public String getActivePackageLabel() {
        return activePackageLabel;
    }
}
