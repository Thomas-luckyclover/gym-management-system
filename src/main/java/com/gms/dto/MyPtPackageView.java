package com.gms.dto;

import java.time.LocalDate;

/**
 * MỘT GÓI PT của khách: dùng cho thẻ "Gói PT của bạn" và cho ô lọc "Gói PT".
 *
 * Ai điền field nào?
 *   - DAO   : các field lấy thẳng từ DB (phần 1).
 *   - Service: các field tính thêm để hiển thị (phần 2).
 */
public class MyPtPackageView {

    // ===== 1. DAO điền: lấy thẳng từ DB =====
    private int subscriptionId;          // Subscriptions.SubscriptionId
    private String type;                 // Subscriptions.SubscriptionType: PTSingle | PTCouple
    private String packageName;          // Orders.PackageNameSnapshot
    private LocalDate startDate;         // Subscriptions.StartDate
    private LocalDate endDate;           // Subscriptions.EndDate
    private int holderCustomerId;        // Subscriptions.CustomerId (người đứng tên)
    private Integer partnerCustomerId;   // Orders.PartnerCustomerId (người đi kèm, gói đôi)
    private int ptEmployeeId;            // Subscriptions.PtEmployeeId (PT phụ trách gói)
    private int generatedSessionCount;   // Subscriptions.GeneratedSessionCount (tổng buổi lúc mua)
    private int maxSwaps;                // Subscriptions.MaxSwapsSnapshot (giới hạn lượt đổi lúc mua)
    private int swapsUsed;               // Subscriptions.SwapsUsed (đã đổi bao nhiêu lượt)

    // ===== 2. Service điền: để hiển thị =====
    private String ptName;               // tên PT phụ trách
    private String ptPhone;              // [SDT-PT] số điện thoại PT phụ trách
    private String partnerName;          // tên "người tập cùng" (gói đôi)
    private String typeLabel;            // "PT cá nhân" | "PT đôi"
    private int upcomingSessionCount;    // số buổi Scheduled chưa bắt đầu
    private int remainingSwaps;          // maxSwaps - swapsUsed

    public boolean isCouple() { return "PTCouple".equals(type); }

    // ===== getter / setter =====
    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getHolderCustomerId() { return holderCustomerId; }
    public void setHolderCustomerId(int holderCustomerId) { this.holderCustomerId = holderCustomerId; }
    public Integer getPartnerCustomerId() { return partnerCustomerId; }
    public void setPartnerCustomerId(Integer partnerCustomerId) { this.partnerCustomerId = partnerCustomerId; }
    public int getPtEmployeeId() { return ptEmployeeId; }
    public void setPtEmployeeId(int ptEmployeeId) { this.ptEmployeeId = ptEmployeeId; }
    public int getGeneratedSessionCount() { return generatedSessionCount; }
    public void setGeneratedSessionCount(int generatedSessionCount) { this.generatedSessionCount = generatedSessionCount; }
    public int getMaxSwaps() { return maxSwaps; }
    public void setMaxSwaps(int maxSwaps) { this.maxSwaps = maxSwaps; }
    public int getSwapsUsed() { return swapsUsed; }
    public void setSwapsUsed(int swapsUsed) { this.swapsUsed = swapsUsed; }
    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }
    public String getPtPhone() { return ptPhone; }
    public void setPtPhone(String ptPhone) { this.ptPhone = ptPhone; }
    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }
    public int getUpcomingSessionCount() { return upcomingSessionCount; }
    public void setUpcomingSessionCount(int upcomingSessionCount) { this.upcomingSessionCount = upcomingSessionCount; }
    public int getRemainingSwaps() { return remainingSwaps; }
    public void setRemainingSwaps(int remainingSwaps) { this.remainingSwaps = remainingSwaps; }
}
