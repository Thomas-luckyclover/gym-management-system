package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO tổng hợp dữ liệu màn hình Bảo lưu gói tập cho một hội viên được tra cứu (UC-18, BF-09).
 * Cung cấp đầy đủ thông tin hồ sơ, gói Membership hiện tại, điều kiện bảo lưu,
 * thông tin đợt bảo lưu đang mở (nếu có), và lịch sử các lần bảo lưu trước.
 */
public class FreezeCustomerView {

    // ===== 1. Thông tin khách hàng =====
    private int customerId;
    private int personId;
    private String customerCode;
    private String fullName;
    private String phone;
    private String customerStatus;
    private String checkInPhoto;
    private String avatar;

    // ===== 2. Thông tin gói Membership hiện tại =====
    private boolean hasMembership;
    private int subscriptionId;
    private int orderId;
    private String packageName;
    private LocalDate packageStartDate;
    private LocalDate packageEndDate;
    private String subscriptionStatus;
    private int freezeUsed;
    private int maxFreezesSnapshot;

    // ===== 3. Đánh giá tính hợp lệ để tạo mới bảo lưu =====
    private boolean eligibleToFreeze;
    private String ineligibilityReason;

    // Giới hạn ngày cho form tạo bảo lưu mới (khi eligibleToFreeze = true)
    private int maxFreezeMonths; // Giá trị P-02 (mặc định 1 tháng)
    private LocalDate minStartDate; // Ngày mai (DBR-13)
    private LocalDate maxEndDate; // min(minStartDate + P-02 tháng - 1 ngày, packageEndDate)

    // ===== 4. Chi tiết đợt bảo lưu đang diễn ra (nếu gói đang Frozen) =====
    private boolean currentlyFrozen;
    private Integer openFreezeRecordId;
    private LocalDate currentFreezeStartDate;
    private LocalDate currentFreezePlannedEndDate;
    private String currentFreezeReason;
    private String currentFreezeHandledBy;
    private LocalDateTime currentFreezeCreatedAt;
    private int daysFrozenIfCancelledToday;
    private LocalDate projectedNewEndDateIfCancelledToday;
    private boolean canCancelEarly;

    // ===== 5. Cảnh báo tự phục vụ (CBR-115) =====
    private boolean isOwnProfile;

    // ===== 6. Danh sách các buổi PT bị ảnh hưởng (CBR-62) =====
    private List<FreezeSessionImpactView> affectedSessions = new ArrayList<>();

    // ===== 7. Lịch sử bảo lưu của riêng khách hàng này =====
    private List<FreezeRecordItemView> freezeHistory = new ArrayList<>();

    public FreezeCustomerView() {
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

    public String getCustomerStatus() {
        return customerStatus;
    }

    public void setCustomerStatus(String customerStatus) {
        this.customerStatus = customerStatus;
    }

    public String getCheckInPhoto() {
        return checkInPhoto;
    }

    public void setCheckInPhoto(String checkInPhoto) {
        this.checkInPhoto = checkInPhoto;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public boolean isHasMembership() {
        return hasMembership;
    }

    public void setHasMembership(boolean hasMembership) {
        this.hasMembership = hasMembership;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(int subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public LocalDate getPackageStartDate() {
        return packageStartDate;
    }

    public void setPackageStartDate(LocalDate packageStartDate) {
        this.packageStartDate = packageStartDate;
    }

    public LocalDate getPackageEndDate() {
        return packageEndDate;
    }

    public void setPackageEndDate(LocalDate packageEndDate) {
        this.packageEndDate = packageEndDate;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public int getFreezeUsed() {
        return freezeUsed;
    }

    public void setFreezeUsed(int freezeUsed) {
        this.freezeUsed = freezeUsed;
    }

    public int getMaxFreezesSnapshot() {
        return maxFreezesSnapshot;
    }

    public void setMaxFreezesSnapshot(int maxFreezesSnapshot) {
        this.maxFreezesSnapshot = maxFreezesSnapshot;
    }

    public boolean isEligibleToFreeze() {
        return eligibleToFreeze;
    }

    public void setEligibleToFreeze(boolean eligibleToFreeze) {
        this.eligibleToFreeze = eligibleToFreeze;
    }

    public String getIneligibilityReason() {
        return ineligibilityReason;
    }

    public void setIneligibilityReason(String ineligibilityReason) {
        this.ineligibilityReason = ineligibilityReason;
    }

    public int getMaxFreezeMonths() {
        return maxFreezeMonths;
    }

    public void setMaxFreezeMonths(int maxFreezeMonths) {
        this.maxFreezeMonths = maxFreezeMonths;
    }

    public LocalDate getMinStartDate() {
        return minStartDate;
    }

    public void setMinStartDate(LocalDate minStartDate) {
        this.minStartDate = minStartDate;
    }

    public LocalDate getMaxEndDate() {
        return maxEndDate;
    }

    public void setMaxEndDate(LocalDate maxEndDate) {
        this.maxEndDate = maxEndDate;
    }

    public boolean isCurrentlyFrozen() {
        return currentlyFrozen;
    }

    public void setCurrentlyFrozen(boolean currentlyFrozen) {
        this.currentlyFrozen = currentlyFrozen;
    }

    public Integer getOpenFreezeRecordId() {
        return openFreezeRecordId;
    }

    public void setOpenFreezeRecordId(Integer openFreezeRecordId) {
        this.openFreezeRecordId = openFreezeRecordId;
    }

    public LocalDate getCurrentFreezeStartDate() {
        return currentFreezeStartDate;
    }

    public void setCurrentFreezeStartDate(LocalDate currentFreezeStartDate) {
        this.currentFreezeStartDate = currentFreezeStartDate;
    }

    public LocalDate getCurrentFreezePlannedEndDate() {
        return currentFreezePlannedEndDate;
    }

    public void setCurrentFreezePlannedEndDate(LocalDate currentFreezePlannedEndDate) {
        this.currentFreezePlannedEndDate = currentFreezePlannedEndDate;
    }

    public String getCurrentFreezeReason() {
        return currentFreezeReason;
    }

    public void setCurrentFreezeReason(String currentFreezeReason) {
        this.currentFreezeReason = currentFreezeReason;
    }

    public String getCurrentFreezeHandledBy() {
        return currentFreezeHandledBy;
    }

    public void setCurrentFreezeHandledBy(String currentFreezeHandledBy) {
        this.currentFreezeHandledBy = currentFreezeHandledBy;
    }

    public LocalDateTime getCurrentFreezeCreatedAt() {
        return currentFreezeCreatedAt;
    }

    public void setCurrentFreezeCreatedAt(LocalDateTime currentFreezeCreatedAt) {
        this.currentFreezeCreatedAt = currentFreezeCreatedAt;
    }

    public int getDaysFrozenIfCancelledToday() {
        return daysFrozenIfCancelledToday;
    }

    public void setDaysFrozenIfCancelledToday(int daysFrozenIfCancelledToday) {
        this.daysFrozenIfCancelledToday = daysFrozenIfCancelledToday;
    }

    public LocalDate getProjectedNewEndDateIfCancelledToday() {
        return projectedNewEndDateIfCancelledToday;
    }

    public void setProjectedNewEndDateIfCancelledToday(LocalDate projectedNewEndDateIfCancelledToday) {
        this.projectedNewEndDateIfCancelledToday = projectedNewEndDateIfCancelledToday;
    }

    public boolean isCanCancelEarly() {
        return canCancelEarly;
    }

    public void setCanCancelEarly(boolean canCancelEarly) {
        this.canCancelEarly = canCancelEarly;
    }

    public boolean isOwnProfile() {
        return isOwnProfile;
    }

    public void setOwnProfile(boolean ownProfile) {
        isOwnProfile = ownProfile;
    }

    public List<FreezeSessionImpactView> getAffectedSessions() {
        return affectedSessions;
    }

    public void setAffectedSessions(List<FreezeSessionImpactView> affectedSessions) {
        this.affectedSessions = affectedSessions;
    }

    public List<FreezeRecordItemView> getFreezeHistory() {
        return freezeHistory;
    }

    public void setFreezeHistory(List<FreezeRecordItemView> freezeHistory) {
        this.freezeHistory = freezeHistory;
    }
}
