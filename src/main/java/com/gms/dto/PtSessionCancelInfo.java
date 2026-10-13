package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Dữ liệu tối thiểu cần cho màn hủy buổi PT khách hàng.
 * DAO trả về dữ liệu đã JOIN (Customers + TimeSlots + Subscriptions)
 * để Service không phải gọi thêm DAO khác mới xác định được quyền và thời điểm.
 */
public class PtSessionCancelInfo {

    private int sessionId;
    private int customerId;          // người đứng tên gói
    private Integer partnerCustomerId; // null nếu không phải gói đôi

    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;

    private String sessionStatus;   // 'Scheduled' | 'CheckedIn' | 'Missed' | 'Cancelled'
    private String subscriptionStatus; // 'Active' | 'Expired' | 'Cancelled' | ...

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public Integer getPartnerCustomerId() {
        return partnerCustomerId;
    }

    public void setPartnerCustomerId(Integer partnerCustomerId) {
        this.partnerCustomerId = partnerCustomerId;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }
}
