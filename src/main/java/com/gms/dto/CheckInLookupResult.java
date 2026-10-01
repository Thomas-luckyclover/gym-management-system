package com.gms.dto;

import java.time.LocalDate;

/**
 * DTO chứa kết quả tra cứu hồ sơ và đánh giá điều kiện check-in của hội viên (UC-19).
 * Áp dụng các luật:
 * - CBR-37, CBR-38: Ảnh đối chiếu check-in thuộc hồ sơ hội viên
 * - DBR-47, DBR-48, CBR-58: Điều kiện canUseService (gói tập / vé ngày còn hiệu lực)
 * - DBR-34: Trạng thái Customer (Active / Frozen / Expired / New)
 * - GBR-03: Ngày kết thúc tính trọn vẹn cả ngày đó
 * - GBR-28: Mã màu ngữ nghĩa (ok / warn / danger)
 */
public class CheckInLookupResult {

    public enum Decision {
        ELIGIBLE("Đủ điều kiện vào tập", "ok"),
        ELIGIBLE_DAYPASS("Đủ điều kiện vào tập (Vé ngày)", "ok"),
        WARN_MISSING_PHOTO("Thiếu ảnh check-in", "warn"),
        DENIED_FROZEN("Từ chối vào tập — Gói đang bảo lưu", "danger"),
        DENIED_EXPIRED("Từ chối vào tập — Gói đã hết hạn", "danger"),
        DENIED_DAYPASS_USED("Từ chối vào tập — Vé ngày đã dùng", "danger"),
        DENIED_PT_ONLY("Từ chối vào tập — Chỉ có gói PT", "danger"),
        DENIED_NO_PACKAGE("Từ chối vào tập — Chưa có gói tập", "danger"),
        DENIED_DELETION_PENDING("Từ chối vào tập — Hồ sơ chờ duyệt xoá", "danger"),
        NOT_FOUND("Không tìm thấy hồ sơ", "danger");

        private final String title;
        private final String alertType; // ok, warn, danger theo GBR-28

        Decision(String title, String alertType) {
            this.title = title;
            this.alertType = alertType;
        }

        public String getTitle() {
            return title;
        }

        public String getAlertType() {
            return alertType;
        }
    }

    private int customerId;
    private int personId;
    private String customerCode;
    private String fullName;
    private String phone;
    private String avatar;
    private String checkInPhoto;
    private String customerStatus;
    private boolean hasPendingDeletion;

    // Thông tin gói tập tự do (Membership / DayPass)
    private Integer subscriptionId;
    private String subscriptionType;
    private String packageName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String subscriptionStatus;
    private LocalDate freezeEndDate;

    // Thông tin gói PT (nếu có)
    private boolean hasPtPackage;
    private String ptPackageName;
    private Integer ptRemainingSessions;
    private Integer ptTotalSessions;

    // Đánh giá check-in
    private Decision decision;
    private String message;
    private boolean canCheckIn;
    private boolean requiresPhoto;

    public CheckInLookupResult() {
    }

    public static CheckInLookupResult notFound(String message) {
        CheckInLookupResult result = new CheckInLookupResult();
        result.setDecision(Decision.NOT_FOUND);
        result.setMessage(message);
        result.setCanCheckIn(false);
        return result;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getPersonId() {
        return personId;
    }

    public void setPersonId(int personId) {
        this.personId = personId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getCheckInPhoto() {
        return checkInPhoto;
    }

    public void setCheckInPhoto(String checkInPhoto) {
        this.checkInPhoto = checkInPhoto;
    }

    public String getCustomerStatus() {
        return customerStatus;
    }

    public void setCustomerStatus(String customerStatus) {
        this.customerStatus = customerStatus;
    }

    public boolean isHasPendingDeletion() {
        return hasPendingDeletion;
    }

    public void setHasPendingDeletion(boolean hasPendingDeletion) {
        this.hasPendingDeletion = hasPendingDeletion;
    }

    public Integer getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Integer subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public LocalDate getFreezeEndDate() {
        return freezeEndDate;
    }

    public void setFreezeEndDate(LocalDate freezeEndDate) {
        this.freezeEndDate = freezeEndDate;
    }

    public boolean isHasPtPackage() {
        return hasPtPackage;
    }

    public void setHasPtPackage(boolean hasPtPackage) {
        this.hasPtPackage = hasPtPackage;
    }

    public String getPtPackageName() {
        return ptPackageName;
    }

    public void setPtPackageName(String ptPackageName) {
        this.ptPackageName = ptPackageName;
    }

    public Integer getPtRemainingSessions() {
        return ptRemainingSessions;
    }

    public void setPtRemainingSessions(Integer ptRemainingSessions) {
        this.ptRemainingSessions = ptRemainingSessions;
    }

    public Integer getPtTotalSessions() {
        return ptTotalSessions;
    }

    public void setPtTotalSessions(Integer ptTotalSessions) {
        this.ptTotalSessions = ptTotalSessions;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isCanCheckIn() {
        return canCheckIn;
    }

    public void setCanCheckIn(boolean canCheckIn) {
        this.canCheckIn = canCheckIn;
    }

    public boolean isRequiresPhoto() {
        return requiresPhoto;
    }

    public void setRequiresPhoto(boolean requiresPhoto) {
        this.requiresPhoto = requiresPhoto;
    }
}
