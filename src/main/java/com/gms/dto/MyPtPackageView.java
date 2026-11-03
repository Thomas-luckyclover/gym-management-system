package com.gms.dto;

import java.time.LocalDate;

/**
 * DTO 1 GÓI PT của khách — DAO TẠO: MyPtSessionDAO.findMyPackages (Subscriptions + Orders + PT + người tập cùng);
 * Service ĐIỀN thêm: MyBookingService.fillPackage (typeLabel, zaloUrl, partnerName).
 * JSP đọc: my-bookings.jsp (biến "sp" = gói đang xem) và _booking-package.jspf (biến "p" = 1 thẻ gói ở cột trái).
 * Hàm tính sẵn: isActive() = tab Đang tập / Đã kết thúc · getProgressPercent() = % vòng tròn "còn N buổi".
 */
public class MyPtPackageView {
    // ===== IN RA MÀN =====
    // ← Subscriptions.SubscriptionId → link ?package=<id> của thẻ gói (bấm để xem gói)
    private int subscriptionId;
    // ← tên gói (snapshot trên đơn) → tên thẻ gói, tiêu đề cột phải
    private String packageName;
    // ← Subscriptions.StartDate → "dd/MM/yyyy – …" dưới tên gói
    private LocalDate startDate;
    // ← Subscriptions.EndDate → ngày hết gói, câu "Gói hết hạn ngày …"
    private LocalDate endDate;
    // ← tổng số buổi của gói → vòng tròn "còn N buổi" (= total − past), "x/y đã qua"
    private int totalSessions;
    // ← DAO đếm buổi có ngày tập TRƯỚC hôm nay → vòng tròn, "x/y đã qua"
    private int pastSessions;
    // ← tên PT → "PT …" trên thẻ gói, khung "PT của gói"
    private String ptName;
    // ← SĐT PT → nút gọi PT
    private String ptPhone;
    // ← fillPackage: "Gói PT đơn" / "Gói PT đôi" → dòng phụ dưới tên gói
    private String typeLabel;
    // ← fillPackage: tên NGƯỜI KIA của gói đôi (mình là người đứng tên → tên người tập cùng, và ngược lại) → "… cùng <tên>"
    private String partnerName;
    // ← fillPackage: ZaloUtil.toUrl(ptPhone) → nút "Nhắn Zalo"
    private String zaloUrl;

    // ===== DÙNG ĐỂ TÍNH (không in thẳng) =====
    // ← Subscriptions.SubscriptionType (PTSingle / PTCouple) → isCouple(), typeLabel
    private String type;
    // ← Subscriptions.Status → isActive()
    private String status;
    // ← người đứng tên → fillPackage biết mình là ai
    private int holderCustomerId;
    // ← Orders.PartnerCustomerId (gói đôi)
    private Integer partnerCustomerId;
    // ← tên người đứng tên → partnerName
    private String holderName;
    // ← tên người tập cùng → partnerName
    private String partnerFullName;

    public boolean isCouple() {
        return "PTCouple".equals(type);
    }

    public boolean isActive() {
        return "Active".equals(status);
    }

    public int getProgressPercent() {
        return totalSessions == 0 ? 0 : Math.min(100, pastSessions * 100 / totalSessions);
    }

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
