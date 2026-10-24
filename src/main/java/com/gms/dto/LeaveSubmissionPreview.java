package com.gms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Service tính từ quỹ còn sau giữ chỗ Pending; Servlet trả JSON, JS hiện bản chia ngày trước khi gửi. */
public class LeaveSubmissionPreview {

    // Hai biên do Service xác thực, tính số ngày gồm cả hai biên.
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final int totalDays;
    // Phần Annual luôn là những ngày đầu; 0 khi chủ động chọn Unpaid hoặc hết phép.
    private final int annualDays;
    private final int unpaidDays;
    // Quỹ của năm nghỉ, đã trừ Approved và các đơn Pending khác.
    private final BigDecimal remainingAnnualDays;
    private final boolean needsUnpaidConsent;
    // Scheduled trong toàn khoảng nghỉ; JS chặn gửi và dẫn PT tới lịch hiện có.
    private final int sessionConflicts;
    // Chỉ là dự báo ngày đầu cho ô i hiện có; backend duyệt kiểm tra toàn khoảng.
    private final StaffingPreview staffing;

    public LeaveSubmissionPreview(LocalDate fromDate, LocalDate toDate, int annualDays, int unpaidDays,
            BigDecimal remainingAnnualDays, boolean needsUnpaidConsent, int sessionConflicts, StaffingPreview staffing) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.totalDays = annualDays + unpaidDays;
        this.annualDays = annualDays;
        this.unpaidDays = unpaidDays;
        this.remainingAnnualDays = remainingAnnualDays;
        this.needsUnpaidConsent = needsUnpaidConsent;
        this.sessionConflicts = sessionConflicts;
        this.staffing = staffing;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }
    public LocalDate getToDate() {
        return toDate;
    }
    public int getTotalDays() {
        return totalDays;
    }
    public int getAnnualDays() {
        return annualDays;
    }
    public int getUnpaidDays() {
        return unpaidDays;
    }
    public BigDecimal getRemainingAnnualDays() {
        return remainingAnnualDays;
    }
    public boolean isNeedsUnpaidConsent() {
        return needsUnpaidConsent;
    }
    public int getSessionConflicts() {
        return sessionConflicts;
    }
    public StaffingPreview getStaffing() {
        return staffing;
    }
}
