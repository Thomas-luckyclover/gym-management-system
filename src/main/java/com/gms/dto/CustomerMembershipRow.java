package com.gms.dto;

import java.time.LocalDate;

/**
 * DTO đại diện cho một dòng trong bảng Danh sách hội viên & Gói tập tại màn hình Bảo lưu Lễ tân.
 * Thiết kế theo phong cách lưới dữ liệu quản lý phòng gym (tương tự GymMaster).
 */
public class CustomerMembershipRow {

    private int customerId;
    private String customerCode;
    private String fullName;
    private String phone;
    private String customerStatus;

    private Integer subscriptionId;
    private String packageName;
    private String subscriptionType;
    private LocalDate startDate;
    private LocalDate endDate;
    private int freezeUsed;
    private Integer maxFreezesSnapshot;
    private String subscriptionStatus;

    private Integer openFreezeId;
    private LocalDate openFreezeStart;
    private LocalDate openFreezeEnd;

    public CustomerMembershipRow() {
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
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

    public Integer getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Integer subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
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

    public int getFreezeUsed() {
        return freezeUsed;
    }

    public void setFreezeUsed(int freezeUsed) {
        this.freezeUsed = freezeUsed;
    }

    public Integer getMaxFreezesSnapshot() {
        return maxFreezesSnapshot;
    }

    public void setMaxFreezesSnapshot(Integer maxFreezesSnapshot) {
        this.maxFreezesSnapshot = maxFreezesSnapshot;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public Integer getOpenFreezeId() {
        return openFreezeId;
    }

    public void setOpenFreezeId(Integer openFreezeId) {
        this.openFreezeId = openFreezeId;
    }

    public LocalDate getOpenFreezeStart() {
        return openFreezeStart;
    }

    public void setOpenFreezeStart(LocalDate openFreezeStart) {
        this.openFreezeStart = openFreezeStart;
    }

    public LocalDate getOpenFreezeEnd() {
        return openFreezeEnd;
    }

    public void setOpenFreezeEnd(LocalDate openFreezeEnd) {
        this.openFreezeEnd = openFreezeEnd;
    }

    // ===== Helper Methods cho JSP =====

    public boolean hasMembership() {
        return subscriptionId != null && subscriptionId > 0;
    }

    public boolean isCurrentlyFrozen() {
        return openFreezeId != null && openFreezeId > 0;
    }

    public boolean isEligibleToFreeze() {
        if (!hasMembership() || isCurrentlyFrozen()) {
            return false;
        }
        if (!"Active".equalsIgnoreCase(subscriptionStatus) && !"Active".equalsIgnoreCase(customerStatus)) {
            return false;
        }
        int max = maxFreezesSnapshot != null ? maxFreezesSnapshot : 1;
        return freezeUsed < max && endDate != null && !endDate.isBefore(LocalDate.now());
    }

    public String getFreezeStatusBadgeClass() {
        if (isCurrentlyFrozen()) {
            return "badge-warning";
        }
        if (isEligibleToFreeze()) {
            return "badge-ok";
        }
        if (hasMembership() && maxFreezesSnapshot != null && freezeUsed >= maxFreezesSnapshot) {
            return "badge-danger";
        }
        return "badge-muted";
    }

    public String getFreezeStatusLabel() {
        if (isCurrentlyFrozen()) {
            return "Đang bảo lưu";
        }
        if (isEligibleToFreeze()) {
            return "Đủ ĐK (" + freezeUsed + "/" + (maxFreezesSnapshot != null ? maxFreezesSnapshot : 1) + " lần)";
        }
        if (hasMembership() && maxFreezesSnapshot != null && freezeUsed >= maxFreezesSnapshot) {
            return "Hết lượt (" + freezeUsed + "/" + maxFreezesSnapshot + ")";
        }
        if (hasMembership() && endDate != null && endDate.isBefore(LocalDate.now())) {
            return "Gói đã hết hạn";
        }
        return "Chưa có gói";
    }
}
