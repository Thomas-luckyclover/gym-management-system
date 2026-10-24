package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.ServiceResult;
import com.gms.dto.LeaveBalance;
import com.gms.dto.LeaveSubmissionPreview;
import com.gms.dto.LeaveRequestGroupView;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

            // B3: đọc giờ chốt nộp đơn P-12 từ SystemConfigs.
            LocalTime submissionCutoff = LocalTime.parse(
                    systemConfigService.getString(
                            SystemConfigService.LEAVE_SUBMIT_DEADLINE));
            // B4: cùng phép dự báo với khi người dùng đổi ngày/ca trên form.
            StaffingPreview preview = loadStaffingPreview(employee, DateTimeUtil.today().plusDays(1), "FullDay");

            // B5: lấy 10 đơn gần nhất của chính nhân viên này
            List<LeaveRequest> myRequests = leaveRequestDAO.findByEmployeeId(employee.getEmployeeId(), 10);

            // Nhóm đủ hai phần kể cả khi phần thứ hai nằm ngoài 10 dòng mới nhất.
            Map<String, LeaveRequestGroupView> groups = new LinkedHashMap<>();
            for (LeaveRequest request : myRequests) {
                String key = request.getLeaveGroupId();
                if (key == null) {
                    key = "single-" + request.getLeaveRequestId();
                }
                if (!groups.containsKey(key)) {
                    List<LeaveRequest> parts;
                    if (request.getLeaveGroupId() == null) {
                        parts = new ArrayList<>();
                        parts.add(request);
                    } else {
                        parts = leaveRequestDAO.findByLeaveGroup(request.getLeaveGroupId(), employee.getEmployeeId());
                    }
                    groups.put(key, new LeaveRequestGroupView(parts));
                }
            }
            StaffLeaveFormView view = new StaffLeaveFormView(employee.getEmployeeId(), employee.getEmployeeCode(),
                    fullName, employee.getPosition().getDbValue(), submissionCutoff, preview,
                    new ArrayList<>(groups.values()));
            return ServiceResult.ok(view);

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

    /** Nộp đơn theo khoảng ngày gồm cả hai biên; ca sáng/chiều vẫn tính một ngày nghỉ. */
    public ServiceResult<Integer> submitLeaveRequest(int accountId, LeaveRequest.LeaveType leaveType,
            LocalDate leaveDate, LocalDate toDate, LeaveRequest.ShiftType shiftType, String reason) {
        return submitLeaveRequest(accountId, leaveType, leaveDate, toDate, shiftType, reason, false, -1, -1);
    }

    /** Hai phần chỉ được lưu khi người gửi đồng ý đúng phương án phân bổ vừa xem. */
    public ServiceResult<Integer> submitLeaveRequest(int accountId, LeaveRequest.LeaveType leaveType,
            LocalDate leaveDate, LocalDate toDate, LeaveRequest.ShiftType shiftType, String reason,
            boolean acceptUnpaid, int expectedAnnualDays, int expectedUnpaidDays) {

        // B1: kiểm tra ngày, ca, loại nghỉ và lý do trước khi truy vấn.
        ServiceResult<Void> validation = validateLeavePeriod(leaveType, leaveDate, toDate, shiftType);
        if (!validation.isSuccess()) {
            return ServiceResult.failFields(validation.getFieldErrors());
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

            try (Connection connection = DBContext.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    leaveRequestDAO.lockDecisionScope(connection);
                    String currentPosition = leaveRequestDAO.findWorkingPosition(connection, employee.getEmployeeId());
                    if (!employee.getPosition().getDbValue().equals(currentPosition)) {
                        connection.rollback();
                        return ServiceResult.fail("Thông tin nhân viên đã thay đổi. Vui lòng đăng nhập lại.");
                    }
                    if (leaveRequestDAO.hasConflictingLeave(connection, employee.getEmployeeId(), leaveDate, toDate, shiftType.getDbValue())) {
                        connection.rollback();
                        return ServiceResult.fail("Bạn đã có đơn nghỉ trùng ngày và ca này.");
                    }
                    // B4: khóa chung với duyệt/rút đơn, kiểm tra lại quỹ và lịch trong cùng transaction.
                    if (DateTimeUtil.now().isAfter(deadline)) {
                        connection.rollback();
                        return ServiceResult.fail("Đã quá hạn gửi đơn. Vui lòng báo quản lý.");
                    }
                    int conflicts = countSessionConflicts(connection, employee, leaveDate, toDate, shiftType);
                    if (conflicts > 0) {
                        connection.rollback();
                        return ServiceResult.failField("sessions", String.format(Messages.MSG78_LEAVE_BLOCKED_BY_SESSIONS, conflicts));
                    }
                    LeaveSubmissionPreview plan = calculateSubmission(connection, employee, leaveType, leaveDate, toDate, null);
                    if (plan.isNeedsUnpaidConsent() && !acceptUnpaid) {
                        connection.rollback();
                        return ServiceResult.failField("acceptUnpaid", "Không đủ phép năm. Xem phương án chuyển phần vượt sang không lương và xác nhận trước khi gửi.");
                    }
                    if (acceptUnpaid && leaveType == LeaveRequest.LeaveType.ANNUAL
                            && (expectedAnnualDays != plan.getAnnualDays() || expectedUnpaidDays != plan.getUnpaidDays())) {
                        connection.rollback();
                        return ServiceResult.failField("acceptUnpaid", "Quỹ phép đã thay đổi. Vui lòng xem lại số ngày và xác nhận phương án mới.");
                    }

                    // B5: tạo một hoặc hai phần, sau đó lưu và ghi audit bằng cùng Connection.
                    List<LeaveRequest> parts = createRequestParts(employee, plan, shiftType, reason);
                    int newLeaveRequestId = saveRequestParts(connection, accountId, employee, parts);
                    // B6: chỉ gửi một thông báo cho cả yêu cầu; lỗi bất kỳ bước nào rollback cả hai dòng.
                    String content = "Nhân viên " + employee.getEmployeeCode() + " xin nghỉ "
                            + DateTimeUtil.formatDate(leaveDate) + "–" + DateTimeUtil.formatDate(toDate)
                            + " (" + shiftType.getDisplayName() + ").";
                    if (parts.size() > 1) {
                        content += " Gồm " + plan.getAnnualDays() + " ngày phép năm và " + plan.getUnpaidDays() + " ngày không lương.";
                    }
                    notificationService.notifyRoleInTransaction(connection, Role.MANAGER, "Đơn xin nghỉ phép mới", content);

                    connection.commit();
                    return ServiceResult.ok(newLeaveRequestId);
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                }
            }

        } catch (IllegalStateException e) {
            return ServiceResult.fail("Chưa cấu hình quỹ phép năm. Vui lòng liên hệ quản lý.");
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

    /** Xem số ngày từng phần và lịch PT trước khi gửi; không ghi hoặc giữ chỗ từ GET. */
    public ServiceResult<LeaveSubmissionPreview> previewSubmission(int accountId, LeaveRequest.LeaveType leaveType,
            LocalDate fromDate, LocalDate toDate, LeaveRequest.ShiftType shiftType) {
        ServiceResult<Void> validation = validateLeavePeriod(leaveType, fromDate, toDate, shiftType);
        if (!validation.isSuccess()) {
            return ServiceResult.fail(validation.getMessage());
        }
        try {
            Employee employee = findEmployeeForAccount(accountId);
            if (employee == null) {
                return ServiceResult.fail("Không tìm thấy nhân viên đang làm việc.");
            }
            StaffingPreview staffing = loadStaffingPreview(employee, fromDate, shiftType.getDbValue());
            try (Connection connection = DBContext.getConnection()) {
                LeaveSubmissionPreview plan = calculateSubmission(connection, employee, leaveType, fromDate, toDate, staffing);
                int conflicts = countSessionConflicts(connection, employee, fromDate, toDate, shiftType);
                return ServiceResult.ok(new LeaveSubmissionPreview(fromDate, toDate, plan.getAnnualDays(), plan.getUnpaidDays(),
                        plan.getRemainingAnnualDays(), plan.isNeedsUnpaidConsent(), conflicts, staffing));
            }
        } catch (IllegalStateException e) {
            return ServiceResult.fail("Chưa cấu hình quỹ phép năm. Vui lòng liên hệ quản lý.");
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Dùng cùng công thức cho preview và POST; POST truyền connection đã khóa. */
    private LeaveSubmissionPreview calculateSubmission(Connection connection, Employee employee,
            LeaveRequest.LeaveType leaveType, LocalDate fromDate, LocalDate toDate, StaffingPreview staffing) throws SQLException {
        // B1: tính số ngày bao gồm cả ngày bắt đầu và ngày kết thúc.
        int totalDays = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        int annualDays = 0;
        BigDecimal remainingAnnualDays = BigDecimal.ZERO;
        // B2: Annual dùng tối đa quỹ còn sau giữ chỗ; Unpaid không đọc quỹ phép.
        if (leaveType == LeaveRequest.LeaveType.ANNUAL) {
            int quota = systemConfigService.getInt(SystemConfigService.LEAVE_ANNUAL_QUOTA);
            LeaveBalance balance = leaveRequestDAO.getAnnualBalance(connection, employee.getEmployeeId(), fromDate.getYear(), quota);
            remainingAnnualDays = balance.getRemainingDays();
            // Quy tắc mới dùng đơn vị nguyên ngày; số dư lịch sử 0,5 không đủ cho một ngày mới.
            annualDays = Math.min(totalDays, remainingAnnualDays.intValue());
        }
        // B3: phần không dùng Annual chuyển sang đề nghị Unpaid; POST còn yêu cầu đồng ý.
        int unpaidDays = totalDays - annualDays;
        return new LeaveSubmissionPreview(fromDate, toDate, annualDays, unpaidDays, remainingAnnualDays,
                leaveType == LeaveRequest.LeaveType.ANNUAL && unpaidDays > 0, 0, staffing);
    }

    /** PT cần giải quyết tất cả Scheduled trong khoảng nghỉ, không chỉ ngày đầu. */
    private int countSessionConflicts(Connection connection, Employee employee, LocalDate fromDate, LocalDate toDate,
            LeaveRequest.ShiftType shiftType) throws SQLException {
        int total = 0;
        if (employee.getPosition() == Role.PT) {
            for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
                total += leaveRequestDAO.countScheduledSessions(connection, employee.getEmployeeId(), date, shiftType.getDbValue());
            }
        }
        return total;
    }

    /** Từ phương án đã xác nhận, tạo Annual trước rồi Unpaid; chỉ gắn mã đợt nếu có hai phần. */
    private List<LeaveRequest> createRequestParts(Employee employee, LeaveSubmissionPreview plan,
            LeaveRequest.ShiftType shift, String reason) {
        boolean split = plan.getAnnualDays() > 0 && plan.getUnpaidDays() > 0;
        String groupId = null;
        if (split) {
            // UUID tạo mã đợt duy nhất; cả hai phần dùng cùng mã này, không tạo bảng mới.
            groupId = UUID.randomUUID().toString();
        }
        List<LeaveRequest> parts = new ArrayList<>();
        LocalDate firstDate = plan.getFromDate();
        if (plan.getAnnualDays() > 0) {
            LocalDate lastAnnualDate = firstDate.plusDays(plan.getAnnualDays() - 1L);
            parts.add(createRequest(employee, LeaveRequest.LeaveType.ANNUAL, firstDate,
                    lastAnnualDate, shift, reason, groupId));
        }
        if (plan.getUnpaidDays() > 0) {
            LocalDate firstUnpaidDate = firstDate.plusDays(plan.getAnnualDays());
            parts.add(createRequest(employee, LeaveRequest.LeaveType.UNPAID, firstUnpaidDate,
                    plan.getToDate(), shift, reason, groupId));
        }
        return parts;
    }

    /** Lưu các phần và audit; trả mã phần đầu. Bên gọi chịu trách nhiệm commit hoặc rollback cả đợt. */
    private int saveRequestParts(Connection connection, int accountId, Employee employee,
            List<LeaveRequest> parts) throws SQLException {
        int firstRequestId = 0;
        for (LeaveRequest part : parts) {
            int partId = leaveRequestDAO.insert(connection, part);
            if (partId <= 0) {
                throw new SQLException("Không lưu được phần đơn nghỉ phép.");
            }
            if (firstRequestId == 0) {
                firstRequestId = partId;
            }
            auditLogService.logInTransaction(connection, accountId, employee.getPosition(), "Nộp đơn nghỉ phép",
                    "LeaveRequests", partId, "Status", null, "Pending", part.getReason());
        }
        return firstRequestId;
    }

    /** Tạo một dòng Annual/Unpaid đã tính ngày; không thay đổi ngày/ca người gửi lựa chọn. */
    private LeaveRequest createRequest(Employee employee, LeaveRequest.LeaveType type, LocalDate fromDate,
            LocalDate toDate, LeaveRequest.ShiftType shift, String reason, String groupId) {
        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employee.getEmployeeId());
        request.setLeaveType(type);
        request.setFromDate(fromDate);
        request.setToDate(toDate);
        request.setShiftType(shift);
        request.setDays(requestedDays(request));
        request.setReason(reason.trim());
        request.setLeaveGroupId(groupId);
        return request;
    }

    /** Kiểm tra loại, ngày và ca trước mọi truy vấn; không nhận đơn quá khứ hoặc khác năm. */
    private ServiceResult<Void> validateLeavePeriod(LeaveRequest.LeaveType leaveType, LocalDate leaveDate,
            LocalDate toDate, LeaveRequest.ShiftType shiftType) {
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

        return ServiceResult.ok(null);
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
            try (Connection connection = DBContext.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    leaveRequestDAO.lockDecisionScope(connection);
                    boolean isDeleted = leaveRequestDAO.withdraw(connection, leaveRequestId, employee.getEmployeeId());
                    if (!isDeleted) {
                        connection.rollback();
                        return ServiceResult.fail("Không thể rút đơn (đơn không tồn tại, đã được xử lý hoặc không phải của bạn).");
                    }

                    // B2.1: ghi Audit Log ghi nhận nhân viên tự rút đơn
                    auditLogService.logInTransaction(
                            connection,
                            accountId,
                            employee.getPosition(),
                            "Rút đơn nghỉ phép",
                            "LeaveRequests",
                            leaveRequestId,
                            "Status",
                            "Pending",
                            "Withdrawn",
                            "Nhân viên tự rút đơn");

                    connection.commit();
                    return ServiceResult.ok(null);

                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Mỗi ngày trong khoảng nghỉ tính một ngày, không chia đôi theo ca sáng/chiều. */
    private BigDecimal requestedDays(LeaveRequest request) {
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(
                request.getFromDate(), request.getToDate()) + 1);
    }

    /** Chỉ PT/Lễ tân còn Working, tài khoản Active mới được gửi/xem dữ liệu của chính mình. */
    private Employee findEmployeeForAccount(int accountId) throws SQLException {
        Account account = accountDAO.findById(accountId);
        if (account == null || account.getStatus() != Account.Status.ACTIVE) {
            return null;
        }
        Employee employee = employeeDAO.findByPersonId(account.getPersonId());
        if (employee == null || employee.getStatus() != Employee.Status.WORKING
                || (employee.getPosition() != Role.PT && employee.getPosition() != Role.RECEPTIONIST)) {
            return null;
        }
        return employee;
    }
}
