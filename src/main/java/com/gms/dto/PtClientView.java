package com.gms.dto;

import java.time.LocalDate;

/**
 * 1 khách trong khối "Khách của tôi" trên PT Dashboard — chỉ khách được giao cho PT đang đăng nhập (GBR-10).
 *
 * Ai tạo: SubscriptionDAO.findClientsOfPt, PtDashboardService gắn nhãn.
 * Ai dùng: pt/dashboard.jsp.
 *
 * VD: "Nguyễn Văn Tâm", KH-1007, gói PTCouple hết hạn 23/09, người đi kèm → partner = true.
 *
 * @author Le Cong Minh
 */
public class PtClientView {

    private final String fullName;
    private final String customerCode;
    private final String sourceType;
    private final LocalDate endDate;
    private final String status;
    private final boolean partner;

    private String packageLabel;
    private String endDateText;
    private boolean frozen;
    private boolean endingSoon;

    /**
     * @param fullName     họ tên khách.
     * @param customerCode mã khách, VD "KH-1007".
     * @param sourceType   loại gói PT: PTSingle | PTCouple | SingleSession.
     * @param endDate      ngày cuối gói PT còn dùng được (GBR-03).
     * @param status       Subscriptions.Status (xem ngày cũ có thể là Expired / CheckedIn).
     * @param partner      true nếu khách là người đi kèm trong gói couple (không đứng tên).
     */
    public PtClientView(String fullName, String customerCode, String sourceType, LocalDate endDate,
            String status, boolean partner) {
        this.fullName = fullName;
        this.customerCode = customerCode;
        this.sourceType = sourceType;
        this.endDate = endDate;
        this.status = status;
        this.partner = partner;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public String getSourceType() {
        return sourceType;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPartner() {
        return partner;
    }

    /** @return VD "Gói PT cá nhân". */
    public String getPackageLabel() {
        return packageLabel;
    }

    public void setPackageLabel(String packageLabel) {
        this.packageLabel = packageLabel;
    }

    /** @return ngày hết hạn dạng dd/MM/yyyy (GBR-01). */
    public String getEndDateText() {
        return endDateText;
    }

    public void setEndDateText(String endDateText) {
        this.endDateText = endDateText;
    }

    /** @return true nếu gói PT đang bảo lưu. */
    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    /** @return true nếu gói PT hết hạn trong 7 ngày kể từ ngày đang xem — PT nên nhắc khách gia hạn. */
    public boolean isEndingSoon() {
        return endingSoon;
    }

    public void setEndingSoon(boolean endingSoon) {
        this.endingSoon = endingSoon;
    }
}
