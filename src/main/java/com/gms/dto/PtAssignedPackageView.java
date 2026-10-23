package com.gms.dto;

import com.gms.entity.Package;
import com.gms.util.ZaloUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MỘT THẺ KHÁCH trong khối "Khách phụ trách" của PT Dashboard (1 gói PT hoặc 1 buổi lẻ).
 *
 * Ai điền gì:
 *   PtDashboardDAO.findAssignedPackages      → tên, loại gói, hạn, số buổi còn, mã gói, SĐT
 *   PtDashboardService.getAssignedPackages   → nhãn loại gói + danh sách cảnh báo (attentionItems)
 *   Tự tính trong getter                     → link Zalo từ SĐT, độ ưu tiên sắp xếp
 *
 * JSP đọc qua attribute assignedPackages, ví dụ:
 *   "Mai Đỗ & Lan Chi" · PT đôi · còn 6 buổi · đến 25/10/2026 · [Gọi] [Zalo]
 */
public class PtAssignedPackageView {

    // ---------- DAO điền qua constructor ----------
    private final String customerName;            // Người đứng tên gói, vd "Mai Đỗ"
    private final String partnerName;             // Người tập cùng (chỉ gói PT đôi), null nếu không có
    private final Package.Type ptSubscriptionType; // PT_SINGLE / PT_COUPLE / SINGLE_SESSION
    private final LocalDate ptEndDate;            // Hạn gói (Subscriptions.EndDate)
    private final int scheduledSessionCount;      // Số buổi Scheduled chưa bắt đầu của cả gói

    // ---------- DAO điền qua setter ----------
    private int ptSubscriptionId;                 // Mã gói — Service dùng để ghép cảnh báo đúng gói
    private String customerPhone;                 // SĐT người đứng tên
    private String partnerPhone;                  // SĐT người tập cùng

    // ---------- Service điền ----------
    private String ptSubscriptionTypeLabel;       // "PT cá nhân" / "PT đôi" / "Buổi lẻ"
    private final List<PtAttentionView> attentionItems = new ArrayList<>(); // Cảnh báo của gói này


    /** DAO gọi, mỗi dòng SQL 1 lần. */
    public PtAssignedPackageView(String customerName, String partnerName,
            Package.Type ptSubscriptionType, LocalDate ptEndDate, int scheduledSessionCount) {
        this.customerName = customerName;
        this.partnerName = partnerName;
        this.ptSubscriptionType = ptSubscriptionType;
        this.ptEndDate = ptEndDate;
        this.scheduledSessionCount = scheduledSessionCount;
    }


    // ---------- Getter tự tính ----------

    /** Link Zalo của người đứng tên, vd https://zalo.me/0912345678. Không có SĐT → null, JSP ẩn nút. */
    public String getCustomerZaloUrl() {
        return ZaloUtil.toUrl(customerPhone);
    }

    /** Link Zalo của người tập cùng (gói đôi). */
    public String getPartnerZaloUrl() {
        return ZaloUtil.toUrl(partnerPhone);
    }

    /**
     * Service sắp xếp theo số này (mergeClientAttention):
     * 0 = có buổi rơi vào ngày nghỉ (phải dời) · 1 = có cảnh báo khác · 2 = bình thường.
     */
    public int getAttentionPriority() {
        for (PtAttentionView item : attentionItems) {
            if ("closedDay".equals(item.getKind())) {
                return 0;
            }
        }
        return attentionItems.isEmpty() ? 2 : 1;
    }


    // ---------- Getter / setter thường ----------

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
