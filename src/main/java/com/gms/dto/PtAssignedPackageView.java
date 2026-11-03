package com.gms.dto;

import com.gms.entity.Package;
import com.gms.util.ZaloUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO 1 KHÁCH PHỤ TRÁCH (1 gói PT / buổi lẻ) — DAO TẠO: PtDashboardDAO.findAssignedPackages;
 * Service ĐIỀN: getAssignedPackages (nhãn loại gói) + mergeClientAttention (gắn việc cần chú ý, xếp ưu tiên).
 * Servlet: "assignedPackages" → pt/dashboard.jsp, khung "Khách phụ trách" (biến client).
 * Hàm tính sẵn: getCustomerZaloUrl / getPartnerZaloUrl (nút Zalo) · getAttentionPriority (0 phòng nghỉ, 1 có việc, 2 bình thường → xếp lên đầu).
 */
public class PtAssignedPackageView {
    // ===== IN RA MÀN =====
    // ← Persons.FullName người đứng tên → tên khách
    private final String customerName;
    // ← tên người tập cùng (PTCouple) → "& <tên>", nhãn tên cạnh SĐT
    private final String partnerName;
    // ← Service typeLabel → "PT cá nhân" | "PT đôi" | "Buổi lẻ"
    private String ptSubscriptionTypeLabel;
    // ← đếm buổi Scheduled chưa bắt đầu → "còn N buổi"
    private final int scheduledSessionCount;
    // ← Subscriptions.EndDate → "đến dd/MM/yyyy"
    private final LocalDate ptEndDate;
    // ← Persons.Phone → nút gọi + Zalo người đứng tên
    private String customerPhone;
    // ← SĐT người tập cùng → nút gọi + Zalo người tập cùng
    private String partnerPhone;
    // ← mergeClientAttention: các PtAttentionView cùng gói → các khung vàng/đỏ dưới khách
    private final List<PtAttentionView> attentionItems = new ArrayList<>();

    // ===== KHÔNG IN RA MÀN =====
    // ← Subscriptions.SubscriptionId → ghép attentionItems
    private int ptSubscriptionId;
    // ← Subscriptions.SubscriptionType → nhãn loại gói
    private final Package.Type ptSubscriptionType;

    public PtAssignedPackageView(String customerName, String partnerName,
            Package.Type ptSubscriptionType, LocalDate ptEndDate, int scheduledSessionCount) {
        this.customerName = customerName;
        this.partnerName = partnerName;
        this.ptSubscriptionType = ptSubscriptionType;
        this.ptEndDate = ptEndDate;
        this.scheduledSessionCount = scheduledSessionCount;
    }

    public String getCustomerZaloUrl() {
        return ZaloUtil.toUrl(customerPhone);
    }

    public String getPartnerZaloUrl() {
        return ZaloUtil.toUrl(partnerPhone);
    }

    public int getAttentionPriority() {
        for (PtAttentionView item : attentionItems) {
            if ("closedDay".equals(item.getKind())) {
                return 0;
            }
        }
        return attentionItems.isEmpty() ? 2 : 1;
    }

    public String getCustomerName() { return customerName; }
    public String getPartnerName() { return partnerName; }
    public Package.Type getPtSubscriptionType() { return ptSubscriptionType; }
    public LocalDate getPtEndDate() { return ptEndDate; }
    public int getScheduledSessionCount() { return scheduledSessionCount; }

    public int getPtSubscriptionId() { return ptSubscriptionId; }
    public void setPtSubscriptionId(int ptSubscriptionId) { this.ptSubscriptionId = ptSubscriptionId; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getPartnerPhone() { return partnerPhone; }
    public void setPartnerPhone(String partnerPhone) { this.partnerPhone = partnerPhone; }

    public String getPtSubscriptionTypeLabel() { return ptSubscriptionTypeLabel; }
    public void setPtSubscriptionTypeLabel(String label) { this.ptSubscriptionTypeLabel = label; }

    public List<PtAttentionView> getAttentionItems() { return attentionItems; }
}
