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

    // GETTER TỰ TÍNH — không có field/setter "currentlyFrozen"; = openFreezeId khác null và lớn hơn 0; JSP: không dùng (chỉ Java/test)
    public boolean isCurrentlyFrozen() {
        return openFreezeId != null && openFreezeId > 0;
    }

    // GETTER TỰ TÍNH — không có field/setter "eligibleToFreeze"; = có gói, chưa bảo lưu, trạng thái Active, còn lượt và gói chưa hết hạn; JSP: không dùng (chỉ Java/test)
    public boolean isEligibleToFreeze() {
        if (!hasMembership() || isCurrentlyFrozen()) {
            return false;
        }
        if (!"Active".equalsIgnoreCase(subscriptionStatus) && !"Active".equalsIgnoreCase(customerStatus)) {
            return false;
        }
        int max = maxFreezesSnapshot != null ? maxFreezesSnapshot : 1;
        // Chỉ gói đang sử dụng mới được bảo lưu; gói mua nối tiếp chưa đến ngày bắt đầu thì chưa bảo lưu được
        if (isNotStartedYet()) {
            return false;
        }
        return freezeUsed < max && endDate != null && !endDate.isBefore(LocalDate.now());
    }

    /** Gói Membership đang hiển thị chưa đến ngày bắt đầu (gói mua nối tiếp, khách chưa có gói đang dùng). */
    public boolean isNotStartedYet() {
        return hasMembership() && startDate != null && startDate.isAfter(LocalDate.now());
    }

    // GETTER TỰ TÍNH — không có field/setter "freezeStatusBadgeClass"; = class huy hiệu theo đang bảo lưu / đủ điều kiện / còn lại; JSP: receptionist/freeze-membership.jsp:502
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

    // GETTER TỰ TÍNH — không có field/setter "freezeStatusLabel"; = chữ theo đang bảo lưu / đủ điều kiện (lượt đã dùng) / còn lại; JSP: receptionist/freeze-membership.jsp:504
    public String getFreezeStatusLabel() {
        if (isCurrentlyFrozen()) {
            return "Đang bảo lưu";
        }
        if (isEligibleToFreeze()) {
            return "Đủ ĐK (" + freezeUsed + "/" + (maxFreezesSnapshot != null ? maxFreezesSnapshot : 1) + " lần)";
        }
        if (isNotStartedYet()) {
            return "Gói chưa bắt đầu";
        }
        if (hasMembership() && maxFreezesSnapshot != null && freezeUsed >= maxFreezesSnapshot) {
            return "Hết lượt (" + freezeUsed + "/" + maxFreezesSnapshot + ")";
        }
        if (hasMembership() && endDate != null && endDate.isBefore(LocalDate.now())) {
            return "Gói đã hết hạn";
        }
        return "Chưa có gói";
    }

    // GETTER TỰ TÍNH — không có field/setter "daysRemaining"; = số ngày từ hôm nay tới endDate, không có gói → null; JSP: receptionist/freeze-membership.jsp:489
    public Long getDaysRemaining() {
        if (!hasMembership() || endDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        try {
            today = com.gms.util.DateTimeUtil.today();
        } catch (Throwable ignored) {
        }
        return java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
    }

    // GETTER TỰ TÍNH — không có field/setter "daysRemainingText"; = chữ theo getDaysRemaining ("Đã hết hạn", "Hết hạn hôm nay", "Còn N ngày"); JSP: receptionist/freeze-membership.jsp:487 …
    public String getDaysRemainingText() {
        Long days = getDaysRemaining();
        if (days == null) {
            return "";
        }
        if (days < 0) {
            return "Đã hết hạn";
        } else if (days == 0) {
            return "Hết hạn hôm nay";
        } else {
            return "Còn " + days + " ngày";
        }
    }
}
