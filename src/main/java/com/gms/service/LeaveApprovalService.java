package com.gms.service;

import com.gms.dto.LeaveApprovalReview;
import com.gms.dto.LeaveBalance;
import com.gms.dto.ReviewCheck;
import com.gms.dto.ShiftStaffingRow;
import com.gms.dao.LeaveRequestDAO;
import com.gms.entity.LeaveRequest;
import com.gms.util.DateTimeUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Chỉ kiểm tra điều kiện nghỉ phép; không ghi dữ liệu, không tự mở transaction. */
public class LeaveApprovalService {

    private final LeaveRequestDAO leaveRequestDAO;
    private final SystemConfigService systemConfigService;

    public LeaveApprovalService(LeaveRequestDAO leaveRequestDAO, SystemConfigService systemConfigService) {
        this.leaveRequestDAO = leaveRequestDAO;
        this.systemConfigService = systemConfigService;
    }

    /**
     * Cùng một phép kiểm tra cho GET và POST. Khi duyệt thật, bên gọi truyền
     * Connection đã khóa để đọc lại quỹ phép, nhân sự và lịch dạy trước khi ghi.
     * workingPosition phải được đọc từ nhân viên còn Working, không lấy từ form.
     */
    public LeaveApprovalReview review(Connection connection, LeaveRequest request, String workingPosition)
            throws SQLException {
        LeaveApprovalReview review = new LeaveApprovalReview();

        // B1: kiểm tra ngày/ca/số ngày trước khi dùng chúng trong các truy vấn.
        String inputError = validateRequest(request);
        if (inputError != null) {
            review.setInputError(inputError);
            review.getChecks().add(new ReviewCheck("REQUEST", "Thông tin đơn", true, false, inputError));
            return review;
        }

        // B2: gom đủ ba điều kiện để màn chi tiết giải thích từng mục bị chặn.
        review.getChecks().add(checkAnnualBalance(connection, request, review));
        review.getChecks().add(checkStaffing(connection, request, workingPosition, review));
        review.getChecks().add(checkSessions(connection, request, workingPosition));
        return review;
    }

    /** Kiểm tra dữ liệu đã lưu, kể cả khi người dùng tự gửi POST ngoài giao diện. */
    private String validateRequest(LeaveRequest request) {
        LocalDate fromDate = request.getFromDate();
        LocalDate toDate = request.getToDate();
        if (fromDate == null || toDate == null || request.getShiftType() == null || request.getLeaveType() == null) {
            return "Khoảng ngày hoặc ca nghỉ không hợp lệ.";
        }
        if (toDate.isBefore(fromDate) || ChronoUnit.DAYS.between(fromDate, toDate) >= 31
                || fromDate.getYear() != toDate.getYear()) {
            return "Khoảng ngày hoặc ca nghỉ không hợp lệ.";
        }
        if (!fromDate.equals(toDate) && request.getShiftType() != LeaveRequest.ShiftType.FULL_DAY) {
            return "Đơn nhiều ngày phải nghỉ cả ngày.";
        }
        if (fromDate.isBefore(DateTimeUtil.today())) {
            return "Không thể duyệt đơn nghỉ phép của ngày đã qua.";
        }
        BigDecimal expectedDays = new BigDecimal("0.5");
        if (request.getShiftType() == LeaveRequest.ShiftType.FULL_DAY) {
            expectedDays = BigDecimal.valueOf(ChronoUnit.DAYS.between(fromDate, toDate) + 1);
        }
        if (request.getDays() == null || request.getDays().compareTo(expectedDays) != 0) {
            return "Số ngày nghỉ không khớp ngày và ca. Vui lòng kiểm tra lại đơn.";
        }
        return null;
    }

    /** Phép năm: Approved và Pending khác cùng giữ quỹ; đơn hiện tại không bị trừ hai lần. */
    private ReviewCheck checkAnnualBalance(Connection connection, LeaveRequest request, LeaveApprovalReview review)
            throws SQLException {
        ReviewCheck check = new ReviewCheck("ANNUAL_QUOTA", "Quỹ phép năm", true, false, null);
        if (request.getLeaveType() != LeaveRequest.LeaveType.ANNUAL) {
            check.setApplicable(false);
            check.setOk(true);
            check.setMessage("Không áp dụng cho " + request.getLeaveType().getDisplayName() + ".");
            return check;
        }
        int quota;
        try {
            quota = systemConfigService.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA);
        } catch (IllegalStateException e) {
            check.setMessage("Chưa cấu hình quỹ phép năm P-23. Cần thiết lập chính sách trước khi duyệt.");
            return check;
        }
        int excludedRequestId = request.isPending() ? request.getLeaveRequestId() : 0;
        LeaveBalance balance = leaveRequestDAO.getAnnualBalance(connection, request.getEmployeeId(),
                request.getFromDate().getYear(), quota, excludedRequestId);
        review.setBalance(balance);
        boolean enough = balance.getRemainingDays().compareTo(request.getDays()) >= 0;
        check.setOk(enough);
        if (enough) {
            check.setMessage("Còn " + balance.getRemainingDays() + " ngày phép năm; đơn này xin " + request.getDays() + " ngày.");
        } else {
            check.setMessage("Vượt quá số phép năm còn lại: còn " + balance.getRemainingDays()
                    + ", xin " + request.getDays() + " ngày. Kiểm tra cả các đơn đang chờ.");
        }
        return check;
    }

    /** Đếm từng ngày và từng ca; giả định nhân viên đang làm việc làm cả hai ca. */
    private ReviewCheck checkStaffing(Connection connection, LeaveRequest request, String position, LeaveApprovalReview review)
            throws SQLException {
        ReviewCheck check = new ReviewCheck("STAFFING", "Định biên nhân sự", true, false, null);
        if (!"PT".equals(position) && !"Receptionist".equals(position)) {
            check.setMessage("Nhân viên đã nghỉ việc hoặc không còn ở vị trí PT/Lễ tân đang làm việc.");
            return check;
        }
        int minRequired;
        if ("PT".equals(position)) {
            minRequired = systemConfigService.getInt(SystemConfigService.MIN_PT_PER_SHIFT);
        } else {
            minRequired = systemConfigService.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT);
        }
        review.setMinStaffRequired(minRequired);
        List<String> shifts = new ArrayList<>();
        if (request.getShiftType() == LeaveRequest.ShiftType.FULL_DAY) {
            shifts.add("Morning");
            shifts.add("Afternoon");
        } else {
            shifts.add(request.getShiftType().getDbValue());
        }
        int remaining = Integer.MAX_VALUE;
        String firstShortage = null;
        for (LocalDate date = request.getFromDate(); !date.isAfter(request.getToDate()); date = date.plusDays(1)) {
            for (String shift : shifts) {
                int after = leaveRequestDAO.countOnDutyAfterApproval(connection, date, shift, position, request.getLeaveRequestId());
                remaining = Math.min(remaining, after);
                String shiftLabel = "Morning".equals(shift) ? "Ca sáng" : "Ca chiều";
                review.getStaffingRows().add(new ShiftStaffingRow(DateTimeUtil.formatDate(date) + " · " + shiftLabel,
                        position, after + 1, after, minRequired));
                if (after < minRequired && firstShortage == null) {
                    firstShortage = DateTimeUtil.formatDate(date) + " · " + shiftLabel + ": còn " + after
                            + " người, tối thiểu " + minRequired + ". Cần bố trí người trước khi duyệt.";
                }
            }
        }
        review.setRemainingStaff(remaining);
        check.setOk(firstShortage == null);
        if (firstShortage != null) {
            check.setMessage(firstShortage);
        } else {
            check.setMessage("Đạt trên toàn khoảng nghỉ: còn ít nhất " + remaining + " người, tối thiểu " + minRequired
                    + ". Tạm tính nhân viên đang làm việc làm cả hai ca.");
        }
        return check;
    }

    /** Lịch PT: kiểm tra cả ngày cuối của đơn, không chỉ ngày bắt đầu. */
    private ReviewCheck checkSessions(Connection connection, LeaveRequest request, String position) throws SQLException {
        ReviewCheck check = new ReviewCheck("SESSIONS", "Lịch dạy PT", "PT".equals(position), true, null);
        if (!check.isApplicable()) {
            check.setMessage("Chỉ áp dụng cho PT đang làm việc.");
            return check;
        }
        int total = 0;
        LocalDate firstConflictDate = null;
        for (LocalDate date = request.getFromDate(); !date.isAfter(request.getToDate()); date = date.plusDays(1)) {
            int count = leaveRequestDAO.countScheduledSessions(connection, request.getEmployeeId(), date,
                    request.getShiftType().getDbValue());
            total += count;
            if (count > 0 && firstConflictDate == null) {
                firstConflictDate = date;
            }
        }
        check.setOk(total == 0);
        if (total == 0) {
            check.setMessage("Không có lịch dạy PT bị trùng trong khoảng nghỉ.");
        } else {
            check.setMessage(DateTimeUtil.formatDate(firstConflictDate) + ": trùng " + total
                    + " buổi dạy PT trong khoảng nghỉ. Nhờ PT xử lý lịch trước khi duyệt.");
        }
        return check;
    }
}
