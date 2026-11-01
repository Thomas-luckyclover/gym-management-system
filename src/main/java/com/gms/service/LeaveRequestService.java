package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dto.LeaveApprovalReview;
import com.gms.dto.LeaveBulkApprovalResult;
import com.gms.dto.LeaveEmployeeView;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveRequestItemView;
import com.gms.dto.LeaveRequestRow;
import com.gms.dto.LeaveRequestGroupView;
import com.gms.dto.LeaveReviewDetailView;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.LeaveRequest;
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
 * Luồng của Manager: xem danh sách/chi tiết, duyệt và từ chối đơn (BF-05).
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
        if (hasReversedDateRange(filter)) {
            return ServiceResult.fail(Messages.MSG206_LEAVE_FILTER_DATE_ORDER);
        }
        try {
            // B1: lấy trang nhân viên; khi chọn ngày, SQL chỉ giữ người có đơn phù hợp.
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
                Map<String, LeaveRequestGroupView> requestGroups = new LinkedHashMap<>();
                List<LeaveRequestItemView> items = new ArrayList<>();
                for (LeaveRequestRow row : rows) {
                    LeaveEmployeeView employee = employeeById.get(row.getLeaveRequest().getEmployeeId());
                    if (employee == null) {
                        continue;
                    }
                    LeaveRequestItemView item = toItemView(row, today);
                    item.setLeaveGroup(loadLeaveGroup(row.getLeaveRequest(), requestGroups));
                    items.add(item);
                    employee.getRequests().add(item);
                }
                loadApprovalSummaries(rows, items);
            }
            return ServiceResult.ok(new PageResult<>(employees, currentPage, pageSize, totalEmployees));
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Trang từng đơn cho các tab trạng thái; kiểm tra Pending giống tab Tất cả và trang chi tiết. */
    public ServiceResult<PageResult<LeaveRequestItemView>> getRequestPage(
            LeaveRequestFilter filter, Integer managerAccountId, int page, int pageSize) {
        if (hasReversedDateRange(filter)) {
            return ServiceResult.fail(Messages.MSG206_LEAVE_FILTER_DATE_ORDER);
        }
        try {
            // B1: phân trang theo đợt/đơn độc lập; số trên tab vẫn đếm từng đơn.
            Integer managerEmployeeId = findWorkingManagerId(managerAccountId);
            int totalRequests = leaveRequestDAO.countRequests(filter);
            int totalPages = Math.max(1, (totalRequests + pageSize - 1) / pageSize);
            int currentPage = Math.max(1, Math.min(page, totalPages));
            int offset = PaginationUtil.calculateOffset(currentPage, pageSize);
            List<LeaveRequestRow> rows = leaveRequestDAO.findRequestPage(filter, offset, pageSize, managerEmployeeId);

            // B2: giữ thứ tự SQL, đánh dấu sắp nghỉ và đọc cùng bộ kiểm tra điều kiện duyệt.
            List<LeaveRequestItemView> items = new ArrayList<>();
            Map<String, LeaveRequestGroupView> requestGroups = new LinkedHashMap<>();
            LocalDate today = DateTimeUtil.today();
            for (LeaveRequestRow row : rows) {
                LeaveRequestItemView item = toItemView(row, today);
                item.setLeaveGroup(loadLeaveGroup(row.getLeaveRequest(), requestGroups));
                items.add(item);
            }
            loadApprovalSummaries(rows, items);
            // B3: nạp sẵn hai phần cùng đợt; trình duyệt chỉ ẩn/hiện HTML khi bấm dropdown.
            for (LeaveRequestItemView item : items) {
                LeaveRequestGroupView group = item.getLeaveGroup();
                if (group != null && group.getRequests().size() > 1) {
                    ServiceResult<List<LeaveReviewDetailView>> parts = getReviewParts(item.getLeaveRequestId(), managerAccountId);
                    if (parts.isSuccess()) {
                        group.setReviewParts(parts.getData());
                    } else {
                        group.setReviewError(parts.getMessage());
                    }
                }
            }
            return ServiceResult.ok(new PageResult<>(items, currentPage, pageSize, totalRequests));
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

    /** Số đơn trên bốn tab, giữ các bộ lọc và bỏ riêng trạng thái đang chọn. */
    public ServiceResult<Map<String, Integer>> getStatusCounts(LeaveRequestFilter filter) {
        if (hasReversedDateRange(filter)) {
            return ServiceResult.fail(Messages.MSG206_LEAVE_FILTER_DATE_ORDER);
        }
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

    /** Kiểm tra khoảng ngày nhập từ bộ lọc; cho phép bỏ trống một hoặc cả hai ngày. */
    private boolean hasReversedDateRange(LeaveRequestFilter filter) {
        return filter != null && filter.getFromDate() != null && filter.getToDate() != null
                && filter.getFromDate().isAfter(filter.getToDate());
    }

    /**
     * Đổi tham số tab trạng thái trên URL thành Status để lọc danh sách của
     * Manager.
     *
     * @param statusParam giá trị param "status" (Pending, Approved, Rejected, All)
     *                    hoặc null/rỗng
     * @return Status tương ứng; null là tab Tất cả khi statusParam là "All"
     */
    LeaveRequest.Status parseStatusFilter(String statusParam) {
        // B1: mở danh sách không có status thì vào Chờ duyệt.
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
        LeaveRequest request = row.getLeaveRequest();
        view.setLateApproval(request.isPending() && request.getFromDate() != null
                && request.getFromDate().isBefore(today));
        return view;
    }

    /**
     * Tải đầy đủ các đơn con trong cùng một đợt nghỉ (chung LeaveGroupId).
     * - Dùng Map làm cache tạm để tránh gọi CSDL lặp lại nhiều lần trên cùng một trang.
     * - Luôn đọc đủ cả 2 phần (ví dụ: phần Phép năm đã duyệt và phần Không lương đang chờ),
     *   kể cả khi một trong hai phần không khớp với bộ lọc trạng thái hiện tại của danh sách.
     */
    private LeaveRequestGroupView loadLeaveGroup(LeaveRequest request, Map<String, LeaveRequestGroupView> groups)
            throws SQLException {
        if (request.getLeaveGroupId() == null) {
            return null;
        }
        String key = request.getEmployeeId() + "/" + request.getLeaveGroupId();
        if (!groups.containsKey(key)) {
            List<LeaveRequest> parts = leaveRequestDAO.findByLeaveGroup(request.getLeaveGroupId(), request.getEmployeeId());
            if (parts.isEmpty()) {
                throw new SQLException("Không tìm thấy các phần cùng đợt nghỉ.");
            }
            groups.put(key, new LeaveRequestGroupView(parts));
        }
        return groups.get(key);
    }

    /** Một connection cho các đơn Pending của trang; đơn đã xử lý không chạy lại kiểm tra. */
    private void loadApprovalSummaries(List<LeaveRequestRow> rows, List<LeaveRequestItemView> items) throws SQLException {
        Map<Integer, LeaveRequestItemView> pendingItems = new LinkedHashMap<>();
        for (LeaveRequestItemView item : items) {
            if (item.getStatus() == LeaveRequest.Status.PENDING) {
                pendingItems.put(item.getLeaveRequestId(), item);
            }
        }
        if (pendingItems.isEmpty()) {
            return;
        }
        try (Connection connection = DBContext.getConnection()) {
            for (LeaveRequestRow row : rows) {
                LeaveRequestItemView item = pendingItems.get(row.getLeaveRequest().getLeaveRequestId());
                if (item != null) {
                    loadApprovalSummary(connection, item, row.getLeaveRequest());
                }
            }
        }
    }

    /** Đọc cùng điều kiện với trang chi tiết và POST, không tin kết quả GET khi duyệt thật. */
    private void loadApprovalSummary(Connection connection, LeaveRequestItemView view, LeaveRequest request) throws SQLException {
        String workingPosition = leaveRequestDAO.findWorkingPosition(connection, request.getEmployeeId());
        LeaveApprovalReview review = leaveApprovalService.review(connection, request, workingPosition);
        String blockingMessage = review.getBlockingMessage();
        view.setApprovalReady(blockingMessage == null);
        if (blockingMessage == null) {
            if (view.isLateApproval()) {
                view.setCheckSummary("Cần xác nhận");
                view.setCheckMessage("Xác nhận nhân viên nghỉ đúng đơn trong phần thời gian đã qua, kèm lý do.");
            } else {
                view.setCheckSummary("Đạt");
                view.setCheckMessage("Chưa phát hiện vướng mắc. Mở chi tiết để kiểm tra trước khi duyệt.");
            }
        } else {
            view.setCheckSummary("Cần xử lý");
            view.setCheckMessage(blockingMessage);
        }
    }

    // ===== Màn duyệt đơn (màn #27, UC-15) =====

    /**
     * Lấy thông tin đầy đủ 1 đơn để hiện trên màn duyệt của Manager (#27).
     *
     * @param leaveRequestId mã đơn
     * @return view đơn, hoặc thông báo lỗi nếu không tìm thấy / lỗi hệ thống
     */
    public ServiceResult<LeaveReviewDetailView> getReviewDetail(int leaveRequestId) {
        return getReviewDetail(leaveRequestId, null);
    }

    /**
     * Lấy thông tin đầy đủ 1 đơn để hiện trên màn duyệt của Manager (#27, BF-05).
     *
     * @param leaveRequestId   mã đơn
     * @param managerAccountId accountId của Manager đang xem (có thể null)
     * @return view đơn, hoặc thông báo lỗi nếu không tìm thấy / lỗi hệ thống
     */
    public ServiceResult<LeaveReviewDetailView> getReviewDetail(int leaveRequestId, Integer managerAccountId) {
        if (leaveRequestId <= 0) {
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
        }
        try {
            Integer managerEmployeeId = findWorkingManagerId(managerAccountId);
            LeaveReviewDetailView detail = leaveRequestDAO.findReviewDetail(leaveRequestId, managerEmployeeId);
            if (detail == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "đơn nghỉ phép"));
            }
            LeaveRequest req = detail.getLeaveRequest();
            detail.setLeaveGroup(loadLeaveGroup(req, new LinkedHashMap<>()));

            // B1: chỉ đơn chờ cần kiểm tra điều kiện duyệt hiện tại; đơn đã xử lý đọc kết quả đã lưu.
            if (req.isPending()) {
                try (Connection connection = DBContext.getConnection()) {
                    String workingPosition = leaveRequestDAO.findWorkingPosition(connection, req.getEmployeeId());
                    LeaveApprovalReview review = leaveApprovalService.review(connection, req, workingPosition);
                    detail.setStaffingRows(review.getStaffingRows());
                    detail.setChecks(review.getChecks());
                    detail.setApprovalBlockMessage(review.getBlockingMessage());
                }
            }

            // B2: nhắc đơn sắp nghỉ/đã bắt đầu; không đặt hạn duyệt hoặc tự đổi trạng thái.
            if (req.isPending() && detail.isLeaveDatePassed()) {
                detail.setBanner("Đơn đã bắt đầu nghỉ. Cần xác nhận phần thời gian nghỉ đã qua trước khi duyệt muộn.");
            } else if (isNearLeaveDate(req, DateTimeUtil.today())) {
                detail.setBanner("Đơn sắp đến ngày nghỉ.");
            }

            return ServiceResult.ok(detail);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Nhóm mở rộng: đọc đầy đủ từng đơn cùng đợt, dùng lại kiểm tra của trang chi tiết. */
    public ServiceResult<List<LeaveReviewDetailView>> getReviewParts(int requestId, Integer managerAccountId) {
        ServiceResult<LeaveReviewDetailView> selected = getReviewDetail(requestId, managerAccountId);
        if (!selected.isSuccess()) {
            return ServiceResult.fail(selected.getMessage());
        }
        LeaveReviewDetailView detail = selected.getData();
        List<LeaveReviewDetailView> parts = new ArrayList<>();
        if (detail.getLeaveGroup() == null) {
            parts.add(detail);
            return ServiceResult.ok(parts);
        }
        for (LeaveRequest request : detail.getLeaveGroup().getRequests()) {
            if (request.getLeaveRequestId() == requestId) {
                parts.add(detail);
                continue;
            }
            ServiceResult<LeaveReviewDetailView> result = getReviewDetail(request.getLeaveRequestId(), managerAccountId);
            if (!result.isSuccess()) {
                return ServiceResult.fail(result.getMessage());
            }
            parts.add(result.getData());
        }
        return ServiceResult.ok(parts);
    }

    /**
     * Duyệt tối đa một trang (50 đơn), giữ thứ tự mã được gửi và bỏ mã trùng.
     * Mỗi đơn dùng transaction riêng của approveRequest, kiểm tra lại trên dữ liệu sau đơn trước.
     * @param managerAccountId tài khoản từ session
     * @param requestIds các mã đơn trên trang; rỗng hoặc mã sai thì không ghi gì
     * @return số đã duyệt và lỗi từng đơn; lỗi một đơn không hoàn tác các đơn đã thành công
     */
    public ServiceResult<LeaveBulkApprovalResult> approveRequests(int managerAccountId, List<Integer> requestIds) {
        if (requestIds == null || requestIds.isEmpty() || requestIds.size() > 50) {
            return ServiceResult.fail(Messages.MSG207_LEAVE_BULK_COUNT);
        }
        // B1: kiểm tra mã và bỏ mã trùng bằng danh sách thường; tối đa 50 phần tử.
        List<Integer> uniqueRequestIds = new ArrayList<>();
        for (Integer requestId : requestIds) {
            if (requestId == null || requestId <= 0) {
                return ServiceResult.fail(Messages.MSG208_LEAVE_LIST_INVALID_RELOAD);
            }
            if (!uniqueRequestIds.contains(requestId)) {
                uniqueRequestIds.add(requestId);
            }
        }

        int approvedCount = 0;
        Map<Integer, String> failedRequests = new LinkedHashMap<>();
        // B2: duyệt theo thứ tự người dùng chọn; giữ lỗi riêng của từng đơn.
        for (Integer requestId : uniqueRequestIds) {
            ServiceResult<Void> result = approveRequest(managerAccountId, requestId);
            if (result.isSuccess()) {
                approvedCount++;
            } else {
                failedRequests.put(requestId, result.getMessage());
            }
        }
        return ServiceResult.ok(new LeaveBulkApprovalResult(approvedCount, failedRequests));
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
        return approveRequest(managerAccountId, leaveRequestId, null);
    }

    /**
     * Duyệt muộn: lý do 5–500 ký tự xác nhận ngày/ca thực tế của phần thời gian đã qua.
     * Ngày bắt đầu đọc lại từ DB đã khóa; không tin cờ duyệt muộn từ trình duyệt.
     * Đơn thường và duyệt nhiều truyền null, nên không thể bỏ qua bước xác nhận này.
     */
    public ServiceResult<Void> approveRequest(int managerAccountId, int leaveRequestId, String lateApprovalReason) {
        try {
            // B1: lấy Manager từ tài khoản đăng nhập; không lấy EmployeeId từ form.
            Employee manager = findEmployeeForAccount(managerAccountId);
            if (!isWorkingManager(manager)) {
                return ServiceResult.fail(Messages.MSG209_LEAVE_NO_PERMISSION);
            }

            // B2: mở Transaction để đảm bảo duyệt đơn + ghi audit log + gửi thông báo diễn ra đồng thời
            try (Connection connection = DBContext.getConnection()) {
                connection.setAutoCommit(false); // Bắt đầu transaction

                try {
                    // B2.1: khóa trước khi đọc lại quyền, trạng thái và quỹ phép.
                    leaveRequestDAO.lockDecisionScope(connection);
                    ServiceResult<LeaveRequest> lockedResult = validateDecision(connection, leaveRequestId, manager.getEmployeeId());
                    if (!lockedResult.isSuccess()) {
                        connection.rollback();
                        return ServiceResult.fail(lockedResult.getMessage());
                    }
                    LeaveRequest request = lockedResult.getData();
                    boolean isLateApproval = request.getFromDate() != null
                            && request.getFromDate().isBefore(DateTimeUtil.today());
                    if (isLateApproval && !ValidationUtil.isLengthValid(lateApprovalReason, 5, 500)) {
                        connection.rollback();
                        return ServiceResult.failField("lateApprovalReason",
                                Messages.MSG210_LEAVE_LATE_APPROVAL_RULE);
                    }
                    String position = leaveRequestDAO.findWorkingPosition(connection, request.getEmployeeId());
                    ServiceResult<Void> checks = validateApproval(connection, request, position);
                    if (!checks.isSuccess()) {
                        connection.rollback();
                        return checks;
                    }

                    // B2.2: cập nhật trạng thái đơn thành Approved (WHERE Status = 'Pending')
                    boolean isApproved = leaveRequestDAO.approve(connection, leaveRequestId, manager.getEmployeeId());
                    if (!isApproved) {
                        // Nếu 0 dòng bị ảnh hưởng, có nghĩa người khác đã duyệt/từ chối đơn này trước đó
                        connection.rollback();
                        return ServiceResult.fail(Messages.MSG211_LEAVE_ALREADY_HANDLED);
                    }

                    // B2.3: ghi nhật ký kiểm toán (Audit Log)
                    auditLogService.logInTransaction(
                            connection,
                            managerAccountId,
                            Role.MANAGER,
                            isLateApproval ? "Duyệt muộn đơn nghỉ phép" : "Duyệt đơn nghỉ phép",
                            "LeaveRequests",
                            leaveRequestId,
                            "Status",
                            "Pending",
                            "Approved",
                            isLateApproval ? lateApprovalReason.trim() : null);

                    // B2.4: gửi thông báo đến chuông của nhân viên
                    String notificationTitle = "Nghỉ phép được duyệt";
                    String notificationContent = "Đơn nghỉ " + formatLeavePeriod(request)
                            + " (ca " + request.getShiftType().getDisplayName() + ") đã được duyệt.";
                    notificationService.notifyEmployeeInTransaction(
                            connection,
                            request.getEmployeeId(),
                            notificationTitle,
                            notificationContent);

                    // B2.5: xác nhận mọi thay đổi thành công
                    connection.commit();
                    return ServiceResult.ok(null);

                } catch (SQLException | RuntimeException e) {
                    connection.rollback(); // Hoàn tác nếu có lỗi
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
                        "rejectReason", Messages.MSG212_REJECT_REASON_LENGTH);
            }

            // B2: lấy Manager đang đăng nhập.
            Employee manager = findEmployeeForAccount(managerAccountId);
            if (!isWorkingManager(manager)) {
                return ServiceResult.fail(Messages.MSG209_LEAVE_NO_PERMISSION);
            }

            // B3: mở Transaction để từ chối + ghi audit log + gửi thông báo
            try (Connection connection = DBContext.getConnection()) {
                connection.setAutoCommit(false); // Bắt đầu transaction

                try {
                    leaveRequestDAO.lockDecisionScope(connection);
                    ServiceResult<LeaveRequest> lockedResult = validateDecision(connection, leaveRequestId, manager.getEmployeeId());
                    if (!lockedResult.isSuccess()) {
                        connection.rollback();
                        return ServiceResult.fail(lockedResult.getMessage());
                    }
                    LeaveRequest request = lockedResult.getData();
                    // B3.1: cập nhật trạng thái đơn thành Rejected
                    boolean isRejected = leaveRequestDAO.reject(
                            connection, leaveRequestId, manager.getEmployeeId(), rejectReason);
                    if (!isRejected) {
                        connection.rollback();
                        return ServiceResult.fail(Messages.MSG211_LEAVE_ALREADY_HANDLED);
                    }

                    // B3.2: ghi nhật ký kiểm toán (Audit Log)
                    auditLogService.logInTransaction(
                            connection,
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
                    String notificationTitle = "Nghỉ phép bị từ chối";
                    String notificationContent = "Đơn nghỉ " + formatLeavePeriod(request)
                            + " bị từ chối. Lý do: " + rejectReason.trim();
                    if (notificationContent.length() > 500) {
                        notificationContent = notificationContent.substring(0, 497) + "...";
                    }
                    notificationService.notifyEmployeeInTransaction(
                            connection,
                            request.getEmployeeId(),
                            notificationTitle,
                            notificationContent);

                    // B3.4: xác nhận giao dịch thành công
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

    /** Chỉ nhân viên Working ở vị trí Manager mới được xử lý đơn. */
    private boolean isWorkingManager(Employee employee) {
        return employee != null && employee.getPosition() == Role.MANAGER
                && employee.getStatus() == Employee.Status.WORKING;
    }

    /** Đọc lại quyền Manager và trạng thái Pending trên Connection đã khóa. */
    private ServiceResult<LeaveRequest> validateDecision(Connection connection, int requestId, int managerEmployeeId)
            throws SQLException {
        if (!"Manager".equals(leaveRequestDAO.findWorkingPosition(connection, managerEmployeeId))) {
            return ServiceResult.fail(Messages.MSG209_LEAVE_NO_PERMISSION);
        }
        LeaveRequest current = leaveRequestDAO.findById(connection, requestId);
        if (current == null || !current.isPending()) {
            return ServiceResult.fail(Messages.MSG213_LEAVE_STATUS_CHANGED_RELOAD);
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
