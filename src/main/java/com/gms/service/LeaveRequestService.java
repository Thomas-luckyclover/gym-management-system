package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveRequestItemView;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.dto.StaffLeaveFormView;
import com.gms.entity.Employee;
import com.gms.entity.LeaveRequest;
import com.gms.entity.Person;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.PaginationUtil;
import com.gms.util.ValidationUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ đơn xin nghỉ phép của nhân viên (BF-05, UC-14, UC-15, JOB-07).
 */
public class LeaveRequestService {

    // Đơn còn dưới số phút này đến hạn duyệt thì đánh dấu khẩn cấp (tô nổi trên danh sách)
    private static final long URGENT_WITHIN_MINUTES = 180;

    // DAO truy vấn bảng LeaveRequests; Service không tự viết SQL
    private final LeaveRequestDAO leaveRequestDAO;

    // DAO đọc Employees (lấy EmployeeId từ AccountId khi Manager duyệt)
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;

    // DAO đọc Persons (lấy họ tên nhân viên hiển thị trên form nộp đơn)
    private final PersonDAO personDAO;

    // Đọc tham số P-20 (giờ chốt duyệt đơn), P-13/P-14 (min staffing)
    private final SystemConfigService systemConfigService;

    // Ghi audit log mỗi khi Manager duyệt / từ chối
    private final AuditLogService auditLogService;

    // Thông báo cho nhân viên sau khi đơn được xử lý
    private final NotificationService notificationService;

    public LeaveRequestService() {
        leaveRequestDAO = new LeaveRequestDAO();
        accountDAO = new AccountDAO();
        employeeDAO = new EmployeeDAO();
        personDAO = new PersonDAO();
        systemConfigService = new SystemConfigService();
        auditLogService = new AuditLogService();
        notificationService = new NotificationService();
    }

    /**
     * Lấy một trang đơn nghỉ phép cho màn danh sách của Manager (màn #26, UC-15).
     *
     * @param keyword  từ khóa tên nhân viên từ URL, có thể null/rỗng
     * @param status   tab trạng thái từ URL (Pending, Approved, Rejected, All), có thể null
     * @param page     số trang, bắt đầu từ 1
     * @param pageSize số dòng mỗi trang
     * @return trang các dòng đã tính hạn duyệt và đếm ngược, hoặc thông báo lỗi hệ thống
     */
    public ServiceResult<PageResult<LeaveRequestItemView>> getManagerPage(
            String keyword, String status, int page, int pageSize) {

        try {
            // B1: chuẩn hóa tham số URL thành bộ lọc hợp lệ
            String trimmedKeyword = keyword == null ? "" : keyword.trim();
            LeaveRequestFilter filter = new LeaveRequestFilter(
                    parseStatusFilter(status), trimmedKeyword);

            // B2: đếm tổng và lấy đúng một trang (DAO cắt bằng OFFSET/FETCH)
            int totalItems = leaveRequestDAO.countByFilter(filter);
            int offset = PaginationUtil.calculateOffset(page, pageSize);
            List<LeaveRequestDAO.LeaveRequestRow> rows
                    = leaveRequestDAO.findManagerPage(filter, offset, pageSize);

            // B3: đổi từng dòng DAO sang view, thêm hạn duyệt P-20 và đếm ngược
            LocalTime approvalTime = LocalTime.parse(
                    systemConfigService.getString(
                            SystemConfigService.LEAVE_APPROVED_DEADLINE));
            LocalDateTime now = DateTimeUtil.now();

            List<LeaveRequestItemView> items = new ArrayList<>();
            for (LeaveRequestDAO.LeaveRequestRow row : rows) {
                items.add(toItemView(row, approvalTime, now));
            }

            return ServiceResult.ok(
                    new PageResult<>(items, page, pageSize, totalItems));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy số đơn cho các tab của màn #26 (UC-15).
     *
     * @return map có 4 khóa: All, Pending, Approved, Rejected; hoặc thông báo lỗi hệ thống
     */
    public ServiceResult<Map<String, Integer>> getStatusCounts() {
        try {
            // B1: DAO trả số đơn của 3 trạng thái thật
            Map<String, Integer> counts = leaveRequestDAO.getStatusCounts();

            // B2: tab "Tất cả" = tổng 3 trạng thái, thêm vào đầu map để JSP đọc theo khóa All
            int total = 0;
            for (int count : counts.values()) {
                total += count;
            }

            Map<String, Integer> result = new java.util.LinkedHashMap<>();
            result.put("All", total);
            result.putAll(counts);

            return ServiceResult.ok(result);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đổi tham số tab trạng thái trên URL thành Status để lọc danh sách của Manager.
     *
     * @param statusParam giá trị param "status" (Pending, Approved, Rejected, All) hoặc null/rỗng
     * @return Status tương ứng; null nghĩa là tab "Tất cả"; URL trống thì mặc định PENDING
     */
    LeaveRequest.Status parseStatusFilter(String statusParam) {
        // B1: URL không có status thì màn mặc định mở tab "Chờ duyệt"
        if (statusParam == null || statusParam.isBlank()) {
            return LeaveRequest.Status.PENDING;
        }

        // B2: tab "Tất cả" không lọc theo trạng thái
        if ("All".equalsIgnoreCase(statusParam.trim())) {
            return null;
        }

        // B3: giá trị lạ (fromDbValue trả null) cũng rơi về "Tất cả", không báo lỗi
        return LeaveRequest.Status.fromDbValue(statusParam.trim());
    }

    /**
     * Đổi một dòng DAO sang LeaveRequestItemView: tính hạn duyệt (P-20 của ngày
     * liền trước ngày nghỉ, CBR-85), chữ đếm ngược và cờ khẩn cấp cho đơn Pending.
     */
    private LeaveRequestItemView toItemView(
            LeaveRequestDAO.LeaveRequestRow row,
            LocalTime approvalTime,
            LocalDateTime now) {

        LeaveRequest request = row.getLeaveRequest();

        // B1: hạn duyệt = giờ P-20 vào ngày liền trước ngày nghỉ
        LocalDateTime approvalDeadline
                = request.getLeaveDate().minusDays(1).atTime(approvalTime);

        // B2: đơn đã xử lý không cần đếm ngược; đơn Pending thì tính số phút còn lại
        String countdownText = "";
        boolean isUrgent = false;
        if (request.isPending()) {
            long minutesLeft = Duration.between(now, approvalDeadline).toMinutes();
            countdownText = formatCountdown(minutesLeft);
            isUrgent = minutesLeft < URGENT_WITHIN_MINUTES;
        }

        // B3: tự từ chối = Rejected mà không có người duyệt (CBR-85)
        boolean isAutoRejected = request.isRejected()
                && request.getReviewedByEmployeeId() == null;

        return new LeaveRequestItemView(
                request.getLeaveRequestId(),
                request.getEmployeeId(),
                row.getEmployeeCode(),
                row.getFullName(),
                row.getPosition(),
                row.getAvatarUrl(),
                request.getLeaveType(),
                request.getLeaveDate(),
                request.getShiftType(),
                request.getReason(),
                request.getStatus(),
                request.getRejectReason(),
                isAutoRejected,
                request.getSubmittedAt(),
                request.getReviewedAt(),
                row.getReviewerName(),
                approvalDeadline,
                countdownText,
                isUrgent);
    }

    /** Đổi số phút còn lại thành chữ hiển thị; số âm nghĩa là đã quá hạn duyệt. */
    private String formatCountdown(long minutesLeft) {
        if (minutesLeft < 0) {
            return "Đã quá hạn duyệt";
        }
        if (minutesLeft < 60) {
            return "Còn " + minutesLeft + " phút";
        }
        return "Còn " + (minutesLeft / 60) + " giờ " + (minutesLeft % 60) + " phút";
    }

    // ===== Màn duyệt đơn (màn #27, UC-15) =====

    /**
     * Lấy thông tin đầy đủ 1 đơn để hiện trên màn duyệt của Manager (#27).
     *
     * @param leaveRequestId mã đơn
     * @return view đơn, hoặc thông báo lỗi nếu không tìm thấy / lỗi hệ thống
     */
    public ServiceResult<LeaveRequestDAO.LeaveReviewDetail> getReviewDetail(int leaveRequestId) {
        if (leaveRequestId <= 0) {
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
        }
        try {
            LeaveRequestDAO.LeaveReviewDetail detail =
                    leaveRequestDAO.findReviewDetail(leaveRequestId);
            if (detail == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
            }
            return ServiceResult.ok(detail);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Manager duyệt đơn nghỉ phép (UC-15).
     * Kiểm tra: đơn phải Pending, không vi phạm min-staffing (CBR-88, CBR-90, BR-14).
     *
     * @param managerAccountId AccountId của Manager từ session
     * @param leaveRequestId   mã đơn cần duyệt
     * @return ok() nếu thành công, fail() kèm thông báo lỗi nếu không được
     */
    public ServiceResult<Void> approveRequest(int managerAccountId, int leaveRequestId) {
        try {
            // B1: lấy thông tin đơn
            LeaveRequestDAO.LeaveReviewDetail detail =
                    leaveRequestDAO.findReviewDetail(leaveRequestId);
            if (detail == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
            }
            LeaveRequest request = detail.getLeaveRequest();

            // B2: chỉ duyệt đơn Pending
            if (!request.isPending()) {
                return ServiceResult.fail("Đơn này đã được xử lý rồi.");
            }

            // B3: tìm Employee của Manager (lấy EmployeeId để ghi ReviewedByEmployeeId)
            Employee manager = findEmployeeForAccount(managerAccountId);
            if (manager == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }

            // B4: kiểm tra min-staffing sau khi duyệt (CBR-88, CBR-90)
            String position = detail.getPosition();
            String shiftType = request.getShiftType().getDbValue();
            int remaining = leaveRequestDAO.countOnDutyAfterApproval(
                    request.getLeaveDate(), shiftType, position, leaveRequestId);

            int minRequired = "PT".equalsIgnoreCase(position)
                    ? systemConfigService.getInt(SystemConfigService.MIN_PT_PER_SHIFT)
                    : systemConfigService.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT);

            if (remaining < minRequired) {
                String roleLabel = "PT".equalsIgnoreCase(position) ? "PT" : "lễ tân";
                return ServiceResult.fail(String.format(
                        Messages.MSG79_LEAVE_BREAKS_MIN_STAFFING,
                        request.getShiftType().getDisplayName(),
                        request.getLeaveDate(),
                        minRequired,
                        roleLabel));
            }

            // B5: ghi DB
            leaveRequestDAO.approve(leaveRequestId, manager.getEmployeeId());

            // B6: ghi audit log
            auditLogService.log(
                    managerAccountId,
                    "Duyệt đơn nghỉ phép",
                    "LeaveRequests",
                    leaveRequestId,
                    "Status",
                    "Pending",
                    "Approved",
                    null);

            // B7: thông báo cho nhân viên (non-blocking, lỗi không rollback)
            try (Connection conn = DBContext.getConnection()) {
                notificationService.notifyEmployeeInTransaction(
                        conn,
                        detail.getLeaveRequest().getEmployeeId(),
                        "Nghỉ phép được duyệt",
                        "Đơn nghỉ ngày " + request.getLeaveDate()
                                + " (đã duyệt)");
            } catch (Exception ignored) {
                // thông báo thất bại không nên chặn luồng chính
            }

            return ServiceResult.ok(null);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Manager từ chối đơn nghỉ phép (UC-15).
     * Kiểm tra: đơn phải Pending, lý do 5-500 ký tự.
     *
     * @param managerAccountId AccountId của Manager từ session
     * @param leaveRequestId   mã đơn cần từ chối
     * @param rejectReason     lý do từ chối
     * @return ok() nếu thành công, fail() kèm lỗi nếu không được
     */
    public ServiceResult<Void> rejectRequest(
            int managerAccountId, int leaveRequestId, String rejectReason) {
        try {
            // B1: validate lý do từ chối 5–500 ký tự
            if (!ValidationUtil.isLengthValid(rejectReason, 5, 500)) {
                return ServiceResult.failField(
                        "rejectReason", "Lý do từ chối phải từ 5 đến 500 ký tự.");
            }

            // B2: kiểm tra đơn tồn tại và còn Pending
            LeaveRequestDAO.LeaveReviewDetail detail =
                    leaveRequestDAO.findReviewDetail(leaveRequestId);
            if (detail == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
            }
            if (!detail.getLeaveRequest().isPending()) {
                return ServiceResult.fail("Đơn này đã được xử lý rồi.");
            }

            // B3: tìm Employee của Manager
            Employee manager = findEmployeeForAccount(managerAccountId);
            if (manager == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }

            // B4: ghi DB
            leaveRequestDAO.reject(leaveRequestId, manager.getEmployeeId(), rejectReason);

            // B5: ghi audit log
            auditLogService.log(
                    managerAccountId,
                    "Từ chối đơn nghỉ phép",
                    "LeaveRequests",
                    leaveRequestId,
                    "Status",
                    "Pending",
                    "Rejected",
                    rejectReason);

            // B6: thông báo cho nhân viên
            try (Connection conn = DBContext.getConnection()) {
                notificationService.notifyEmployeeInTransaction(
                        conn,
                        detail.getLeaveRequest().getEmployeeId(),
                        "Nghỉ phép bị từ chối",
                        "Đơn nghỉ ngày "
                                + detail.getLeaveRequest().getLeaveDate()
                                + " bị từ chối. Lý do: " + rejectReason);
            } catch (Exception ignored) {
                // lỗi thông báo không chặn luồng chính
            }

            return ServiceResult.ok(null);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Màn nộp đơn của nhân viên (màn #25, UC-14) =====

    /**
     * Lấy dữ liệu để hiện form nộp đơn nghỉ phép của PT/Lễ tân (màn #25).
     *
     * @param accountId AccountId của nhân viên từ session, không lấy từ request parameter
     * @return view gồm thông tin nhân viên, hạn nộp P-12 và các đơn đã nộp; hoặc thông báo lỗi
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

            // B3: đọc giờ chốt nộp đơn P-12 (mặc định 15:00 ngày liền trước ngày nghỉ)
            LocalTime submissionCutoff = LocalTime.parse(
                    systemConfigService.getString(
                            SystemConfigService.LEAVE_SUBMIT_DEADLINE));

            // B4: lấy 10 đơn gần nhất của chính nhân viên này
            List<LeaveRequest> myRequests
                    = leaveRequestDAO.findByEmployeeId(employee.getEmployeeId(), 10);

            return ServiceResult.ok(new StaffLeaveFormView(
                    employee.getEmployeeId(),
                    employee.getEmployeeCode(),
                    fullName,
                    employee.getPosition().getDbValue(),
                    submissionCutoff,
                    myRequests));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Tìm Employee đang Working theo AccountId (Manager duyệt đơn). */
    private Employee findEmployeeForAccount(int accountId) throws SQLException {
        com.gms.entity.Account account = accountDAO.findById(accountId);
        if (account == null) {
            return null;
        }
        return employeeDAO.findByPersonId(account.getPersonId());
    }
}
