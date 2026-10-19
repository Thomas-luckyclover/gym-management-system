package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 1 BUỔI PT của khách. Dùng ở màn "Lịch tập" (ô thời khóa biểu) và "Gói của tôi" (dòng bảng buổi).
 *   Phần 1: DAO đọc từ DB (MyPtSessionDAO.readSessionRow).
 *   Phần 2: Service tính thêm để hiển thị (MyPtSessionService.fillDisplay).
 */
public class MyPtSessionRow {

    // ---------------- Phần 1: DAO đọc từ DB ----------------
    private int sessionId;               // Sessions.SessionId
    private String status;               // Sessions.Status: Scheduled | CheckedIn | Missed | Cancelled
    private String missedReason;         // Sessions.MissedReason (khi Missed)
    private String cancelType;           // Sessions.CancelType (khi Cancelled)
    private String cancelReason;         // Sessions.CancelReason
    private LocalDateTime cancelledAt;   // Sessions.CancelledAt

    private LocalDate slotDate;          // TimeSlots.SlotDate
    private LocalTime startTime;         // TimeSlots.StartTime
    private LocalTime endTime;           // TimeSlots.EndTime

    private int subscriptionId;          // mã gói
    private String type;                 // PTSingle | PTCouple | SingleSession
    private LocalDate packageStartDate;  // thời hạn gói (buổi lẻ = null)
    private LocalDate packageEndDate;
    private int holderCustomerId;        // người đứng tên gói
    private Integer partnerCustomerId;   // người đi kèm (chỉ gói đôi)
    private String packageName;          // tên gói lúc mua
    private int sessionNo;               // số thứ tự buổi trong gói theo ngày giờ → "buổi 31/45"
    private int totalSessions;           // tổng số buổi của gói (GeneratedSessionCount)

    private String ptName;               // tên PT
    private String ptPhone;              // SĐT PT
    private String holderName;           // tên người đứng tên gói
    private String partnerFullName;      // tên người đi kèm

    // ---------------- Phần 2: Service tính thêm ----------------
    private String typeLabel;            // "PT cá nhân" | "PT đôi" | "Buổi lẻ"
    private String partnerName;          // "người tập cùng" = người còn lại so với người đang xem
    private String statusKey;            // today | upcoming | warn | pending | checkedIn | missed | cancelled → JSP chọn màu
    private String statusLabel;          // chữ hiện trên badge
    private String explanation;          // lý do / giải thích (thanh chi tiết)
    private String zaloUrl;              // https://zalo.me/<SĐT PT>
    private String calendarUrl;          // link "Thêm vào Google Calendar" (chỉ buổi chưa diễn ra)

    /** Gói đôi? */
    public boolean isCouple() {
        return "PTCouple".equals(type);
    }

    /** Buổi lẻ? */
    public boolean isSingleSession() {
        return "SingleSession".equals(type);
    }

    // ---------------- Getter / Setter ----------------
    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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

    public Integer getPartnerCustomerId() { return partnerCustomerId; }
    public void setPartnerCustomerId(Integer partnerCustomerId) { this.partnerCustomerId = partnerCustomerId; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public int getSessionNo() { return sessionNo; }
    public void setSessionNo(int sessionNo) { this.sessionNo = sessionNo; }

    public int getTotalSessions() { return totalSessions; }
    public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }

    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }

    public String getPtPhone() { return ptPhone; }
    public void setPtPhone(String ptPhone) { this.ptPhone = ptPhone; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getPartnerFullName() { return partnerFullName; }
    public void setPartnerFullName(String partnerFullName) { this.partnerFullName = partnerFullName; }

    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getStatusKey() { return statusKey; }
    public void setStatusKey(String statusKey) { this.statusKey = statusKey; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getZaloUrl() { return zaloUrl; }
    public void setZaloUrl(String zaloUrl) { this.zaloUrl = zaloUrl; }

    public String getCalendarUrl() { return calendarUrl; }
    public void setCalendarUrl(String calendarUrl) { this.calendarUrl = calendarUrl; }
}
