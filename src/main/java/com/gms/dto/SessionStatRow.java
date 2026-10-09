package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Số liệu buổi PT và giờ kết thúc cho dashboard. */
public class SessionStatRow {

    private final LocalDate slotDate;
    private final LocalTime endTime;
    private final String status;
    private final String cancelType;
    private final String sourceType;
    private final long commissionAmount;

    /**
     * @param slotDate         ngày diễn ra buổi.
     * @param status           Scheduled | CheckedIn | Missed | Cancelled.
     * @param cancelType   cách xử lý khi PT không dạy được / khách hủy: Reschedule | Transfer | Cancel |
     *                         CustomerRequest; null nếu không có.
     * @param sourceType       loại gói sinh ra buổi: PTSingle | PTCouple | SingleSession.
     * @param commissionAmount hoa hồng đã chốt trên buổi (VND) — DBR-58.
     */
    public SessionStatRow(LocalDate slotDate, LocalTime endTime, String status, String cancelType, String sourceType,
            long commissionAmount) {
        this.slotDate = slotDate;
        this.endTime = endTime;
        this.status = status;
        this.cancelType = cancelType;
        this.sourceType = sourceType;
        this.commissionAmount = commissionAmount;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }

    public String getCancelType() {
        return cancelType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public long getCommissionAmount() {
        return commissionAmount;
    }
}
