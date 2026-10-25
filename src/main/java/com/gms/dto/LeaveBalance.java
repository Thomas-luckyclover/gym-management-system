package com.gms.dto;

import java.math.BigDecimal;

/**
 * Thông tin quỹ phép năm của nhân viên trong một năm dương lịch (BF-05).
 */
public class LeaveBalance {

    private final BigDecimal remainingDays;    // Còn lại = Quota - Used - Pending

    public LeaveBalance(int quota, BigDecimal usedDays, BigDecimal pendingDays) {
        BigDecimal used = usedDays != null ? usedDays : BigDecimal.ZERO;
        BigDecimal pending = pendingDays != null ? pendingDays : BigDecimal.ZERO;
        // BigDecimal giữ chính xác cả 0,5 ngày của lịch sử; quỹ còn = định mức - đã duyệt - đang chờ.
        BigDecimal remaining = BigDecimal.valueOf(quota).subtract(used).subtract(pending);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }
        this.remainingDays = remaining;
    }

    public BigDecimal getRemainingDays() {
        return remainingDays;
    }

}
