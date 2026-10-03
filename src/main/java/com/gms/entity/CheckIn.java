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

    // Constructor mặc định
    public CheckIn() {
    }

    // Constructor tạo mới CheckIn
    public CheckIn(int subscriptionId, int verifiedByEmployeeId, LocalDateTime checkedInAt, boolean photoVerified) {
        this.subscriptionId = subscriptionId;
        this.verifiedByEmployeeId = verifiedByEmployeeId;
        this.checkedInAt = checkedInAt;
        this.photoVerified = photoVerified;
    }

    // Lấy checkInId
    public int getCheckInId() {
        return checkInId;
    }

    // Gán checkInId
    public void setCheckInId(int checkInId) {
        this.checkInId = checkInId;
    }

    // Lấy subscriptionId
    public int getSubscriptionId() {
        return subscriptionId;
    }

    // Gán subscriptionId
    public void setSubscriptionId(int subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    // Lấy ID nhân viên xác nhận check-in
    public int getVerifiedByEmployeeId() {
        return verifiedByEmployeeId;
    }

    // Gán ID nhân viên xác nhận
    public void setVerifiedByEmployeeId(int verifiedByEmployeeId) {
        this.verifiedByEmployeeId = verifiedByEmployeeId;
    }

    // Lấy thời điểm check-in
    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    // Gán thời điểm check-in
    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    // Kiểm tra đã xác minh ảnh chưa
    public boolean isPhotoVerified() {
        return photoVerified;
    }

    // Gán trạng thái đã xác minh ảnh
    public void setPhotoVerified(boolean photoVerified) {
        this.photoVerified = photoVerified;
    }
}
