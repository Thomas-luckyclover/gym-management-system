package com.gms.dto;

/** Dữ liệu BF-05: DAO đọc, Service xử lý, JSP hiển thị. */
public class EligibleReviewer {
    private final int employeeId;
    private final String employeeCode;
    private final String fullName;
    private final String position;
    private final String avatarUrl;

    public EligibleReviewer(int employeeId, String employeeCode, String fullName, String position, String avatarUrl) {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.avatarUrl = avatarUrl;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPosition() {
        return position;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

}
