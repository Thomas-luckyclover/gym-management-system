package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.ServiceResult;
import com.gms.dto.StaffLeaveFormView;
import com.gms.dto.StaffingPreview;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.LeaveRequest;
import com.gms.entity.Person;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Luồng của PT/Lễ tân: xem form, dự báo quân số, nộp đơn và rút đơn của mình. */
public class StaffLeaveService {

    private final LeaveRequestDAO leaveRequestDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;
    private final PersonDAO personDAO;
    private final SystemConfigService systemConfigService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public StaffLeaveService() {
        this(new LeaveRequestDAO(), new AccountDAO(), new EmployeeDAO(), new PersonDAO(),
                new SystemConfigService(), new AuditLogService(), new NotificationService());
    }

    /** Test truyền DAO giả để kiểm tra nghiệp vụ mà không cần kết nối database. */
    StaffLeaveService(LeaveRequestDAO leaveRequestDAO, AccountDAO accountDAO, EmployeeDAO employeeDAO,
            PersonDAO personDAO, SystemConfigService systemConfigService, AuditLogService auditLogService,
            NotificationService notificationService) {
        this.leaveRequestDAO = leaveRequestDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.personDAO = personDAO;
        this.systemConfigService = systemConfigService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    // ===== Màn nộp đơn của nhân viên (màn #25, UC-14) =====

    /**
     * Lấy dữ liệu để hiện form nộp đơn nghỉ phép của PT/Lễ tân (màn #25).
     *
     * @param accountId AccountId của nhân viên từ session, không lấy từ request
     *                  parameter
     * @return view gồm thông tin nhân viên, hạn nộp P-12 và các đơn đã nộp; hoặc
     *         thông báo lỗi
     */
    public ServiceResult<StaffLeaveFormView> getStaffLeaveForm(int accountId) {
        try {
            // B1: tìm nhân viên từ tài khoản đăng nhập
            Employee employee = findEmployeeForAccount(accountId);
            if (employee == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "nhân viên"));
            }

            // B2: lấy họ tên từ Persons để hiện trên form
            Person person = personDAO.findById(employee.getPersonId());
            String fullName = person == null ? "" : person.getFullName();

            // B3: đọc giờ chốt nộp đơn P-12 và định biên P-13, P-14 từ SystemConfigs
            LocalTime submissionCutoff = LocalTime.parse(
                    systemConfigService.getString(
                            SystemConfigService.LEAVE_SUBMIT_DEADLINE));
            // B4: cùng phép dự báo với khi người dùng đổi ngày/ca trên form.
            StaffingPreview preview = loadStaffingPreview(employee, DateTimeUtil.today().plusDays(1), "FullDay");

            // B5: lấy 10 đơn gần nhất của chính nhân viên này
            List<LeaveRequest> myRequests = leaveRequestDAO.findByEmployeeId(employee.getEmployeeId(), 10);

            return ServiceResult.ok(new StaffLeaveFormView(
                    employee.getEmployeeId(),
                    employee.getEmployeeCode(),
                    fullName,
                    employee.getPosition().getDbValue(),
                    submissionCutoff,
                    preview.getMinPt(),
                    preview.getMinReceptionist(),
                    preview.getRemainingPt(),
                    preview.getRemainingReceptionist(),
                    myRequests));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Dự báo quân số của ca đã chọn nếu đơn của nhân viên này được duyệt (SRS 5.2.1).
     * Chỉ trừ 1 người ở đúng chức vụ của người nộp.
     *
     * @param accountId AccountId của nhân viên từ session
     * @param leaveDate ngày nghỉ đang chọn trên form
     * @param shiftType ca nghỉ đang chọn (Morning, Afternoon, FullDay)
     * @return số PT, Lễ tân còn lại và định biên tối thiểu; hoặc thông báo lỗi
     */
    public ServiceResult<StaffingPreview> previewStaffing(int accountId, LocalDate leaveDate, String shiftType) {
        // B1: kiểm tra tham số
        if (leaveDate == null || LeaveRequest.ShiftType.fromDbValue(shiftType) == null) {
            return ServiceResult.fail("Ngày hoặc ca nghỉ không hợp lệ.");
        }

        try {
            // B2: tìm nhân viên đang đăng nhập
            Employee employee = findEmployeeForAccount(accountId);
            if (employee == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "nhân viên"));
            }

            return ServiceResult.ok(loadStaffingPreview(employee, leaveDate, shiftType));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Dự báo cùng một công thức cho lần mở form và khi đổi ngày/ca. */
    private StaffingPreview loadStaffingPreview(Employee employee, LocalDate date, String shiftType) throws SQLException {
        int remainingPt = leaveRequestDAO.countOnDutyAfterApproval(date, shiftType, "PT", 0);
        int remainingReceptionist = leaveRequestDAO.countOnDutyAfterApproval(date, shiftType, "Receptionist", 0);
        // DAO trừ người xin nghỉ; cộng lại ở chức vụ không phải của người nộp.
        if (employee.getPosition() == Role.PT) {
            remainingReceptionist += 1;
        } else {
            remainingPt += 1;
        }
        int minPt = systemConfigService.getInt(SystemConfigService.MIN_PT_PER_SHIFT);
        int minReceptionist = systemConfigService.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT);
        return new StaffingPreview(remainingPt, remainingReceptionist, minPt, minReceptionist);
    }

    /**
     * Nhân viên nộp đơn xin nghỉ phép (màn #25, UC-14).
     * Kiểm tra: hạn chốt nộp P-12 (CBR-84), trùng lịch nghỉ, và buổi dạy Scheduled
     * của PT (CBR-83).
     *
     * @param accountId AccountId của nhân viên từ session
     * @param leaveType loại nghỉ được gửi mới (Annual hoặc Unpaid)
     * @param leaveDate ngày nghỉ (phải > hôm nay)
     * @param shiftType ca nghỉ (Morning, Afternoon, FullDay)
     * @param reason    lý do nghỉ (5–500 ký tự)
     * @return mã đơn mới tạo (LeaveRequestId), hoặc thông báo lỗi cụ thể
     */
    public ServiceResult<Integer> submitLeaveRequest(
            int accountId,
            LeaveRequest.LeaveType leaveType,
            LocalDate leaveDate,
            LeaveRequest.ShiftType shiftType,
            String reason) {

        return submitLeaveRequest(accountId, leaveType, leaveDate, leaveDate, shiftType, reason);
    }

    /** Nộp đơn theo khoảng ngày gồm cả hai biên; nửa ngày chỉ áp dụng cho một ngày. */
    public ServiceResult<Integer> submitLeaveRequest(int accountId, LeaveRequest.LeaveType leaveType,
            LocalDate leaveDate, LocalDate toDate, LeaveRequest.ShiftType shiftType, String reason) {

        // B1: validate dữ liệu đầu vào cơ bản
        if (leaveType == null) {
            return ServiceResult.failField("leaveType", "Vui lòng chọn loại nghỉ.");
        }
        // Đơn mới chỉ nhận phép năm hoặc không lương; Sick chỉ giữ để đọc đơn cũ.
        if (leaveType != LeaveRequest.LeaveType.ANNUAL && leaveType != LeaveRequest.LeaveType.UNPAID) {
            return ServiceResult.failField("leaveType", "Chỉ hỗ trợ nghỉ phép năm hoặc nghỉ không lương.");
        }
        if (shiftType == null) {
            return ServiceResult.failField("shiftType", "Vui lòng chọn ca nghỉ.");
        }
        if (leaveDate == null) {
            return ServiceResult.failField("leaveDate", "Vui lòng chọn ngày nghỉ.");
        }

        if (toDate == null || toDate.isBefore(leaveDate)
                || ChronoUnit.DAYS.between(leaveDate, toDate) >= 31
                || leaveDate.getYear() != toDate.getYear()) {
            return ServiceResult.failField("toDate", "Chọn ngày kết thúc từ ngày bắt đầu, tối đa 31 ngày trong cùng năm.");
        }
        if (!leaveDate.equals(toDate) && shiftType != LeaveRequest.ShiftType.FULL_DAY) {
            return ServiceResult.failField("shiftType", "Đơn nhiều ngày phải chọn Cả ngày.");
        }

        LocalDate today = DateTimeUtil.today();
        if (!leaveDate.isAfter(today)) {
            return ServiceResult.failField("leaveDate", "Ngày nghỉ phải sau ngày hôm nay.");
        }

        if (!ValidationUtil.isLengthValid(reason, 5, 500)) {
            return ServiceResult.failField("reason", "Lý do nghỉ phải từ 5 đến 500 ký tự.");
        }

        try {
            // B2: tìm thông tin nhân viên theo tài khoản
            Employee employee = findEmployeeForAccount(accountId);
            if (employee == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "nhân viên"));
            }

            // B3: kiểm tra hạn chốt nộp đơn P-12 (CBR-84: mặc định 15:00 ngày liền trước
            // ngày nghỉ)
            LocalTime submissionCutoff = LocalTime.parse(
                    systemConfigService.getString(
                            SystemConfigService.LEAVE_SUBMIT_DEADLINE));
            LocalDateTime deadline = leaveDate.minusDays(1).atTime(submissionCutoff);
            LocalDateTime now = DateTimeUtil.now();

            if (now.isAfter(deadline)) {
                return ServiceResult.fail(String.format(
                        Messages.MSG77_LEAVE_SUBMISSION_DEADLINE,
                        leaveDate,
                        submissionCutoff));
            }

            // B4: kiểm tra trùng đơn nghỉ Pending hoặc Approved (cùng ca hoặc bao phủ
            // FullDay)
            boolean hasConflict;
            try (Connection connection = DBContext.getConnection()) {
                hasConflict = leaveRequestDAO.hasConflictingLeave(connection,
                        employee.getEmployeeId(), leaveDate, toDate, shiftType.getDbValue());
            }
            if (hasConflict) {
                return ServiceResult.fail(
                        "Bạn đã có đơn nghỉ trùng ngày và ca này đang chờ duyệt hoặc đã được duyệt.");
            }

            // B5: đối với PT, kiểm tra buổi dạy Scheduled trong ngày và ca xin nghỉ
            // (CBR-83)
            boolean isPt = Role.PT == employee.getPosition();
            if (isPt) {
                for (LocalDate date = leaveDate; !date.isAfter(toDate); date = date.plusDays(1)) {
                    int clashCount = leaveRequestDAO.countScheduledSessions(
                            employee.getEmployeeId(), date, shiftType.getDbValue());
                    if (clashCount > 0) {
                        return ServiceResult.fail(String.format(Messages.MSG78_LEAVE_BLOCKED_BY_SESSIONS, clashCount)
                                + " (" + DateTimeUtil.formatDate(date) + ")");
                    }
                }
            }

            // B6: tạo entity và mở Transaction lưu đơn + ghi AuditLog + thông báo cho Manager (BF-05)
            LeaveRequest request = new LeaveRequest();
            request.setEmployeeId(employee.getEmployeeId());
            request.setLeaveType(leaveType);
            request.setFromDate(leaveDate);
            request.setToDate(toDate);
            request.setShiftType(shiftType);
            request.setDays(requestedDays(request));
            request.setReason(reason.trim());

            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    leaveRequestDAO.lockDecisionScope(conn);
                    if (leaveRequestDAO.hasConflictingLeave(conn, employee.getEmployeeId(), leaveDate, toDate, shiftType.getDbValue())) {
                        conn.rollback();
                        return ServiceResult.fail("Bạn đã có đơn nghỉ trùng ngày và ca này.");
                    }
                    int newLeaveRequestId = leaveRequestDAO.insert(conn, request);
                    if (newLeaveRequestId <= 0) {
                        conn.rollback();
                        return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                    }

                    // B6.1: ghi nhật ký kiểm toán (Audit Log)
                    auditLogService.logInTransaction(
                            conn,
                            accountId,
                            employee.getPosition(),
                            "Nộp đơn nghỉ phép",
                            "LeaveRequests",
                            newLeaveRequestId,
                            "Status",
                            null,
                            "Pending",
                            reason.trim());

                    // B6.2: gửi thông báo đến chuông của tất cả Manager (theo sơ đồ BF-05)
                    String notifTitle = "Đơn xin nghỉ phép mới";
                    String notifContent = "Nhân viên " + employee.getEmployeeCode()
                            + " xin nghỉ " + formatLeavePeriod(request)
                            + " (" + shiftType.getDisplayName() + ").";
                    notificationService.notifyRoleInTransaction(
                            conn, Role.MANAGER, notifTitle, notifContent);

                    conn.commit();
                    return ServiceResult.ok(newLeaveRequestId);
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                }
            }

        } catch (SQLException e) {
            // Lỗi trùng khóa duy nhất (2601/2627): đơn trùng ngày + ca vừa được tạo (bấm nộp 2 lần)
            if (e.getErrorCode() == 2601 || e.getErrorCode() == 2627) {
                return ServiceResult.fail(
                        "Bạn đã có đơn nghỉ trùng ngày và ca này đang chờ duyệt hoặc đã được duyệt.");
            }
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Nhân viên tự rút đơn xin nghỉ phép còn Pending của mình (BF-05).
     *
     * @param accountId      AccountId của nhân viên từ session
     * @param leaveRequestId mã đơn xin nghỉ cần rút
     * @return ok() nếu thành công, fail() kèm thông báo lỗi cụ thể
     */
    public ServiceResult<Void> withdrawRequest(int accountId, int leaveRequestId) {
        if (leaveRequestId <= 0) {
            return ServiceResult.fail("Mã đơn không hợp lệ.");
        }

        try {
            // B1: tìm thông tin nhân viên theo tài khoản đăng nhập
            Employee employee = findEmployeeForAccount(accountId);
            if (employee == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "nhân viên"));
            }

            // B2: mở transaction để xóa đơn Pending và ghi Audit Log
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    leaveRequestDAO.lockDecisionScope(conn);
                    boolean isDeleted = leaveRequestDAO.withdraw(conn, leaveRequestId, employee.getEmployeeId());
                    if (!isDeleted) {
                        conn.rollback();
                        return ServiceResult.fail("Không thể rút đơn (đơn không tồn tại, đã được xử lý hoặc không phải của bạn).");
                    }

                    // B2.1: ghi Audit Log ghi nhận nhân viên tự rút đơn
                    auditLogService.logInTransaction(
                            conn,
                            accountId,
                            employee.getPosition(),
                            "Rút đơn nghỉ phép",
                            "LeaveRequests",
                            leaveRequestId,
                            "Status",
                            "Pending",
                            "Withdrawn",
                            "Nhân viên tự rút đơn");

                    conn.commit();
                    return ServiceResult.ok(null);

                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Khoảng ngày dùng trong thông báo; không bỏ mất ngày kết thúc của đơn nhiều ngày. */
    private String formatLeavePeriod(LeaveRequest request) {
        String period = DateTimeUtil.formatDate(request.getFromDate());
        if (!request.getFromDate().equals(request.getToDate())) {
            period = period + "–" + DateTimeUtil.formatDate(request.getToDate());
        }
        return period;
    }

    /** Số ngày theo quy tắc hiện có: nửa ngày cho một ca, đủ ngày cho FullDay; tính cả hai ngày biên. */
    private BigDecimal requestedDays(LeaveRequest request) {
        if (request.getShiftType() != LeaveRequest.ShiftType.FULL_DAY) {
            return new BigDecimal("0.5");
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(
                request.getFromDate(), request.getToDate()) + 1);
    }

    /** Tìm Employee đang Working theo AccountId (Manager duyệt đơn). */
    private Employee findEmployeeForAccount(int accountId) throws SQLException {
        Account account = accountDAO.findById(accountId);
        if (account == null || account.getStatus() != Account.Status.ACTIVE) {
            return null;
        }
        return employeeDAO.findByPersonId(account.getPersonId());
    }
}
