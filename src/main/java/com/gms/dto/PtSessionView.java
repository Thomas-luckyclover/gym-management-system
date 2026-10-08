package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 1 buổi PT hiển thị trên PT Dashboard (khối "Buổi trong ngày" và "Buổi cần điểm danh").
 *
 * Ai tạo: SessionDAO.findByPt (dữ liệu thô từ DB), PtDashboardService gắn nhãn tiếng Việt.
 * Ai dùng: pt/dashboard.jsp.
 *
 * VD buổi couple 23/09 19:30–21:00 của Tâm & Hùng, Scheduled, cả 2 có Membership
 *     → customerName "…Tâm", partnerName "…Hùng", statusLabel "Đã xếp", statusBadge "badge-warning",
 *       missingMembership false.
 *
 * @author Le Cong Minh
 */
public class PtSessionView {

    private final int sessionId;
    private final LocalDate slotDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String status;
    private final String sourceType;
    private final String customerName;
    private final String partnerName;
    private final String missedReason;
    private final boolean missingMembership;

    private String dayLabel;
    private String timeRange;
    private String packageLabel;
    private String statusLabel;
    private String statusBadge;

    /**
     * @param sessionId         Sessions.SessionId.
     * @param slotDate          ngày dạy.
     * @param startTime         giờ bắt đầu, VD 19:30.
     * @param endTime           giờ kết thúc, VD 21:00.
     * @param status            Sessions.Status (tiếng Anh, lưu trong DB): Scheduled | CheckedIn | Missed | Cancelled.
     * @param sourceType        Subscriptions.SubscriptionType: PTSingle | PTCouple | SingleSession.
     * @param customerName      khách đứng tên gói.
     * @param partnerName       người đi kèm (chỉ gói couple), null nếu không có.
     * @param missedReason      lý do vắng (chỉ buổi Missed), null nếu không có.
     * @param missingMembership true khi buổi rơi vào ngày khách KHÔNG có gói tập còn hiệu lực
     *                          (gói couple: cả 2 người đều không có — CBR-64, CBR-66;
     *                          buổi lẻ: có gói ngày của chính hôm đó cũng được, không gắn nhãn — CBR-60).
     */
    public PtSessionView(int sessionId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, String status,
            String sourceType, String customerName, String partnerName, String missedReason,
            boolean missingMembership) {
        this.sessionId = sessionId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.sourceType = sourceType;
        this.customerName = customerName;
        this.partnerName = partnerName;
        this.missedReason = missedReason;
        this.missingMembership = missingMembership;
    }

    public int getSessionId() {
        return sessionId;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    /** @return trạng thái gốc trong DB, VD "Scheduled" — JSP không in thẳng, chỉ dùng để so sánh. */
    public String getStatus() {
        return status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public String getMissedReason() {
        return missedReason;
    }

    /** Lý do vắng hiển thị tiếng Việt (DB lưu mã tiếng Anh — GBR-01). */
    public String getMissedReasonText() {
        if (missedReason == null) {
            return null;
        }
        switch (missedReason) {
            case "NoCheckIn":
                return "Khách không check-in";
            case "NoMembership":
                return "Không có gói tập";
            case "MembershipFrozen":
                return "Gói tập đang bảo lưu";
            default:
                return missedReason;
        }
    }

    /** @return true → JSP tô nổi bật buổi + nhãn "Chưa có gói tập" để PT nhắc khách gia hạn. */
    public boolean isMissingMembership() {
        return missingMembership;
    }

    /** @return "Hôm nay", "Hôm qua" hoặc "dd/MM" — dùng ở khối Buổi cần điểm danh (có thể khác ngày). */
    public String getDayLabel() {
        return dayLabel;
    }

    public void setDayLabel(String dayLabel) {
        this.dayLabel = dayLabel;
    }

    /** @return VD "19:30 – 21:00". */
    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    /** @return VD "Gói PT Couple". */
    public String getPackageLabel() {
        return packageLabel;
    }

    public void setPackageLabel(String packageLabel) {
        this.packageLabel = packageLabel;
    }

    /** @return trạng thái tiếng Việt (GBR-01), VD "Đã xếp". */
    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    /** @return class màu badge theo GBR-28, VD "badge-success". */
    public String getStatusBadge() {
        return statusBadge;
    }

    public void setStatusBadge(String statusBadge) {
        this.statusBadge = statusBadge;
    }
}
