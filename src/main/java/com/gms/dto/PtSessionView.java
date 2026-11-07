package com.gms.dto;

import com.gms.entity.Package;
import com.gms.entity.Session;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO 1 BUỔI trong "Lịch dạy của bạn" — DAO TẠO: PtDashboardDAO.findSessions;
 * Service ĐIỀN thêm: PtDashboardService.prepareSessionDisplay (nhãn loại gói, nhãn trạng thái, nút Xử lý buổi).
 * JSP đọc: pt/dashboard.jsp (biến s trong vòng lặp day.sessions).
 */
public class PtSessionView {
    // ===== IN RA MÀN — dữ liệu từ DAO =====
    // ← Sessions.SessionId → link "Xử lý buổi" (/pt/schedule?handle=<id>), so với buổi "Tiếp theo"
    private final int sessionId;
    // ← TimeSlots.SlotDate → ngày của link Xử lý buổi, data-start / data-end (JS tô buổi đang diễn ra)
    private final LocalDate slotDate;
    // ← TimeSlots.StartTime → giờ bắt đầu in đậm
    private final LocalTime startTime;
    // ← TimeSlots.EndTime → "– giờ kết thúc"
    private final LocalTime endTime;
    // ← Persons.FullName của người đứng tên → tên khách
    private final String customerName;
    // ← Persons.FullName người tập cùng (chỉ PTCouple) → "& <tên>"
    private final String partnerName;
    // ← Sessions.Status → khác SCHEDULED thì hiện huy hiệu trạng thái
    private final Session.Status status;

    // ===== IN RA MÀN — Service điền (prepareSessionDisplay) =====
    // ← typeLabel(subscriptionType): "PT cá nhân" | "PT đôi" | "Buổi lẻ" → dòng dưới tên khách
    private String subscriptionTypeLabel;
    // ← theo status: "Đã lên lịch" | "Đã check-in" | "Vắng" | "Đã hủy" → chữ huy hiệu
    private String statusLabel;
    // ← badge-warning | badge-success | badge-danger | badge-neutral → màu huy hiệu
    private String statusBadgeClass;
    // ← status = Scheduled VÀ chưa tới giờ bắt đầu → hiện nút "Xử lý buổi" và chip "Tiếp theo"
    private boolean canOpenActions;
    // ← "Khách không check-in" … (chỉ buổi Missed) → chữ nhỏ cạnh huy hiệu "Vắng"
    private String missedReasonLabel;

    // ===== KHÔNG IN RA MÀN (dùng để tính) =====
    // ← Subscriptions.SubscriptionType → subscriptionTypeLabel
    private final Package.Type subscriptionType;
    // ← Sessions.MissedReason → missedReasonLabel
    private final Session.MissedReason missedReason;

    public PtSessionView(int sessionId, LocalDate slotDate, LocalTime startTime, LocalTime endTime,
            String customerName, String partnerName, Package.Type subscriptionType,
            Session.Status status, Session.MissedReason missedReason) {
        this.sessionId = sessionId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;

        this.customerName = customerName;
        this.partnerName = partnerName;
        this.subscriptionType = subscriptionType;

        this.status = status;
        this.missedReason = missedReason;
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

    public String getCustomerName() {
        return customerName;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public Package.Type getSubscriptionType() {
        return subscriptionType;
    }

    public Session.Status getStatus() {
        return status;
    }

    public Session.MissedReason getMissedReason() {
        return missedReason;
    }

    public String getSubscriptionTypeLabel() {
        return subscriptionTypeLabel;
    }

    public void setSubscriptionTypeLabel(String subscriptionTypeLabel) {
        this.subscriptionTypeLabel = subscriptionTypeLabel;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public String getStatusBadgeClass() {
        return statusBadgeClass;
    }

    public void setStatusBadgeClass(String statusBadgeClass) {
        this.statusBadgeClass = statusBadgeClass;
    }

    public String getMissedReasonLabel() {
        return missedReasonLabel;
    }

    public void setMissedReasonLabel(String missedReasonLabel) {
        this.missedReasonLabel = missedReasonLabel;
    }

    public boolean isCanOpenActions() {
        return canOpenActions;
    }

    public void setCanOpenActions(boolean canOpenActions) {
        this.canOpenActions = canOpenActions;
    }
}
