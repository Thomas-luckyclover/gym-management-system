package com.gms.dto;

import java.time.LocalDate;

/**
 * 1 GÓI PT (cá nhân / đôi) của khách — 1 thẻ ở cột trái màn "Gói của tôi".
 *   Phần 1: DAO đọc từ DB (MyPtSessionDAO.findMyPackages).
 *   Phần 2: Service tính thêm (MyBookingService).
 */
public class MyPtPackageView {

    // ---------------- Phần 1: DAO đọc từ DB ----------------
    private int subscriptionId;          // mã gói
    private String type;                 // PTSingle | PTCouple
    private String status;               // Subscriptions.Status: Active = đang tập, còn lại = đã kết thúc
    private String packageName;          // tên gói lúc mua
    private LocalDate startDate;         // thời hạn gói
    private LocalDate endDate;
    private int totalSessions;           // tổng số buổi (GeneratedSessionCount)
    private int pastSessions;            // số buổi có ngày tập trước hôm nay → thanh tiến độ
    private int holderCustomerId;        // người đứng tên
    private Integer partnerCustomerId;   // người đi kèm (gói đôi)
    private String ptName;               // tên PT phụ trách
    private String ptPhone;              // SĐT PT
    private String holderName;           // tên người đứng tên
    private String partnerFullName;      // tên người đi kèm

    // ---------------- Phần 2: Service tính thêm ----------------
    private String typeLabel;            // "PT cá nhân" | "PT đôi"
    private String partnerName;          // "người tập cùng" = người còn lại so với người đang xem
    private String zaloUrl;              // https://zalo.me/<SĐT PT>

    /** Gói đôi? */
    public boolean isCouple() {
        return "PTCouple".equals(type);
    }

    /** Gói còn đang tập? (Subscriptions.Status = Active) */
    public boolean isActive() {
        return "Active".equals(status);
    }

    /** % thanh tiến độ (0–100). */
    public int getProgressPercent() {
        return totalSessions == 0 ? 0 : Math.min(100, pastSessions * 100 / totalSessions);
    }

    // ---------------- Getter / Setter ----------------
    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public int getTotalSessions() { return totalSessions; }
    public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }

    public int getPastSessions() { return pastSessions; }
    public void setPastSessions(int pastSessions) { this.pastSessions = pastSessions; }

    public int getHolderCustomerId() { return holderCustomerId; }
    public void setHolderCustomerId(int holderCustomerId) { this.holderCustomerId = holderCustomerId; }

    public Integer getPartnerCustomerId() { return partnerCustomerId; }
    public void setPartnerCustomerId(Integer partnerCustomerId) { this.partnerCustomerId = partnerCustomerId; }

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

    public String getZaloUrl() { return zaloUrl; }
    public void setZaloUrl(String zaloUrl) { this.zaloUrl = zaloUrl; }
}
