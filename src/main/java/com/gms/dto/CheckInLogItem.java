package com.gms.dto;

import java.time.LocalDateTime;

/**
 * DTO hiển thị danh sách lượt check-in hôm nay / ca này.
 */
public class CheckInLogItem {

    private final int checkInId;
    private final String fullName;
    private final String customerCode;
    private final LocalDateTime checkedInAt;
    private final String packageName;
    private final boolean photoVerified;

    public CheckInLogItem(int checkInId, String fullName, String customerCode,
                          LocalDateTime checkedInAt, String packageName, boolean photoVerified) {
        this.checkInId = checkInId;
        this.fullName = fullName;
        this.customerCode = customerCode;
        this.checkedInAt = checkedInAt;
        this.packageName = packageName;
        this.photoVerified = photoVerified;
    }

    public int getCheckInId() {
        return checkInId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isPhotoVerified() {
        return photoVerified;
    }
}
