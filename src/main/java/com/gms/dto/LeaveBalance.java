package com.gms.dto;

import java.math.BigDecimal;

/**
 * Thông tin quỹ phép năm của nhân viên trong một năm dương lịch (BF-05).
 */
public class LeaveBalance {

    private final int quota;                   // Định mức P-23 được Service truyền vào; không tự cấp ngày phép.
    private final BigDecimal usedDays;         // Đã duyệt trong năm
    private final BigDecimal pendingDays;      // Đang chờ duyệt trong năm
    private final BigDecimal remainingDays;    // Còn lại = Quota - Used - Pending

    public LeaveBalance(int quota, BigDecimal usedDays, BigDecimal pendingDays) {
        this.quota = quota;
        this.usedDays = usedDays != null ? usedDays : BigDecimal.ZERO;
        this.pendingDays = pendingDays != null ? pendingDays : BigDecimal.ZERO;
        // BigDecimal giữ chính xác cả 0,5 ngày của lịch sử; quỹ còn = định mức - đã duyệt - đang chờ.
        BigDecimal remaining = BigDecimal.valueOf(quota).subtract(this.usedDays).subtract(this.pendingDays);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }
        this.remainingDays = remaining;
    }

    public int getQuota() {
        return quota;
    }

    public BigDecimal getUsedDays() {
        return usedDays;
    }

    public BigDecimal getPendingDays() {
        return pendingDays;
    }

    public BigDecimal getRemainingDays() {
        return remainingDays;
    }

}
