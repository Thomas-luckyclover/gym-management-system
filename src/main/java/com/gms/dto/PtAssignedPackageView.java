package com.gms.dto;

import com.gms.entity.Package;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu một gói PT hoặc một buổi lẻ trong khối Khách phụ trách.
 * PtDashboardDAO.findAssignedPackages tạo DTO và điền năm field dữ liệu gốc.
 * PtDashboardService.getAssignedPackages bổ sung nhãn loại gói vào chính DTO này.
 * Servlet truyền List<PtAssignedPackageView> bằng attribute assignedPackages cho JSP.
 * Gói đôi dùng một DTO chứa hai tên; DTO không chứa danh sách buổi hay nhóm ngày.
 */
public class PtAssignedPackageView {

    // ===== 1. FIELD =====

    // ----- 1.1. PtDashboardDAO điền qua constructor: dữ liệu gốc -----

    // PtDashboardDAO.findAssignedPackages: đọc Persons.FullName của khách đứng tên gói.
    // Nối Subscriptions.CustomerId = Customers.CustomerId và Customers.PersonId = Persons.PersonId.
    // JSP đọc assignedPackage.customerName, in bằng c:out, ví dụ “Nguyễn Văn A”.
    private final String customerName;

    // PtDashboardDAO.findAssignedPackages: đọc Persons.FullName của người cùng gói PT đôi.
    // Nối Subscriptions.OrderId = Orders.OrderId, Orders.PartnerCustomerId = Customers.CustomerId,
    // rồi Customers.PersonId = Persons.PersonId; chỉ lấy người cùng gói khi loại gói là PTCouple.
    // JSP nối “& Đỗ Lan Chi” sau customerName; null với PT cá nhân/buổi lẻ thì bỏ phần này.
    private final String partnerName;

    // PtDashboardDAO.findAssignedPackages: đọc Subscriptions.SubscriptionType, chuyển bằng Package.Type.fromDbValue.
    // Chỉ có PT_SINGLE, PT_COUPLE hoặc SINGLE_SESSION trong khối này.
    // Không in enum lên trang; Service đọc field này để chọn ptSubscriptionTypeLabel.
    private final Package.Type ptSubscriptionType;

    // PtDashboardDAO.findAssignedPackages: đọc Subscriptions.EndDate của gói PT hoặc buổi lẻ.
    // JSP dùng gms:formatDate, in “Hạn sử dụng” và ngày “25/10/2026” cho cả gói PT/buổi lẻ.
    // Buổi lẻ có StartDate = EndDate; đây là hạn sử dụng, không khẳng định ngày dạy thực tế sau khi dời buổi.
    private final LocalDate ptEndDate;

    // PtDashboardDAO.findAssignedPackages: COUNT các Sessions cùng SubscriptionId với gói đang xét.
    // Nối Sessions.TimeSlotId = TimeSlots.TimeSlotId; chỉ đếm Sessions.Status = Scheduled
    // và TimeSlots.SlotDate + TimeSlots.StartTime sau thời điểm Service truyền vào DAO.
    // Đếm trên cả gói, không giới hạn 7/14 ngày và không lấy GeneratedSessionCount trừ số buổi đã chốt.
    // JSP in nhãn “Còn trong lịch” và giá trị “6 buổi”; gói đôi dùng chung số buổi, buổi lẻ được chọn có 1 buổi.
    private final int scheduledSessionCount;

    // ----- 1.2. PtDashboardService bổ sung qua setter: nhãn hiển thị -----

    // PtDashboardService.getAssignedPackages, B4: đọc getPtSubscriptionType() để chọn nhãn.
    // PT_SINGLE → “PT cá nhân”; PT_COUPLE → “PT đôi”; SINGLE_SESSION → “Buổi lẻ”.
    // JSP đọc assignedPackage.ptSubscriptionTypeLabel, in dưới tên khách.
    private String ptSubscriptionTypeLabel;

    // DAO đọc SubscriptionId và SĐT từ Persons; JSP hiện số và ghép cảnh báo đúng gói.
    private int ptSubscriptionId;
    private String customerPhone;
    private String partnerPhone;
    private final List<PtAttentionView> attentionItems = new ArrayList<>();

    // ===== 2. CONSTRUCTOR: DAO điền năm field nhóm 1.1; Service điền nhóm 1.2 sau =====

    /**
     * Nhận một dòng dữ liệu gói PT/buổi lẻ mà DAO đã đọc từ database.
     * @param customerName tên khách đứng tên gói
     * @param partnerName tên người cùng gói đôi, null với PT cá nhân/buổi lẻ
     * @param ptSubscriptionType loại gói PT cá nhân, PT đôi hoặc buổi lẻ
     * @param ptEndDate hạn sử dụng gói PT/buổi lẻ từ Subscriptions.EndDate
     * @param scheduledSessionCount số buổi Scheduled chưa bắt đầu của cả gói
     */
    public PtAssignedPackageView(String customerName, String partnerName,
            Package.Type ptSubscriptionType, LocalDate ptEndDate, int scheduledSessionCount) {
        // B1: nhận tên khách đứng gói và người cùng gói từ DAO.
        this.customerName = customerName;
        this.partnerName = partnerName;

        // B2: nhận loại gói, hạn gói và số buổi còn trong lịch từ DAO.
        this.ptSubscriptionType = ptSubscriptionType;
        this.ptEndDate = ptEndDate;
        this.scheduledSessionCount = scheduledSessionCount;
    }

    // ===== 3. GETTER / SETTER: cùng thứ tự với field ở phần 1 =====

    // ----- 3.1. Getter nhóm DAO (1.1): chỉ đọc dữ liệu gốc, không có setter -----

    public int getPtSubscriptionId() { return ptSubscriptionId; }
    public void setPtSubscriptionId(int value) { ptSubscriptionId = value; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String value) { customerPhone = value; }
    public String getPartnerPhone() { return partnerPhone; }
    public void setPartnerPhone(String value) { partnerPhone = value; }
    public List<PtAttentionView> getAttentionItems() { return attentionItems; }

    /** Đưa thẻ có buổi ngày nghỉ lên trước, tiếp đến cảnh báo khác và khách bình thường. */
    public int getAttentionPriority() {
        for (PtAttentionView item : attentionItems) {
            if ("closedDay".equals(item.getKind())) {
                return 0;
            }
        }
        return attentionItems.isEmpty() ? 2 : 1;
    }

    /** JSP dùng customerName để in tên khách đứng tên gói bằng c:out. */
    public String getCustomerName() {
        return customerName;
    }

    /** JSP dùng partnerName để nối tên người cùng gói; null thì không hiện. */
    public String getPartnerName() {
        return partnerName;
    }

    /** Service đọc loại gói để chọn nhãn “PT cá nhân”, “PT đôi” hoặc “Buổi lẻ”. */
    public Package.Type getPtSubscriptionType() {
        return ptSubscriptionType;
    }

    /** JSP định dạng ngày dd/MM/yyyy để in hạn sử dụng gói PT/buổi lẻ. */
    public LocalDate getPtEndDate() {
        return ptEndDate;
    }

    /** JSP dùng scheduledSessionCount để in số buổi Scheduled chưa bắt đầu. */
    public int getScheduledSessionCount() {
        return scheduledSessionCount;
    }

    // ----- 3.2. Getter / setter nhóm Service (1.2): bổ sung nhãn vào DTO DAO đã tạo -----

    /** JSP dùng ptSubscriptionTypeLabel để in “PT cá nhân”, “PT đôi” hoặc “Buổi lẻ”. */
    public String getPtSubscriptionTypeLabel() {
        return ptSubscriptionTypeLabel;
    }

    /** Service gán nhãn đã chọn từ ptSubscriptionType; setter này không cập nhật database. */
    public void setPtSubscriptionTypeLabel(String ptSubscriptionTypeLabel) {
        this.ptSubscriptionTypeLabel = ptSubscriptionTypeLabel;
    }
}
