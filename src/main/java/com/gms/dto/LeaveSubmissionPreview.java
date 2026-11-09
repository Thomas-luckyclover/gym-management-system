package com.gms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Service tính phương án nghỉ; FormServlet chuyển DTO sang JSP khi bấm Xem dự kiến. */
public class LeaveSubmissionPreview {

    // Hai biên do Service xác thực, tính số ngày gồm cả hai biên.
    private final LocalDate fromDate;
    private final LocalDate toDate;
    // Phần Annual luôn là những ngày đầu; 0 khi chủ động chọn Unpaid hoặc hết phép.
    private final int annualDays;
    private final int unpaidDays;
    // Quỹ của năm nghỉ, đã trừ Approved và các đơn Pending khác.
    private final BigDecimal remainingAnnualDays;
    private final boolean needsUnpaidConsent;
    // Số buổi Scheduled trong khoảng nghỉ; JSP hiện cảnh báo và khóa nút nộp.
    private final int sessionConflicts;
    // DAO đọc các buổi Scheduled trùng ngày/ca; JSP lặp để hiện từng buổi.
    private final List<ConflictingSession> conflictingSessions;
    // Quân số thấp nhất toàn khoảng nghỉ; chỉ là dự báo, khi duyệt sẽ kiểm tra lại.
    private final StaffingPreview staffing;

    public LeaveSubmissionPreview(LocalDate fromDate, LocalDate toDate, int annualDays, int unpaidDays,
            BigDecimal remainingAnnualDays, boolean needsUnpaidConsent, List<ConflictingSession> conflictingSessions, StaffingPreview staffing) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.annualDays = annualDays;
        this.unpaidDays = unpaidDays;
        this.remainingAnnualDays = remainingAnnualDays;
        this.needsUnpaidConsent = needsUnpaidConsent;
        this.conflictingSessions = List.copyOf(conflictingSessions);
        this.sessionConflicts = conflictingSessions.size();
        this.staffing = staffing;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }
    public LocalDate getToDate() {
        return toDate;
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

    public List<ConflictingSession> getConflictingSessions() {
        return conflictingSessions;
    }

    /** Một buổi chặn gửi đơn: DAO đọc TimeSlots, JSP hiện ngày và khoảng giờ. */
    public static class ConflictingSession {
        // TimeSlots.SlotDate: ngày dạy.
        private final LocalDate slotDate;
        // TimeSlots.StartTime/EndTime: khoảng giờ dạy.
        private final LocalTime startTime;
        private final LocalTime endTime;

        public ConflictingSession(LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
            this.slotDate = slotDate;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public LocalDate getSlotDate() { return slotDate; }
        public LocalTime getStartTime() { return startTime; }
        public LocalTime getEndTime() { return endTime; }
    }
}
