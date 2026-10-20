package com.gms.dto;

import java.math.BigDecimal;

/**
 * Thông tin quỹ phép năm của nhân viên trong một năm dương lịch (BF-05).
 */
public class LeaveBalance {

    private int quota;                   // Định mức (P-23: mặc định 14 ngày)
    private BigDecimal usedDays;         // Đã duyệt trong năm
    private BigDecimal pendingDays;      // Đang chờ duyệt trong năm
    private BigDecimal remainingDays;    // Còn lại = Quota - Used - Pending

    public LeaveBalance() {
        this.quota = 14;
        this.usedDays = BigDecimal.ZERO;
        this.pendingDays = BigDecimal.ZERO;
        this.remainingDays = BigDecimal.valueOf(14);
    }

    public LeaveBalance(int quota, BigDecimal usedDays, BigDecimal pendingDays) {
        this.quota = quota;
        this.usedDays = usedDays != null ? usedDays : BigDecimal.ZERO;
        this.pendingDays = pendingDays != null ? pendingDays : BigDecimal.ZERO;
        this.remainingDays = BigDecimal.valueOf(quota).subtract(this.usedDays).subtract(this.pendingDays);
        if (this.remainingDays.compareTo(BigDecimal.ZERO) < 0) {
            this.remainingDays = BigDecimal.ZERO;
        }
    }

    public int getQuota() {
        return quota;
    }

    public void setQuota(int quota) {
        this.quota = quota;
    }

    public BigDecimal getUsedDays() {
        return usedDays;
    }

    public void setUsedDays(BigDecimal usedDays) {
        this.usedDays = usedDays;
    }

    public BigDecimal getPendingDays() {
        return pendingDays;
    }

    public void setPendingDays(BigDecimal pendingDays) {
        this.pendingDays = pendingDays;
    }

    public BigDecimal getRemainingDays() {
        return remainingDays;
    }

    public void setRemainingDays(BigDecimal remainingDays) {
        this.remainingDays = remainingDays;
    }
}
