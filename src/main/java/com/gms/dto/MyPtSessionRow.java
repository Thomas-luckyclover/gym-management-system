package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * [DTO-BUOI] MỘT BUỔI TẬP PT hiển thị trên thời khóa biểu "Lịch tập của tôi".
 *
 * Ai điền field nào?
 *   - DAO     : phần 1 (lấy thẳng từ DB)  → MyPtSessionDAO.findSessionsInRange
 *   - Service : phần 2 (tính thêm)        → MyPtSessionService.fillDisplay
 */
public class MyPtSessionRow {

    // =====================================================================
    // 1. DAO ĐIỀN — lấy thẳng từ DB
    // =====================================================================

    private int sessionId;               // Sessions.SessionId
    private String status;               // Sessions.Status: Scheduled | CheckedIn | Missed | Cancelled
    private int ptEmployeeId;            // Sessions.PtEmployeeId (PT dạy buổi này)
    private String missedReason;         // Sessions.MissedReason (chỉ khi Missed)
    private String cancelType;           // Sessions.CancelType (chỉ khi Cancelled)
    private String cancelReason;         // Sessions.CancelReason
    private LocalDateTime cancelledAt;   // Sessions.CancelledAt

    private LocalDate slotDate;          // TimeSlots.SlotDate
    private LocalTime startTime;         // TimeSlots.StartTime
    private LocalTime endTime;           // TimeSlots.EndTime

    private int subscriptionId;          // Subscriptions.SubscriptionId (mã gói)
    private String type;                 // Subscriptions.SubscriptionType: PTSingle | PTCouple | SingleSession
    private LocalDate packageStartDate;  // Subscriptions.StartDate
    private LocalDate packageEndDate;    // Subscriptions.EndDate
    private int holderCustomerId;        // Subscriptions.CustomerId (người đứng tên gói)

    private String packageName;          // Orders.PackageNameSnapshot (tên gói lúc mua)
    private Integer partnerCustomerId;   // Orders.PartnerCustomerId (người đi kèm, chỉ gói đôi)

    // [TKB] chỉ findSessionsInRange điền (đã JOIN sẵn trong 1 câu SQL)
    private int sessionNo;               // số thứ tự buổi trong gói, theo ngày giờ (ROW_NUMBER) → "buổi 31/45"
    private int totalSessions;           // Subscriptions.GeneratedSessionCount (tổng số buổi của gói)
    private String holderName;           // họ tên người đứng tên gói
    private String partnerFullName;      // họ tên người đi kèm (gói đôi), null nếu không có

    private String ptName;               // tên PT
    private String ptPhone;              // [SDT-PT] số điện thoại PT (Persons.Phone)

    // =====================================================================
    // 2. SERVICE ĐIỀN — tính thêm để hiển thị
    // =====================================================================

    private String partnerName;          // tên "người tập cùng" (gói đôi), null nếu không có
    private String typeLabel;            // "PT cá nhân" | "PT đôi" | "Buổi lẻ"

    private String statusKey;            // [TKB] today | upcoming | warn | pending | checkedIn | missed | cancelled (JSP chọn màu theo key)
    private String statusLabel;          // "Hôm nay" | "Sắp tới" | "Cần chú ý" | "Đang cập nhật" | "Đã check-in" | "Vắng" | "Đã hủy"
    private String explanation;          // câu giải thích trạng thái / lý do (hiện ở thanh chi tiết)
    private String zaloUrl;              // [TKB] https://zalo.me/<SĐT PT>, null nếu PT chưa có SĐT

    // =====================================================================
    // HÀM TIỆN ÍCH
    // =====================================================================

    /** Gói đôi? */
    public boolean isCouple() {
        return "PTCouple".equals(type);
    }

    /** Buổi lẻ? (không có thời hạn gói, không được đổi buổi — BR-74) */
    public boolean isSingleSession() {
        return "SingleSession".equals(type);
    }

    // =====================================================================
    // GETTER / SETTER
    // =====================================================================

    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getPtEmployeeId() { return ptEmployeeId; }
    public void setPtEmployeeId(int ptEmployeeId) { this.ptEmployeeId = ptEmployeeId; }

    public String getMissedReason() { return missedReason; }
    public void setMissedReason(String missedReason) { this.missedReason = missedReason; }

    public String getCancelType() { return cancelType; }
    public void setCancelType(String cancelType) { this.cancelType = cancelType; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public LocalDate getPackageStartDate() { return packageStartDate; }
    public void setPackageStartDate(LocalDate packageStartDate) { this.packageStartDate = packageStartDate; }

    public LocalDate getPackageEndDate() { return packageEndDate; }
    public void setPackageEndDate(LocalDate packageEndDate) { this.packageEndDate = packageEndDate; }

    public int getHolderCustomerId() { return holderCustomerId; }
    public void setHolderCustomerId(int holderCustomerId) { this.holderCustomerId = holderCustomerId; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public Integer getPartnerCustomerId() { return partnerCustomerId; }
    public void setPartnerCustomerId(Integer partnerCustomerId) { this.partnerCustomerId = partnerCustomerId; }

    public int getSessionNo() { return sessionNo; }
    public void setSessionNo(int sessionNo) { this.sessionNo = sessionNo; }

    public int getTotalSessions() { return totalSessions; }
    public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getPartnerFullName() { return partnerFullName; }
    public void setPartnerFullName(String partnerFullName) { this.partnerFullName = partnerFullName; }

    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }

    public String getPtPhone() { return ptPhone; }
    public void setPtPhone(String ptPhone) { this.ptPhone = ptPhone; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }

    public String getStatusKey() { return statusKey; }
    public void setStatusKey(String statusKey) { this.statusKey = statusKey; }

    public String getZaloUrl() { return zaloUrl; }
    public void setZaloUrl(String zaloUrl) { this.zaloUrl = zaloUrl; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
