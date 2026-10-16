package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * THÔNG TIN 1 BUỔI để Service KIỂM TRA trước khi HỦY hoặc ĐỔI buổi.
 * Chỉ dùng nội bộ Service (không gửi ra JSP). DAO điền toàn bộ.
 */
public class PtSessionInfo {

    // --- buổi ---
    private int sessionId;               // Sessions.SessionId
    private String sessionStatus;        // Sessions.Status
    private int timeSlotId;              // Sessions.TimeSlotId (slot hiện tại)
    private LocalDate slotDate;          // TimeSlots.SlotDate
    private LocalTime startTime;         // TimeSlots.StartTime
    private LocalTime endTime;           // TimeSlots.EndTime

    // --- gói ---
    private int subscriptionId;          // Subscriptions.SubscriptionId
    private String subscriptionType;     // PTSingle | PTCouple | SingleSession
    private String subscriptionStatus;   // Active | Expired | ...
    private LocalDate packageStartDate;  // Subscriptions.StartDate
    private LocalDate packageEndDate;    // Subscriptions.EndDate
    private int swapsUsed;               // Subscriptions.SwapsUsed
    private int maxSwaps;                // Subscriptions.MaxSwapsSnapshot
    private String orderStatus;          // Orders.Status (phải là Paid)

    // --- người ---
    private int customerId;              // Subscriptions.CustomerId (người đứng tên)
    private Integer partnerCustomerId;   // Orders.PartnerCustomerId (chỉ gói đôi, còn lại null)
    private int ptEmployeeId;            // Sessions.PtEmployeeId
    private String ptName;               // tên PT
    private String ptStatus;             // Employees.Status (Working mới được đổi)
    private int ptPersonId;              // Employees.PersonId (để chặn PT tự đổi buổi của chính mình)

    // ===== getter / setter =====
    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }
    public String getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; }
    public int getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(int timeSlotId) { this.timeSlotId = timeSlotId; }
    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
    public LocalDate getPackageStartDate() { return packageStartDate; }
    public void setPackageStartDate(LocalDate packageStartDate) { this.packageStartDate = packageStartDate; }
    public LocalDate getPackageEndDate() { return packageEndDate; }
    public void setPackageEndDate(LocalDate packageEndDate) { this.packageEndDate = packageEndDate; }
    public int getSwapsUsed() { return swapsUsed; }
    public void setSwapsUsed(int swapsUsed) { this.swapsUsed = swapsUsed; }
    public int getMaxSwaps() { return maxSwaps; }
    public void setMaxSwaps(int maxSwaps) { this.maxSwaps = maxSwaps; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public Integer getPartnerCustomerId() { return partnerCustomerId; }
    public void setPartnerCustomerId(Integer partnerCustomerId) { this.partnerCustomerId = partnerCustomerId; }
    public int getPtEmployeeId() { return ptEmployeeId; }
    public void setPtEmployeeId(int ptEmployeeId) { this.ptEmployeeId = ptEmployeeId; }
    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }
    public String getPtStatus() { return ptStatus; }
    public void setPtStatus(String ptStatus) { this.ptStatus = ptStatus; }
    public int getPtPersonId() { return ptPersonId; }
    public void setPtPersonId(int ptPersonId) { this.ptPersonId = ptPersonId; }
}
