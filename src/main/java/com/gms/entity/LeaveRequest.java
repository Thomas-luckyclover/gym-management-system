package com.gms.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Đơn xin nghỉ phép của nhân viên PT/Lễ tân (BF-05, UC-14, UC-15), ánh xạ bảng LeaveRequests.
 *
 * <p>DAO là nơi duy nhất tạo đối tượng này từ ResultSet; Service dùng để kiểm tra luật và
 * cập nhật trạng thái; không dùng trực tiếp để hiển thị JSP (JSP dùng DTO).</p>
 */
public class LeaveRequest {

    // DAO: LeaveRequests.LeaveRequestId, mã đơn tự tăng.
    private int leaveRequestId;

    // DAO: LeaveRequests.EmployeeId, nhân viên nộp đơn; Service so với employeeId lấy từ session.
    private int employeeId;

    // DAO: LeaveRequests.ReviewedByEmployeeId; NULL khi đơn còn Pending hoặc hệ thống tự từ chối (CBR-85).
    private Integer reviewedByEmployeeId;

    // DAO: LeaveRequests.LeaveType (Annual | Sick | Unpaid).
    private LeaveType leaveType;

    // DAO: LeaveRequests.LeaveDate, ngày xin nghỉ; phải sau ngày nộp (CK_LeaveRequests_Submit).
    private LocalDate leaveDate;

    // DAO: LeaveRequests.ShiftType (Morning | Afternoon | FullDay).
    private ShiftType shiftType;

    // DAO: LeaveRequests.Reason, lý do nghỉ 5-500 ký tự.
    private String reason;

    // DAO: LeaveRequests.Status (Pending | Approved | Rejected), DBR-44.
    private Status status;

    // DAO: LeaveRequests.RejectReason; chỉ có khi Rejected, 5-500 ký tự.
    private String rejectReason;

    // DAO: LeaveRequests.SubmittedAt, thời điểm nộp; dùng đối chiếu hạn nộp P-12 (CBR-84).
    private LocalDateTime submittedAt;

    // DAO: LeaveRequests.ReviewedAt; có khi Approved hoặc Rejected (kể cả tự từ chối).
    private LocalDateTime reviewedAt;

    // DAO: LeaveRequests.AssignedToEmployeeId, người được giao duyệt (NULL = Quản lý chung).
    private Integer assignedToEmployeeId;

    // DAO: LeaveRequests.FromDate, ngày bắt đầu nghỉ.
    private LocalDate fromDate;

    // DAO: LeaveRequests.ToDate, ngày kết thúc nghỉ.
    private LocalDate toDate;

    // DAO: LeaveRequests.Days, số ngày nghỉ (0.5 hoặc số ngày nguyên).
    private java.math.BigDecimal days;

    // DAO: LeaveRequests.ReminderSentAt; job nhắc Manager đúng 1 lần, NULL nếu chưa nhắc.
    private LocalDateTime reminderSentAt;

    public LeaveRequest() {
    }

    public int getLeaveRequestId() {
        return leaveRequestId;
    }

    public void setLeaveRequestId(int leaveRequestId) {
        this.leaveRequestId = leaveRequestId;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(int employeeId) {
        this.employeeId = employeeId;
    }

    public Integer getReviewedByEmployeeId() {
        return reviewedByEmployeeId;
    }

    public void setReviewedByEmployeeId(Integer reviewedByEmployeeId) {
        this.reviewedByEmployeeId = reviewedByEmployeeId;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getLeaveDate() {
        return leaveDate != null ? leaveDate : fromDate;
    }

    public void setLeaveDate(LocalDate leaveDate) {
        this.leaveDate = leaveDate;
        if (this.fromDate == null) {
            this.fromDate = leaveDate;
        }
        if (this.toDate == null) {
            this.toDate = leaveDate;
        }
    }

    public LocalDate getFromDate() {
        return fromDate != null ? fromDate : leaveDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
        if (this.leaveDate == null) {
            this.leaveDate = fromDate;
        }
    }

    public LocalDate getToDate() {
        return toDate != null ? toDate : (fromDate != null ? fromDate : leaveDate);
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public java.math.BigDecimal getDays() {
        return days;
    }

    public void setDays(java.math.BigDecimal days) {
        this.days = days;
    }

    public Integer getAssignedToEmployeeId() {
        return assignedToEmployeeId;
    }

    public void setAssignedToEmployeeId(Integer assignedToEmployeeId) {
        this.assignedToEmployeeId = assignedToEmployeeId;
    }

    public ShiftType getShiftType() {
        return shiftType;
    }

    public void setShiftType(ShiftType shiftType) {
        this.shiftType = shiftType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getReminderSentAt() {
        return reminderSentAt;
    }

    public void setReminderSentAt(LocalDateTime reminderSentAt) {
        this.reminderSentAt = reminderSentAt;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }

    /** Loại nghỉ; giá trị DB khớp CK_LeaveRequests_Type. */
    public enum LeaveType {

        ANNUAL("Annual", "Nghỉ phép năm"),
        SICK("Sick", "Nghỉ ốm"),
        UNPAID("Unpaid", "Nghỉ không lương");

        private final String dbValue;
        private final String displayName;

        LeaveType(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static LeaveType fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }

            for (LeaveType type : values()) {
                if (type.dbValue.equalsIgnoreCase(dbValue)) {
                    return type;
                }
            }

            return null;
        }
    }

    /** Ca xin nghỉ; giá trị DB khớp CK_LeaveRequests_Shift. */
    public enum ShiftType {

        MORNING("Morning", "Ca sáng"),
        AFTERNOON("Afternoon", "Ca chiều"),
        FULL_DAY("FullDay", "Cả ngày");

        private final String dbValue;
        private final String displayName;

        ShiftType(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static ShiftType fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }

            for (ShiftType shift : values()) {
                if (shift.dbValue.equalsIgnoreCase(dbValue)) {
                    return shift;
                }
            }

            return null;
        }
    }

    /** Trạng thái đơn; giá trị DB khớp CK_LeaveRequests_Status (DBR-44). */
    public enum Status {

        PENDING("Pending", "Chờ duyệt"),
        APPROVED("Approved", "Đã duyệt"),
        REJECTED("Rejected", "Từ chối");

        private final String dbValue;
        private final String displayName;

        Status(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Status fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }

            for (Status status : values()) {
                if (status.dbValue.equalsIgnoreCase(dbValue)) {
                    return status;
                }
            }

            return null;
        }
    }
}
