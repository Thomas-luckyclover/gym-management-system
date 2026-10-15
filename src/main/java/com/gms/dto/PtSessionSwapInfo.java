package com.gms.dto;

import java.time.LocalDate;

/** DAO JOIN buổi, gói và người sở hữu; chỉ dùng nội bộ Service để kiểm tra đổi buổi. */
public class PtSessionSwapInfo extends PtSessionCancelInfo {
    private int subscriptionId;
    private int timeSlotId;
    private int ptEmployeeId;
    private String ptName;
    private String subscriptionType;
    private LocalDate packageStartDate;
    private LocalDate packageEndDate;
    private int swapsUsed;
    private int maxSwaps;

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int value) { subscriptionId = value; }
    public int getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(int value) { timeSlotId = value; }
    public int getPtEmployeeId() { return ptEmployeeId; }
    public void setPtEmployeeId(int value) { ptEmployeeId = value; }
    public String getPtName() { return ptName; }
    public void setPtName(String value) { ptName = value; }
    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String value) { subscriptionType = value; }
    public LocalDate getPackageStartDate() { return packageStartDate; }
    public void setPackageStartDate(LocalDate value) { packageStartDate = value; }
    public LocalDate getPackageEndDate() { return packageEndDate; }
    public void setPackageEndDate(LocalDate value) { packageEndDate = value; }
    public int getSwapsUsed() { return swapsUsed; }
    public void setSwapsUsed(int value) { swapsUsed = value; }
    public int getMaxSwaps() { return maxSwaps; }
    public void setMaxSwaps(int value) { maxSwaps = value; }
}
