package com.gms.dto;

import java.time.LocalDate;

/**
 * 1 buổi PT ở dạng "số liệu thô" để tính thống kê trên PT Dashboard (hoa hồng, biểu đồ, tuần này, dự kiến tháng).
 * Chỉ có đúng các cột cần để cộng / đếm — không có tên khách.
 *
 * Ai tạo: SessionDAO.findStatRows.
 * Ai dùng: PtDashboardService (quyết định buổi nào được tính hoa hồng — rule nằm ở Service, không nằm ở DAO).
 *
 * @author Le Cong Minh
 */
public class SessionStatRow {

    private final LocalDate slotDate;
    private final String status;
    private final String cancelType;
    private final String sourceType;
    private final long commissionAmount;

    /**
     * @param slotDate         ngày diễn ra buổi.
     * @param status           Scheduled | Completed | Missed | Cancelled.
     * @param cancelType   cách xử lý khi PT không dạy được / khách hủy: Reschedule | Transfer | Cancel |
     *                         CustomerRequest; null nếu không có.
     * @param sourceType       loại gói sinh ra buổi: PTSingle | PTCouple | SingleSession.
     * @param commissionAmount hoa hồng đã chốt trên buổi (VND) — DBR-58.
     */
    public SessionStatRow(LocalDate slotDate, String status, String cancelType, String sourceType,
            long commissionAmount) {
        this.slotDate = slotDate;
        this.status = status;
        this.cancelType = cancelType;
        this.sourceType = sourceType;
        this.commissionAmount = commissionAmount;
    }

    public LocalDate getSlotDate() {
        return slotDate;
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
