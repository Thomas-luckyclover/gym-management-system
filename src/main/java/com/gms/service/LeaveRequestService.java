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
import com.gms.dto.StaffingPreview;
import com.gms.dto.ShiftStaffingRow;
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

    // Số ngày tối đa để Manager duyệt đơn nộp sớm (CBR-85, trừ hao Manager nghỉ cuối tuần/nghỉ phép ngắn)
    public static final int APPROVAL_SLA_DAYS = 3;

    // Đơn còn dưới số phút này đến hạn duyệt thì đánh dấu khẩn cấp (tô nổi trên
    // danh sách)
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

    /** Constructor cho test: truyền DAO/Service giả vào. */
    LeaveRequestService(
            LeaveRequestDAO leaveRequestDAO,
            AccountDAO accountDAO,
            EmployeeDAO employeeDAO,
            PersonDAO personDAO,
            SystemConfigService systemConfigService,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.leaveRequestDAO = leaveRequestDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.personDAO = personDAO;
        this.systemConfigService = systemConfigService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public LeaveRequestService() {
        this(new LeaveRequestDAO(), new AccountDAO(), new EmployeeDAO(), new PersonDAO(),
                new SystemConfigService(), new AuditLogService(), new NotificationService());
    }

    /**
     * Lấy một trang đơn nghỉ phép cho màn danh sách của Manager (màn #26, UC-15).
     *
     * @param keyword  từ khóa tên nhân viên từ URL, có thể null/rỗng
     * @param status   tab trạng thái từ URL (Pending, Approved, Rejected, All), có
     *                 thể null
     * @param page     số trang, bắt đầu từ 1
     * @param pageSize số dòng mỗi trang
     * @return trang các dòng đã tính hạn duyệt và đếm ngược, hoặc thông báo lỗi hệ
     *         thống
     */
    public ServiceResult<PageResult<LeaveRequestItemView>> getManagerPage(
            String keyword, String status, int page, int pageSize) {
        return getManagerPage(keyword, status, null, null, null, null, page, pageSize);
    }

    /**
     * Lấy một trang đơn nghỉ phép cho màn danh sách của Manager với bộ lọc mở rộng (màn #26, UC-15, BF-05).
     *
     * @param keyword     từ khóa tên nhân viên từ URL, có thể null/rỗng
     * @param status      tab trạng thái từ URL (Pending, Approved, Rejected, All)
     * @param position    vị trí nhân viên (PT, Receptionist), có thể null/rỗng
     * @param leaveType   loại nghỉ phép (Annual, Sick, Unpaid), có thể null/rỗng
     * @param fromDateStr ngày nghỉ từ (YYYY-MM-DD), có thể null/rỗng
     * @param toDateStr   ngày nghỉ đến (YYYY-MM-DD), có thể null/rỗng
     * @param page        số trang, bắt đầu từ 1
     * @param pageSize    số dòng mỗi trang
     * @return trang các dòng đã tính hạn duyệt và đếm ngược, hoặc thông báo lỗi hệ thống
     */
    public ServiceResult<PageResult<LeaveRequestItemView>> getManagerPage(
            String keyword, String status, String position, String leaveType,
            String fromDateStr, String toDateStr, int page, int pageSize) {

        try {
            // B1: chuẩn hóa tham số URL thành bộ lọc hợp lệ
            String trimmedKeyword = keyword == null ? "" : keyword.trim();
            String trimmedPosition = (position == null || position.isBlank() || "All".equalsIgnoreCase(position))
                    ? null : position.trim();
            LeaveRequest.LeaveType parsedLeaveType = (leaveType == null || leaveType.isBlank() || "All".equalsIgnoreCase(leaveType))
                    ? null : LeaveRequest.LeaveType.fromDbValue(leaveType.trim());

            LocalDate parsedFromDate = null;
            if (fromDateStr != null && !fromDateStr.isBlank()) {
                try {
                    parsedFromDate = LocalDate.parse(fromDateStr.trim());
                } catch (Exception ignored) {
                }
            }

            LocalDate parsedToDate = null;
            if (toDateStr != null && !toDateStr.isBlank()) {
                try {
                    parsedToDate = LocalDate.parse(toDateStr.trim());
                } catch (Exception ignored) {
                }
            }

            LeaveRequestFilter filter = new LeaveRequestFilter(
                    parseStatusFilter(status), trimmedKeyword,
                    trimmedPosition, parsedLeaveType, parsedFromDate, parsedToDate);

            // B2: đếm tổng và lấy đúng một trang (DAO cắt bằng OFFSET/FETCH)
            int totalItems = leaveRequestDAO.countByFilter(filter);
            int offset = PaginationUtil.calculateOffset(page, pageSize);
            List<LeaveRequestDAO.LeaveRequestRow> rows = leaveRequestDAO.findManagerPage(filter, offset, pageSize);

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
     * @return map có 4 khóa: All, Pending, Approved, Rejected; hoặc thông báo lỗi
     *         hệ thống
     */
    public ServiceResult<Map<String, Integer>> getStatusCounts() {
        try {
            // B1: DAO trả số đơn của 3 trạng thái thật
            Map<String, Integer> counts = leaveRequestDAO.getStatusCounts();

            // B2: tab "Tất cả" = tổng 3 trạng thái, thêm vào đầu map để JSP đọc theo khóa
            // All
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
     * Đổi tham số tab trạng thái trên URL thành Status để lọc danh sách của
     * Manager.
     *
     * @param statusParam giá trị param "status" (Pending, Approved, Rejected, All)
     *                    hoặc null/rỗng
     * @return Status tương ứng; null nghĩa là tab "Tất cả"; URL trống thì mặc định
     *         PENDING
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
     * Tính hạn duyệt P-20 của một đơn nghỉ phép (CBR-85):
     * Lấy mốc sớm hơn giữa (Ngày nộp + APPROVAL_SLA_DAYS lúc P-20) và (Ngày nghỉ - 1 ngày lúc P-20).
     *
     * @param request đơn xin nghỉ phép
     * @param approvalCutoff giờ chốt duyệt P-20
     * @return thời điểm hạn duyệt
     */
    public LocalDateTime computeApprovalDeadline(LeaveRequest request, LocalTime approvalCutoff) {
        LocalDate cutoffByLeaveDate = request.getLeaveDate().minusDays(1);
        LocalDate submittedDate = request.getSubmittedAt() != null
                ? request.getSubmittedAt().toLocalDate()
                : DateTimeUtil.today();
        LocalDate cutoffBySubmittedDate = submittedDate.plusDays(APPROVAL_SLA_DAYS);

        LocalDate deadlineDate = cutoffBySubmittedDate.isBefore(cutoffByLeaveDate)
                ? cutoffBySubmittedDate
                : cutoffByLeaveDate;
        return deadlineDate.atTime(approvalCutoff);
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

        // B1: hạn duyệt P-20 theo công thức CBR-85: MIN(Ngày nộp + 3 ngày, Ngày nghỉ - 1 ngày) lúc P-20
        LocalDateTime approvalDeadline = computeApprovalDeadline(request, approvalTime);

        // B2: đơn đã xử lý không cần đếm ngược; đơn Pending thì xét đã quá hạn chưa
        String countdownText = "";
        boolean isUrgent = false;
        boolean isOverdue = false;
        if (request.isPending()) {
            if (!now.isBefore(approvalDeadline)) {
                isOverdue = true;
                countdownText = "Đã quá hạn duyệt";
            } else {
                long minutesLeft = Duration.between(now, approvalDeadline).toMinutes();
                countdownText = formatCountdown(minutesLeft);
                isUrgent = minutesLeft < URGENT_WITHIN_MINUTES;
            }
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
                isUrgent,
                isOverdue);
    }

    /** Đổi số phút còn lại thành chữ hiển thị; số âm nghĩa là đã quá hạn duyệt. */
    private String formatCountdown(long minutesLeft) {
        if (minutesLeft < 0) {
            return "Đã quá hạn duyệt";
        }
        if (minutesLeft < 60) {
            return "Còn " + minutesLeft + " phút";
        }
        long hours = minutesLeft / 60;
        long minutes = minutesLeft % 60;
        if (hours >= 24) {
            long days = hours / 24;
            long remHours = hours % 24;
            return "Còn " + days + " ngày " + remHours + " giờ";
        }
        return "Còn " + hours + " giờ " + minutes + " phút";
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
            LeaveRequestDAO.LeaveReviewDetail detail = leaveRequestDAO.findReviewDetail(leaveRequestId);
            if (detail == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
            }

            // Nếu đơn đang chờ duyệt (Pending), tính trước quân số trực dự kiến và định biên tối thiểu
            // để hỗ trợ Manager ra quyết định chính xác trên màn hình duyệt đơn
            if (detail.getLeaveRequest().isPending()) {
                String position = detail.getPosition();
                String shiftType = detail.getLeaveRequest().getShiftType().getDbValue();
                int remaining = leaveRequestDAO.countOnDutyAfterApproval(
                        detail.getLeaveRequest().getLeaveDate(), shiftType, position, leaveRequestId);

                int minRequired = "PT".equalsIgnoreCase(position)
                        ? systemConfigService.getInt(SystemConfigService.MIN_PT_PER_SHIFT)
                        : systemConfigService.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT);

                detail.setRemainingStaff(remaining);
                detail.setMinStaffRequired(minRequired);

                // Tính hạn duyệt P-20 và cờ quá hạn để màn #27 khóa nút (CBR-85)
                LocalTime approvalCutoff = LocalTime.parse(
                        systemConfigService.getString(SystemConfigService.LEAVE_APPROVED_DEADLINE));
                LocalDateTime deadline = computeApprovalDeadline(detail.getLeaveRequest(), approvalCutoff);
                detail.setApprovalDeadline(deadline);
                detail.setOverdue(!DateTimeUtil.now().isBefore(deadline));

                // Tính định biên chi tiết cho từng ca (với đơn FullDay thì tính cả ca Sáng và Chiều)
                List<String> shifts = new ArrayList<>();
                if (detail.getLeaveRequest().getShiftType() == LeaveRequest.ShiftType.FULL_DAY) {
                    shifts.add(LeaveRequest.ShiftType.MORNING.getDbValue());
                    shifts.add(LeaveRequest.ShiftType.AFTERNOON.getDbValue());
                } else {
                    shifts.add(detail.getLeaveRequest().getShiftType().getDbValue());
                }

                List<ShiftStaffingRow> rows = new ArrayList<>();
                for (String s : shifts) {
                    int after = leaveRequestDAO.countOnDutyAfterApproval(
                            detail.getLeaveRequest().getLeaveDate(), s, position, leaveRequestId);
                    String sLabel = "Morning".equalsIgnoreCase(s) ? "Ca sáng"
                            : ("Afternoon".equalsIgnoreCase(s) ? "Ca chiều" : "Cả ngày");
                    String pLabel = "PT".equalsIgnoreCase(position) ? "PT" : "Lễ tân";
                    rows.add(new ShiftStaffingRow(sLabel, pLabel, after + 1, after, minRequired));
                }
                detail.setStaffingRows(rows);
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
     * Mọi thao tác: duyệt đơn, ghi nhật ký kiểm toán (audit log) và gửi thông báo
     * được thực hiện trong cùng một transaction để bảo toàn tính toàn vẹn dữ liệu.
     *
     * @param managerAccountId AccountId của Manager từ session
     * @param leaveRequestId mã đơn cần duyệt
     * @return ok() nếu thành công, fail() kèm thông báo lỗi nếu không được
     */
    public ServiceResult<Void> approveRequest(int managerAccountId, int leaveRequestId) {
        try {
            // B1: lấy thông tin chi tiết đơn
            LeaveRequestDAO.LeaveReviewDetail detail = leaveRequestDAO.findReviewDetail(leaveRequestId);
            if (detail == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
            }
            LeaveRequest request = detail.getLeaveRequest();

            // B2: kiểm tra trạng thái đơn — chỉ được duyệt đơn đang Pending
            if (!request.isPending()) {
                return ServiceResult.fail("Đơn này đã được xử lý rồi.");
            }

            // B2.1: kiểm tra hạn duyệt P-20 (CBR-85) — chặn duyệt đơn đã quá hạn P-20
            LocalTime approvalCutoff = LocalTime.parse(
                    systemConfigService.getString(SystemConfigService.LEAVE_APPROVED_DEADLINE));
            LocalDateTime approvalDeadline = computeApprovalDeadline(request, approvalCutoff);
            if (!DateTimeUtil.now().isBefore(approvalDeadline)) {
                return ServiceResult.fail("Đơn nghỉ phép này đã quá hạn duyệt ("
                        + approvalCutoff + ").");
            }

            // B3: tìm thông tin nhân viên của Manager (lấy EmployeeId để lưu người duyệt)
            Employee manager = findEmployeeForAccount(managerAccountId);
            if (manager == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }

            // B4: mở Transaction để đảm bảo duyệt đơn + ghi audit log + gửi thông báo diễn ra đồng thời
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false); // Bắt đầu transaction

                // B4.(-1): khóa các đơn cùng ngày nghỉ để hai lượt duyệt cùng ngày phải chạy lần lượt (BR-14)
                leaveRequestDAO.lockLeaveDate(conn, request.getLeaveDate());

                try {
                    String position = detail.getPosition();
                    String shiftType = request.getShiftType().getDbValue();

                    // B4.0: Nếu nhân viên là PT, kiểm tra lại xem có phát sinh buổi dạy Scheduled trùng ca không (CBR-83)
                    if ("PT".equalsIgnoreCase(position)) {
                        int scheduledCount = leaveRequestDAO.countScheduledSessions(
                                conn, request.getEmployeeId(), request.getLeaveDate(), shiftType);
                        if (scheduledCount > 0) {
                            conn.rollback();
                            return ServiceResult.fail(String.format(
                                    Messages.MSG78_LEAVE_BLOCKED_BY_SESSIONS, scheduledCount));
                        }
                    }

                    // B4.1: kiểm tra định biên nhân sự tối thiểu (min-staffing) trong ca
                    int remaining = leaveRequestDAO.countOnDutyAfterApproval(
                            conn, request.getLeaveDate(), shiftType, position, leaveRequestId);

                    int minRequired = "PT".equalsIgnoreCase(position)
                            ? systemConfigService.getInt(SystemConfigService.MIN_PT_PER_SHIFT)
                            : systemConfigService.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT);

                    if (remaining < minRequired) {
                        conn.rollback();
                        String roleLabel = "PT".equalsIgnoreCase(position) ? "PT" : "lễ tân";
                        return ServiceResult.fail(String.format(
                                Messages.MSG79_LEAVE_BREAKS_MIN_STAFFING,
                                request.getShiftType().getDisplayName(),
                                request.getLeaveDate(),
                                minRequired,
                                roleLabel));
                    }

                    // B4.2: cập nhật trạng thái đơn thành Approved (WHERE Status = 'Pending')
                    boolean isApproved = leaveRequestDAO.approve(conn, leaveRequestId, manager.getEmployeeId());
                    if (!isApproved) {
                        // Nếu 0 dòng bị ảnh hưởng, có nghĩa người khác đã duyệt/từ chối đơn này trước đó
                        conn.rollback();
                        return ServiceResult.fail("Đơn này đã được xử lý bởi người khác.");
                    }

                    // B4.3: ghi nhật ký kiểm toán (Audit Log)
                    auditLogService.logInTransaction(
                            conn,
                            managerAccountId,
                            Role.MANAGER,
                            "Duyệt đơn nghỉ phép",
                            "LeaveRequests",
                            leaveRequestId,
                            "Status",
                            "Pending",
                            "Approved",
                            null);

                    // B4.4: gửi thông báo đến chuông của nhân viên
                    String notifTitle = "Nghỉ phép được duyệt";
                    String notifContent = "Đơn nghỉ ngày " + request.getLeaveDate()
                            + " (ca " + request.getShiftType().getDisplayName() + ") đã được duyệt.";
                    notificationService.notifyEmployeeInTransaction(
                            conn,
                            request.getEmployeeId(),
                            notifTitle,
                            notifContent);

                    // B4.5: xác nhận mọi thay đổi thành công
                    conn.commit();
                    return ServiceResult.ok(null);

                } catch (SQLException | RuntimeException e) {
                    conn.rollback(); // Hoàn tác nếu có lỗi
                    throw e;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Manager từ chối đơn nghỉ phép (UC-15).
     * Kiểm tra: đơn phải Pending, lý do 5-500 ký tự.
     * Mọi thao tác: từ chối, ghi audit log và thông báo nằm trong cùng một transaction.
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
            LeaveRequestDAO.LeaveReviewDetail detail = leaveRequestDAO.findReviewDetail(leaveRequestId);
            if (detail == null) {
                return ServiceResult.fail(
                        String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
            }
            LeaveRequest request = detail.getLeaveRequest();
            if (!request.isPending()) {
                return ServiceResult.fail("Đơn này đã được xử lý rồi.");
            }

            // B2.1: kiểm tra hạn duyệt P-20 (CBR-85) — quá hạn P-20 thì thuộc diện hệ thống tự từ chối
            LocalTime approvalCutoff = LocalTime.parse(
                    systemConfigService.getString(SystemConfigService.LEAVE_APPROVED_DEADLINE));
            LocalDateTime approvalDeadline = computeApprovalDeadline(request, approvalCutoff);
            if (!DateTimeUtil.now().isBefore(approvalDeadline)) {
                return ServiceResult.fail("Đơn nghỉ phép này đã quá hạn duyệt ("
                        + approvalCutoff + ").");
            }

            // B3: tìm thông tin nhân viên của Manager
            Employee manager = findEmployeeForAccount(managerAccountId);
            if (manager == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }

            // B4: mở Transaction để từ chối + ghi audit log + gửi thông báo
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false); // Bắt đầu transaction

                try {
                    // B4.1: cập nhật trạng thái đơn thành Rejected
                    boolean isRejected = leaveRequestDAO.reject(
                            conn, leaveRequestId, manager.getEmployeeId(), rejectReason);
                    if (!isRejected) {
                        conn.rollback();
                        return ServiceResult.fail("Đơn này đã được xử lý bởi người khác.");
                    }

                    // B4.2: ghi nhật ký kiểm toán (Audit Log)
                    auditLogService.logInTransaction(
                            conn,
                            managerAccountId,
                            Role.MANAGER,
                            "Từ chối đơn nghỉ phép",
                            "LeaveRequests",
                            leaveRequestId,
                            "Status",
                            "Pending",
                            "Rejected",
                            rejectReason);

                    // B4.3: gửi thông báo đến chuông của nhân viên
                    String notifTitle = "Nghỉ phép bị từ chối";
                    String notifContent = "Đơn nghỉ ngày " + request.getLeaveDate()
                            + " bị từ chối. Lý do: " + rejectReason.trim();
                    if (notifContent.length() > 500) {
                        notifContent = notifContent.substring(0, 497) + "...";
                    }
                    notificationService.notifyEmployeeInTransaction(
                            conn,
                            request.getEmployeeId(),
                            notifTitle,
                            notifContent);

                    // B4.4: xác nhận giao dịch thành công
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
            int minPt = systemConfigService.getInt(SystemConfigService.MIN_PT_PER_SHIFT);
            int minReceptionist = systemConfigService.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT);

            // B4: tính dự báo quân số PT và Lễ tân còn trực vào ngày mai nếu đơn này được duyệt (SRS 5.2.1)
            LocalDate tomorrow = DateTimeUtil.today().plusDays(1);
            int remainingPt = leaveRequestDAO.countOnDutyAfterApproval(tomorrow, "FullDay", "PT", 0);
            int remainingReceptionist = leaveRequestDAO.countOnDutyAfterApproval(tomorrow, "FullDay", "Receptionist", 0);

            if (Role.PT == employee.getPosition()) {
                // Người nộp là PT nên số Lễ tân đang trực không bị trừ 1
                remainingReceptionist = remainingReceptionist + 1;
            } else {
                // Người nộp là Lễ tân nên số PT đang trực không bị trừ 1
                remainingPt = remainingPt + 1;
            }

            // B5: lấy 10 đơn gần nhất của chính nhân viên này
            List<LeaveRequest> myRequests = leaveRequestDAO.findByEmployeeId(employee.getEmployeeId(), 10);

            return ServiceResult.ok(new StaffLeaveFormView(
                    employee.getEmployeeId(),
                    employee.getEmployeeCode(),
                    fullName,
                    employee.getPosition().getDbValue(),
                    submissionCutoff,
                    minPt,
                    minReceptionist,
                    remainingPt,
                    remainingReceptionist,
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

            // B3: đếm số còn lại của từng chức vụ (DAO đã trừ 1 cho đơn đang xét)
            int remainingPt = leaveRequestDAO.countOnDutyAfterApproval(leaveDate, shiftType, "PT", 0);
            int remainingReceptionist = leaveRequestDAO.countOnDutyAfterApproval(leaveDate, shiftType, "Receptionist", 0);

            // B4: chức vụ không phải của người nộp thì không bị trừ 1
            if (Role.PT == employee.getPosition()) {
                remainingReceptionist = remainingReceptionist + 1;
            } else {
                remainingPt = remainingPt + 1;
            }

            // B5: đọc định biên tối thiểu P-13, P-14
            int minPt = systemConfigService.getInt(SystemConfigService.MIN_PT_PER_SHIFT);
            int minReceptionist = systemConfigService.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT);

            return ServiceResult.ok(new StaffingPreview(remainingPt, remainingReceptionist, minPt, minReceptionist));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Nhân viên nộp đơn xin nghỉ phép (màn #25, UC-14).
     * Kiểm tra: hạn chốt nộp P-12 (CBR-84), trùng lịch nghỉ, và buổi dạy Scheduled
     * của PT (CBR-83).
     *
     * @param accountId AccountId của nhân viên từ session
     * @param leaveType loại nghỉ (Annual, Sick, Unpaid)
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

        // B1: validate dữ liệu đầu vào cơ bản
        if (leaveType == null) {
            return ServiceResult.failField("leaveType", "Vui lòng chọn loại nghỉ.");
        }
        if (shiftType == null) {
            return ServiceResult.failField("shiftType", "Vui lòng chọn ca nghỉ.");
        }
        if (leaveDate == null) {
            return ServiceResult.failField("leaveDate", "Vui lòng chọn ngày nghỉ.");
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
            boolean hasConflict = leaveRequestDAO.hasConflictingLeave(
                    employee.getEmployeeId(), leaveDate, shiftType.getDbValue());
            if (hasConflict) {
                return ServiceResult.fail(
                        "Bạn đã có đơn nghỉ trùng ngày và ca này đang chờ duyệt hoặc đã được duyệt.");
            }

            // B5: đối với PT, kiểm tra buổi dạy Scheduled trong ngày và ca xin nghỉ
            // (CBR-83)
            boolean isPt = Role.PT == employee.getPosition();
            if (isPt) {
                int clashCount = leaveRequestDAO.countScheduledSessions(
                        employee.getEmployeeId(), leaveDate, shiftType.getDbValue());
                if (clashCount > 0) {
                    return ServiceResult.fail(String.format(
                            Messages.MSG78_LEAVE_BLOCKED_BY_SESSIONS, clashCount));
                }
            }

            // B6: tạo entity và mở Transaction lưu đơn + ghi AuditLog + thông báo cho Manager (BF-05)
            LeaveRequest request = new LeaveRequest();
            request.setEmployeeId(employee.getEmployeeId());
            request.setLeaveType(leaveType);
            request.setLeaveDate(leaveDate);
            request.setShiftType(shiftType);
            request.setReason(reason.trim());

            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);
                try {
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
                            + " xin nghỉ ngày " + leaveDate
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

    // ===== Background Job (JOB-07) =====

    /**
     * Chạy định kỳ mỗi phút: nhắc Manager trước mốc P-20 và tự động từ chối đơn quá
     * hạn (JOB-07).
     *
     * @throws SQLException lỗi kết nối hoặc truy vấn DB
     */
    public void processJob07() throws SQLException {
        // B1: đọc mốc giờ chốt duyệt đơn P-20 (mặc định 20:00)
        LocalTime cutoffTime = LocalTime.parse(
                systemConfigService.getString(
                        SystemConfigService.LEAVE_APPROVED_DEADLINE));

        LocalDate today = DateTimeUtil.today();
        LocalDate tomorrow = today.plusDays(1);
        LocalDateTime now = DateTimeUtil.now();
        LocalTime currentTime = now.toLocalTime();

        // B2: nhắc Manager từ 2 tiếng trước mốc P-20 (ví dụ từ 18:00 đến trước 20:00)
        LocalTime reminderStartTime = cutoffTime.minusHours(2);
        boolean inReminderWindow = !currentTime.isBefore(reminderStartTime)
                && currentTime.isBefore(cutoffTime);

        // Đơn có hạn duyệt tối nay khi: LeaveDate = tomorrow HOẶC SubmittedDate = today - APPROVAL_SLA_DAYS
        LocalDate remindSubmittedDate = today.minusDays(APPROVAL_SLA_DAYS);
        if (inReminderWindow) {
            List<LeaveRequest> remindList = leaveRequestDAO.findPendingNeedingReminder(tomorrow, remindSubmittedDate);
            if (!remindList.isEmpty()) {
                try (Connection conn = DBContext.getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        // Chỉ đếm những đơn thật sự vừa được đánh dấu (tránh nhắc trùng khi job chạy chồng nhau)
                        int remindedCount = 0;
                        for (LeaveRequest req : remindList) {
                            boolean isMarked = leaveRequestDAO.markReminded(conn, req.getLeaveRequestId());
                            if (isMarked) {
                                remindedCount = remindedCount + 1;
                            }
                        }

                        // Gửi thông báo đến chuông của tất cả Manager trong hệ thống
                        if (remindedCount > 0) {
                            String notifTitle = "Nhắc duyệt đơn nghỉ phép";
                            String notifContent = "Có " + remindedCount
                                    + " đơn nghỉ phép đến hạn cần duyệt trước " + cutoffTime + " hôm nay.";
                            notificationService.notifyRoleInTransaction(conn, Role.MANAGER, notifTitle, notifContent);
                        }

                        conn.commit();
                    } catch (Exception e) {
                        conn.rollback();
                        e.printStackTrace();
                    }
                }
            }
        }

        // B3: tự động từ chối các đơn Pending đã quá mốc P-20
        LocalDate maxOverdueLeaveDate = !currentTime.isBefore(cutoffTime) ? tomorrow : today;
        LocalDate maxOverdueSubmittedDate = maxOverdueLeaveDate.minusDays(1 + APPROVAL_SLA_DAYS);
        List<LeaveRequest> overdueList = leaveRequestDAO.findPendingOverdueRequests(
                maxOverdueLeaveDate, maxOverdueSubmittedDate);
        if (!overdueList.isEmpty()) {
            System.out.println("[JOB-07] Tự từ chối " + overdueList.size() + " đơn quá hạn lúc " + now);
        }

        for (LeaveRequest req : overdueList) {
            // Xử lý từng đơn trong một transaction riêng:
            // Đảm bảo cập nhật trạng thái + ghi audit log + gửi thông báo nguyên tử,
            // đồng thời lỗi ở một đơn không làm gián đoạn các đơn còn lại trong danh sách.
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // B3.1: cập nhật đơn sang Rejected với lý do "Quá hạn duyệt"
                    boolean isRejected = leaveRequestDAO.autoReject(conn, req.getLeaveRequestId(), "Quá hạn duyệt");
                    if (!isRejected) {
                        conn.rollback();
                        continue;
                    }

                    // B3.2: ghi Audit Log ghi nhận hệ thống tự từ chối
                    auditLogService.logInTransaction(
                            conn,
                            null,
                            "Hệ thống tự từ chối đơn nghỉ phép",
                            "LeaveRequests",
                            req.getLeaveRequestId(),
                            "Status",
                            "Pending",
                            "Rejected",
                            "Quá hạn duyệt");

                    // B3.3: gửi thông báo đến chuông của nhân viên
                    String notifTitle = "Nghỉ phép bị từ chối";
                    String notifContent = "Đơn nghỉ ngày " + req.getLeaveDate()
                            + " đã bị từ chối do quá hạn duyệt.";
                    notificationService.notifyEmployeeInTransaction(
                            conn,
                            req.getEmployeeId(),
                            notifTitle,
                            notifContent);

                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    e.printStackTrace();
                }
            }
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
