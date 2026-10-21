package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dto.EligibleReviewer;
import com.gms.dto.LeaveApprovalReview;
import com.gms.dto.LeaveEmployeeView;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveRequestItemView;
import com.gms.dto.LeaveRequestRow;
import com.gms.dto.LeaveReviewDetail;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.dto.TimelineNode;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.LeaveRequest;
import com.gms.entity.LeaveRequestTransfer;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.PaginationUtil;
import com.gms.util.ValidationUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Luồng của Manager: xem danh sách/chi tiết, duyệt, từ chối và chuyển người duyệt (BF-05).
 */
public class LeaveRequestService {

    // DAO truy vấn bảng LeaveRequests; Service không tự viết SQL
    private final LeaveRequestDAO leaveRequestDAO;
    private final LeaveApprovalService leaveApprovalService;

    // DAO đọc Employees (lấy EmployeeId từ AccountId khi Manager duyệt)
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;

    // Ghi audit log mỗi khi Manager duyệt / từ chối
    private final AuditLogService auditLogService;

    // Thông báo cho nhân viên sau khi đơn được xử lý
    private final NotificationService notificationService;

    /** Constructor cho test: truyền DAO/Service giả vào. */
    LeaveRequestService(
            LeaveRequestDAO leaveRequestDAO,
            AccountDAO accountDAO,
            EmployeeDAO employeeDAO,
            SystemConfigService systemConfigService,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.leaveRequestDAO = leaveRequestDAO;
        this.leaveApprovalService = new LeaveApprovalService(leaveRequestDAO, systemConfigService);
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public LeaveRequestService() {
        this(new LeaveRequestDAO(), new AccountDAO(), new EmployeeDAO(),
                new SystemConfigService(), new AuditLogService(), new NotificationService());
    }

    /** Danh sách cha phân trang theo nhân viên; mỗi người giữ đầy đủ đơn phù hợp bộ lọc. */
    public ServiceResult<PageResult<LeaveEmployeeView>> getEmployeePage(
            LeaveRequestFilter filter, Integer managerAccountId, int page, int pageSize) {
        try {
            // B1: lấy trang nhân viên, bao gồm người chưa có đơn. SQL đã sắp theo đơn chờ gần nhất.
            Integer managerEmployeeId = findWorkingManagerId(managerAccountId);
            int totalEmployees = leaveRequestDAO.countEmployees(filter);
            int totalPages = Math.max(1, (totalEmployees + pageSize - 1) / pageSize);
            int currentPage = Math.max(1, Math.min(page, totalPages));
            int offset = PaginationUtil.calculateOffset(currentPage, pageSize);
            List<LeaveEmployeeView> employees = leaveRequestDAO.findEmployeePage(filter, offset, pageSize);
            List<Integer> employeeIds = new ArrayList<>();
            Map<Integer, LeaveEmployeeView> employeeById = new LinkedHashMap<>();
            LocalDate today = DateTimeUtil.today();
            for (LeaveEmployeeView employee : employees) {
                employeeIds.add(employee.getEmployeeId());
                employeeById.put(employee.getEmployeeId(), employee);
                LocalDate nextDate = employee.getNextPendingDate();
                employee.setNearLeaveDate(nextDate != null && !nextDate.isBefore(today) && !nextDate.isAfter(today.plusDays(1)));
            }

            // B2: đọc đơn của cả trang một lần rồi gắn về đúng nhân viên bằng EmployeeId.
            if (!employeeIds.isEmpty()) {
                List<LeaveRequestRow> rows = leaveRequestDAO.findEmployeeRequests(filter, employeeIds, managerEmployeeId);
                for (LeaveRequestRow row : rows) {
                    LeaveEmployeeView employee = employeeById.get(row.getLeaveRequest().getEmployeeId());
                    if (employee == null) {
                        continue;
                    }
                    LeaveRequestItemView item = toItemView(row, today);
                    if (row.getLeaveRequest().isPending()) {
                        loadApprovalSummary(item, row.getLeaveRequest());
                    }
                    employee.getRequests().add(item);
                }
            }
            return ServiceResult.ok(new PageResult<>(employees, currentPage, pageSize, totalEmployees));
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Chuẩn hóa một lần để bảng và số lượng trên tab luôn dùng cùng điều kiện. */
    public LeaveRequestFilter createManagerFilter(String keyword, String status, String position, String leaveType,
            String fromDate, String toDate) {
        String searchText = keyword == null ? "" : keyword.trim();
        String selectedPosition = null;
        if ("PT".equals(position) || "Receptionist".equals(position)) {
            selectedPosition = position;
        }
        LeaveRequest.LeaveType selectedType = null;
        if (leaveType != null) {
            selectedType = LeaveRequest.LeaveType.fromDbValue(leaveType.trim());
        }
        return new LeaveRequestFilter(parseStatusFilter(status), searchText, selectedPosition, selectedType,
                DateTimeUtil.parseHtmlDate(fromDate), DateTimeUtil.parseHtmlDate(toDate));
    }

    /** null nghĩa là khách chưa đăng nhập hoặc tài khoản không còn là Manager đang làm việc. */
    private Integer findWorkingManagerId(Integer managerAccountId) throws SQLException {
        if (managerAccountId == null) {
            return null;
        }
        Employee manager = findEmployeeForAccount(managerAccountId);
        if (!isWorkingManager(manager)) {
            return null;
        }
        return manager.getEmployeeId();
    }

    /**
     * Lấy số đơn cho các tab của màn #26 (UC-15).
     *
     * @return map có 4 khóa: All, Pending, Approved, Rejected; hoặc thông báo lỗi hệ thống
     */
    public ServiceResult<Map<String, Integer>> getStatusCounts() {
        return getStatusCounts(null);
    }

    public ServiceResult<Map<String, Integer>> getStatusCounts(LeaveRequestFilter filter) {
        try {
            Map<String, Integer> counts = leaveRequestDAO.getStatusCounts(filter);
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
     * @return Status tương ứng; null là tab Tất cả, cũng là mặc định của màn gom theo nhân viên
     */
    LeaveRequest.Status parseStatusFilter(String statusParam) {
        // B1: mặc định xem đủ lịch sử khi mở một nhân viên.
        if (statusParam == null || statusParam.isBlank()) {
            return null;
        }

        // B2: tab "Tất cả" không lọc theo trạng thái
        if ("All".equalsIgnoreCase(statusParam.trim())) {
            return null;
        }

        // B3: giá trị lạ (fromDbValue trả null) cũng rơi về "Tất cả", không báo lỗi
        return LeaveRequest.Status.fromDbValue(statusParam.trim());
    }

    /** Đơn chờ có ngày bắt đầu hôm nay hoặc ngày mai cần ưu tiên xử lý. */
    boolean isNearLeaveDate(LeaveRequest request, LocalDate today) {
        LocalDate fromDate = request.getFromDate();
        return request.isPending() && fromDate != null
                && !fromDate.isBefore(today) && !fromDate.isAfter(today.plusDays(1));
    }

    /** Chỉ đổi dữ liệu hiển thị; các kiểm tra database nằm ở loadApprovalSummary(). */
    private LeaveRequestItemView toItemView(LeaveRequestRow row, LocalDate today) {
        LeaveRequestItemView view = new LeaveRequestItemView(row);
        view.setNearLeaveDate(isNearLeaveDate(row.getLeaveRequest(), today));
        return view;
    }

    /** Đọc dữ liệu kiểm tra tách khỏi hàm đổi dòng DAO thành dữ liệu hiển thị. */
    private void loadApprovalSummary(LeaveRequestItemView view, LeaveRequest request) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            String workingPosition = leaveRequestDAO.findWorkingPosition(connection, request.getEmployeeId());
            LeaveApprovalReview review = leaveApprovalService.review(connection, request, workingPosition);
            String blockingMessage = review.getBlockingMessage();
            if (blockingMessage == null) {
                view.setCheckSummary("Đạt");
                view.setCheckMessage("Chưa phát hiện vướng mắc. Mở chi tiết để kiểm tra trước khi duyệt.");
            } else {
                view.setCheckSummary("Cần xử lý");
                view.setCheckMessage(blockingMessage);
            }
        }
    }

    // ===== Màn duyệt đơn (màn #27, UC-15) =====

    /**
     * Lấy thông tin đầy đủ 1 đơn để hiện trên màn duyệt của Manager (#27).
     *
     * @param leaveRequestId mã đơn
     * @return view đơn, hoặc thông báo lỗi nếu không tìm thấy / lỗi hệ thống
     */
    public ServiceResult<LeaveReviewDetail> getReviewDetail(int leaveRequestId) {
        return getReviewDetail(leaveRequestId, null);
    }

    /**
     * Lấy thông tin đầy đủ 1 đơn để hiện trên màn duyệt của Manager (#27, BF-05).
     *
     * @param leaveRequestId   mã đơn
     * @param managerAccountId accountId của Manager đang xem (có thể null)
     * @return view đơn, hoặc thông báo lỗi nếu không tìm thấy / lỗi hệ thống
     */
    public ServiceResult<LeaveReviewDetail> getReviewDetail(int leaveRequestId, Integer managerAccountId) {
        if (leaveRequestId <= 0) {
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
        }
        try {
            Integer managerEmployeeId = findWorkingManagerId(managerAccountId);
            LeaveReviewDetail detail = leaveRequestDAO.findReviewDetail(leaveRequestId, managerEmployeeId);
            if (detail == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
            }
            LeaveRequest req = detail.getLeaveRequest();

            // B1: dùng đúng bộ kiểm tra của danh sách và POST duyệt.
            try (Connection connection = DBContext.getConnection()) {
                String workingPosition = leaveRequestDAO.findWorkingPosition(connection, req.getEmployeeId());
                LeaveApprovalReview review = leaveApprovalService.review(connection, req, workingPosition);
                detail.setLeaveBalance(review.getBalance());
                detail.setMinStaffRequired(review.getMinStaffRequired());
                detail.setRemainingStaff(review.getRemainingStaff());
                detail.setStaffingRows(review.getStaffingRows());
                detail.setChecks(review.getChecks());
                if (review.getInputError() == null && "PT".equals(workingPosition)) {
                    detail.setSessionConflicts(leaveRequestDAO.findSessionConflicts(connection, req.getEmployeeId(),
                            req.getFromDate(), req.getToDate(), req.getShiftType().getDbValue()));
                }
            }

            // B2: chỉ đánh dấu ngày nghỉ gần để nhắc xử lý, không đặt hạn duyệt.
            detail.setNearLeaveDate(isNearLeaveDate(req, DateTimeUtil.today()));

            // B3: lịch sử chuyển duyệt và các đơn trùng khoảng nghỉ.
            List<LeaveRequestTransfer> transfers = leaveRequestDAO.findTransfers(leaveRequestId);
            detail.setTimeline(buildTimeline(detail, transfers));

            if (req.getFromDate() != null && req.getToDate() != null && req.getShiftType() != null) {
                detail.setOverlappingCounts(leaveRequestDAO.countOverlappingRequests(req));
            }

            // B4: chọn thông báo chính của trang.
            if (detail.isLeaveDatePassed()) {
                detail.setBanner("Ngày xin nghỉ đã qua. Không thể duyệt đơn này.");
            } else if (req.isPending() && !detail.isCanAct()) {
                detail.setBanner("Đơn này đang được giao cho " + (detail.getAssignedToName() != null ? detail.getAssignedToName() : "người khác") + ". Bạn chỉ có quyền xem.");
            } else if (detail.isNearLeaveDate()) {
                detail.setBanner("Đơn sắp đến ngày nghỉ. Vui lòng ưu tiên xử lý để bố trí nhân sự.");
            }

            return ServiceResult.ok(detail);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    private List<TimelineNode> buildTimeline(
            LeaveReviewDetail detail,
            List<LeaveRequestTransfer> transfers) {
        List<TimelineNode> list = new ArrayList<>();
        LeaveRequest req = detail.getLeaveRequest();

        // 1. Nộp đơn
        list.add(new TimelineNode(
                "SUBMITTED",
                "Nộp đơn",
                detail.getFullName(),
                null,
                "PT".equals(detail.getPosition()) ? "PT" : "Lễ tân",
                req.getSubmittedAt(),
                "DONE"));

        // 2. Chuyển người duyệt (nếu có)
        if (transfers != null) {
            for (LeaveRequestTransfer t : transfers) {
                list.add(new TimelineNode(
                        "TRANSFERRED",
                        "Chuyển người duyệt",
                        t.getFromEmployeeName(),
                        t.getToEmployeeName(),
                        t.getNote(),
                        t.getCreatedAt(),
                        "DONE"));
            }
        }

        // 3. Trạng thái hiện tại
        if (req.isApproved()) {
            list.add(new TimelineNode(
                    "APPROVED",
                    "Đã duyệt",
                    detail.getReviewerName(),
                    null,
                    "Đơn đã được phê duyệt thành công",
                    req.getReviewedAt(),
                    "DONE"));
        } else if (req.isRejected()) {
            String reviewer = detail.getReviewerName();
            if (reviewer == null) {
                reviewer = detail.isAutoRejected() ? "Hệ thống" : "Quản lý";
            }
            list.add(new TimelineNode(
                    "REJECTED",
                    "Từ chối",
                    reviewer,
                    null,
                    req.getRejectReason(),
                    req.getReviewedAt(),
                    "FAILED"));
        } else {
            list.add(new TimelineNode(
                    "PENDING",
                    "Chờ duyệt",
                    detail.getAssignedToName() != null ? detail.getAssignedToName() : "Chưa phân công",
                    null,
                    "Ngày bắt đầu nghỉ: " + DateTimeUtil.formatDate(req.getFromDate()),
                    null,
                    "CURRENT"));
        }

        return list;
    }

    /**
     * Chuyển quyền duyệt đơn cho Manager khác (Màn #27, BF-05).
     */
    public ServiceResult<Void> transferReviewer(
            int managerAccountId, int leaveRequestId, int toEmployeeId, String note) {
        if (leaveRequestId <= 0 || toEmployeeId <= 0) {
            return ServiceResult.fail("Dữ liệu chuyển người duyệt không hợp lệ.");
        }
        if (note != null && note.length() > 200) {
            return ServiceResult.failField("note", "Ghi chú không được vượt quá 200 ký tự.");
        }
        try {
            Employee currentManager = findEmployeeForAccount(managerAccountId);
            if (!isWorkingManager(currentManager)) {
                return ServiceResult.fail("Bạn không có quyền xử lý đơn nghỉ phép.");
            }
            if (currentManager.getEmployeeId() == toEmployeeId) {
                return ServiceResult.fail("Không thể chuyển quyền duyệt cho chính bạn.");
            }

            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    leaveRequestDAO.lockDecisionScope(conn);
                    ServiceResult<LeaveRequest> lockedResult = validateDecision(conn, leaveRequestId, currentManager.getEmployeeId());
                    if (!lockedResult.isSuccess()) {
                        conn.rollback();
                        return ServiceResult.fail(lockedResult.getMessage());
                    }
                    if (!"Manager".equals(leaveRequestDAO.findWorkingPosition(conn, toEmployeeId))) {
                        conn.rollback();
                        return ServiceResult.fail("Người nhận phải là Quản lý đang làm việc.");
                    }
                    boolean updated = leaveRequestDAO.assign(conn, leaveRequestId, toEmployeeId);
                    if (!updated) {
                        conn.rollback();
                        return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                    }

                    LeaveRequestTransfer transfer = new LeaveRequestTransfer(
                            leaveRequestId, currentManager.getEmployeeId(), toEmployeeId, note != null ? note.trim() : null);
                    leaveRequestDAO.insertTransfer(conn, transfer);

                    auditLogService.logInTransaction(
                            conn,
                            managerAccountId,
                            Role.MANAGER,
                            "Chuyển người duyệt đơn nghỉ phép",
                            "LeaveRequests",
                            leaveRequestId,
                            "AssignedToEmployeeId",
                            String.valueOf(currentManager.getEmployeeId()),
                            String.valueOf(toEmployeeId),
                            note);

                    notificationService.notifyEmployeeInTransaction(
                            conn,
                            toEmployeeId,
                            "Đơn nghỉ phép được giao cho bạn",
                            currentManager.getEmployeeCode() + " đã chuyển quyền duyệt đơn #" + leaveRequestId + " cho bạn.");

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

    /**
     * Lấy danh sách Manager đủ điều kiện nhận chuyển duyệt (Màn #27, BF-05).
     */
    public ServiceResult<List<EligibleReviewer>> getEligibleReviewers(int managerAccountId) {
        try {
            Employee manager = findEmployeeForAccount(managerAccountId);
            int excludeId = manager != null ? manager.getEmployeeId() : 0;
            List<EligibleReviewer> list = leaveRequestDAO.findEligibleReviewers(excludeId);
            return ServiceResult.ok(list);
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
            // B1: lấy Manager từ tài khoản đăng nhập; không lấy EmployeeId từ form.
            Employee manager = findEmployeeForAccount(managerAccountId);
            if (!isWorkingManager(manager)) {
                return ServiceResult.fail("Bạn không có quyền xử lý đơn nghỉ phép.");
            }

            // B2: mở Transaction để đảm bảo duyệt đơn + ghi audit log + gửi thông báo diễn ra đồng thời
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false); // Bắt đầu transaction

                try {
                    // B2.1: khóa trước khi đọc lại trạng thái, phân công và quỹ phép.
                    leaveRequestDAO.lockDecisionScope(conn);
                    ServiceResult<LeaveRequest> lockedResult = validateDecision(conn, leaveRequestId, manager.getEmployeeId());
                    if (!lockedResult.isSuccess()) {
                        conn.rollback();
                        return ServiceResult.fail(lockedResult.getMessage());
                    }
                    LeaveRequest request = lockedResult.getData();
                    String position = leaveRequestDAO.findWorkingPosition(conn, request.getEmployeeId());
                    ServiceResult<Void> checks = validateApproval(conn, request, position);
                    if (!checks.isSuccess()) {
                        conn.rollback();
                        return checks;
                    }

                    // B2.2: cập nhật trạng thái đơn thành Approved (WHERE Status = 'Pending')
                    boolean isApproved = leaveRequestDAO.approve(conn, leaveRequestId, manager.getEmployeeId());
                    if (!isApproved) {
                        // Nếu 0 dòng bị ảnh hưởng, có nghĩa người khác đã duyệt/từ chối đơn này trước đó
                        conn.rollback();
                        return ServiceResult.fail("Đơn này đã được xử lý bởi người khác.");
                    }

                    // B2.3: ghi nhật ký kiểm toán (Audit Log)
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

                    // B2.4: gửi thông báo đến chuông của nhân viên
                    String notifTitle = "Nghỉ phép được duyệt";
                    String notifContent = "Đơn nghỉ " + formatLeavePeriod(request)
                            + " (ca " + request.getShiftType().getDisplayName() + ") đã được duyệt.";
                    notificationService.notifyEmployeeInTransaction(
                            conn,
                            request.getEmployeeId(),
                            notifTitle,
                            notifContent);

                    // B2.5: xác nhận mọi thay đổi thành công
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

            // B2: lấy Manager đang đăng nhập.
            Employee manager = findEmployeeForAccount(managerAccountId);
            if (!isWorkingManager(manager)) {
                return ServiceResult.fail("Bạn không có quyền xử lý đơn nghỉ phép.");
            }

            // B3: mở Transaction để từ chối + ghi audit log + gửi thông báo
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false); // Bắt đầu transaction

                try {
                    leaveRequestDAO.lockDecisionScope(conn);
                    ServiceResult<LeaveRequest> lockedResult = validateDecision(conn, leaveRequestId, manager.getEmployeeId());
                    if (!lockedResult.isSuccess()) {
                        conn.rollback();
                        return ServiceResult.fail(lockedResult.getMessage());
                    }
                    LeaveRequest request = lockedResult.getData();
                    // B3.1: cập nhật trạng thái đơn thành Rejected
                    boolean isRejected = leaveRequestDAO.reject(
                            conn, leaveRequestId, manager.getEmployeeId(), rejectReason);
                    if (!isRejected) {
                        conn.rollback();
                        return ServiceResult.fail("Đơn này đã được xử lý bởi người khác.");
                    }

                    // B3.2: ghi nhật ký kiểm toán (Audit Log)
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

                    // B3.3: gửi thông báo đến chuông của nhân viên
                    String notifTitle = "Nghỉ phép bị từ chối";
                    String notifContent = "Đơn nghỉ " + formatLeavePeriod(request)
                            + " bị từ chối. Lý do: " + rejectReason.trim();
                    if (notifContent.length() > 500) {
                        notifContent = notifContent.substring(0, 497) + "...";
                    }
                    notificationService.notifyEmployeeInTransaction(
                            conn,
                            request.getEmployeeId(),
                            notifTitle,
                            notifContent);

                    // B3.4: xác nhận giao dịch thành công
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

    /** Chỉ nhân viên Working ở vị trí Manager mới được xử lý đơn. */
    private boolean isWorkingManager(Employee employee) {
        return employee != null && employee.getPosition() == Role.MANAGER
                && employee.getStatus() == Employee.Status.WORKING;
    }

    /** Đọc lại quyền và trạng thái trên Connection đã khóa, tránh thao tác từ tab cũ. */
    private ServiceResult<LeaveRequest> validateDecision(Connection connection, int requestId, int managerEmployeeId)
            throws SQLException {
        if (!"Manager".equals(leaveRequestDAO.findWorkingPosition(connection, managerEmployeeId))) {
            return ServiceResult.fail("Bạn không có quyền xử lý đơn nghỉ phép.");
        }
        LeaveRequest current = leaveRequestDAO.findById(connection, requestId);
        if (current == null || !current.isPending()) {
            return ServiceResult.fail("Đơn này đã thay đổi trạng thái. Vui lòng tải lại trang.");
        }
        Integer assignee = current.getAssignedToEmployeeId();
        if (assignee != null && assignee != managerEmployeeId) {
            return ServiceResult.fail("Đơn này đã được phân công cho người khác duyệt.");
        }
        return ServiceResult.ok(current);
    }

    /**
     * Kiểm tra toàn bộ ngày/ca trong transaction duyệt. Không tin Days hoặc dữ liệu kiểm tra từ trình duyệt.
     * Pending khác giữ chỗ quỹ phép; đơn hiện tại được loại ra rồi cộng đúng một lần.
     */
    ServiceResult<Void> validateApproval(Connection connection, LeaveRequest request, String position) throws SQLException {
        LeaveApprovalReview review = leaveApprovalService.review(connection, request, position);
        String blockingMessage = review.getBlockingMessage();
        if (blockingMessage != null) {
            return ServiceResult.fail(blockingMessage);
        }
        return ServiceResult.ok(null);
    }

    /** Khoảng ngày dùng trong thông báo; không bỏ mất ngày kết thúc của đơn nhiều ngày. */
    private String formatLeavePeriod(LeaveRequest request) {
        String period = DateTimeUtil.formatDate(request.getFromDate());
        if (!request.getFromDate().equals(request.getToDate())) {
            period = period + "–" + DateTimeUtil.formatDate(request.getToDate());
        }
        return period;
    }

    // ===== Background Job (JOB-07) =====

    /** Nhắc một lần cho đơn Pending nghỉ ngày mai; không tự duyệt/từ chối hoặc đặt hạn theo giờ. */
    public void sendDueReminders() throws SQLException {
        LocalDate tomorrow = DateTimeUtil.today().plusDays(1);
        List<LeaveRequest> requests = leaveRequestDAO.findPendingNeedingReminder(tomorrow);
        if (requests.isEmpty()) {
            return;
        }
        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Đánh dấu chỉ khi còn Pending và chưa nhắc; hai lượt job không gửi trùng.
                int remindedCount = 0;
                for (LeaveRequest request : requests) {
                    if (leaveRequestDAO.markReminded(connection, request.getLeaveRequestId())) {
                        remindedCount += 1;
                    }
                }
                if (remindedCount > 0) {
                    String content = "Có " + remindedCount + " đơn nghỉ từ ngày mai đang chờ xử lý. "
                            + "Vui lòng kiểm tra và bố trí nhân sự.";
                    notificationService.notifyRoleInTransaction(connection, Role.MANAGER,
                            "Đơn sắp đến ngày nghỉ", content);
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
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
