package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng CheckIns — ghi nhận mỗi lượt khách vào phòng tập (UC-19).
 * Khớp schema 1_Init_Schema.sql (Mục 13 CheckIns).
 */
public class CheckIn {

    private int checkInId;
    private int subscriptionId;
    private int verifiedByEmployeeId;
    private LocalDateTime checkedInAt;
    private boolean photoVerified;

    public CheckIn() {
    }

    public CheckIn(int subscriptionId, int verifiedByEmployeeId, LocalDateTime checkedInAt, boolean photoVerified) {
        this.subscriptionId = subscriptionId;
        this.verifiedByEmployeeId = verifiedByEmployeeId;
        this.checkedInAt = checkedInAt;
        this.photoVerified = photoVerified;
    }

    public int getCheckInId() {
        return checkInId;
    }

    public void setCheckInId(int checkInId) {
        this.checkInId = checkInId;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(int subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public int getVerifiedByEmployeeId() {
        return verifiedByEmployeeId;
    }

    public void setVerifiedByEmployeeId(int verifiedByEmployeeId) {
        this.verifiedByEmployeeId = verifiedByEmployeeId;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public boolean isPhotoVerified() {
        return photoVerified;
    }

    public void setPhotoVerified(boolean photoVerified) {
        this.photoVerified = photoVerified;
    }
}
