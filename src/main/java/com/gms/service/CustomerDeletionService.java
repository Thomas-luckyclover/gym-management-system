package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.CustomerDeletionRequestDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.CustomerDeletionReviewView;
import com.gms.dto.CustomerProfileDetailView;
import com.gms.dto.PageResult;
import com.gms.dto.CustomerProfileRowView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.entity.CustomerDeletionRequest;
import com.gms.entity.Employee;
import com.gms.entity.Person;
import com.gms.listener.SessionRegistryListener;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.SQLException;
import com.gms.util.PaginationUtil;
import java.util.List;

/**
 * Nghiệp vụ xóa hồ sơ khách hàng và phê duyệt yêu cầu xóa (BF-07, UC-16, UC-17).
 * Tuân thủ quy tắc nghiệp vụ: BR-15, CBR-16, CBR-115, CBR-86, CBR-04, DBR-28, DBR-54.
 */
public class CustomerDeletionService {

    private final CustomerDeletionRequestDAO customerDeletionRequestDAO;
    private final CustomerDAO customerDAO;
    private final PersonDAO personDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final SelfServiceGuard selfServiceGuard;

    /** Constructor hỗ trợ Unit Test (Dependency Injection). */
    CustomerDeletionService(
            CustomerDeletionRequestDAO customerDeletionRequestDAO,
            CustomerDAO customerDAO,
            PersonDAO personDAO,
            AccountDAO accountDAO,
            EmployeeDAO employeeDAO,
            AuditLogService auditLogService,
            NotificationService notificationService,
            SelfServiceGuard selfServiceGuard) {
        this.customerDeletionRequestDAO = customerDeletionRequestDAO;
        this.customerDAO = customerDAO;
        this.personDAO = personDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.selfServiceGuard = selfServiceGuard;
    }

    /** Constructor mặc định khởi tạo đầy đủ các DAO và Service nghiệp vụ. */
    public CustomerDeletionService() {
        this(
                new CustomerDeletionRequestDAO(),
                new CustomerDAO(),
                new PersonDAO(),
                new AccountDAO(),
                new EmployeeDAO(),
                new AuditLogService(),
                new NotificationService(),
                new SelfServiceGuard()
        );
    }

    // ===== Luồng Lễ tân thao tác: Xóa ngay hoặc Gửi yêu cầu xóa (Màn #28, #29) =====

    /**
     * Lễ tân thực hiện xóa hồ sơ khách hàng (UC-16).
     * - Nếu khách KHÔNG còn gói đang chạy và KHÔNG còn buổi PT Scheduled: Soft delete ngay lập tức.
     * - Nếu khách CÒN gói đang chạy hoặc CÒN buổi PT Scheduled: Tạo CustomerDeletionRequest Pending chờ Quản lý duyệt.
     *
     * @param receptionistAccountId mã tài khoản Lễ tân đang thao tác
     * @param customerId             mã hồ sơ khách hàng cần xóa
     * @param reason                 lý do xóa (5 - 500 ký tự)
     * @return ServiceResult thành công kèm thông báo tương ứng (MSG26 hoặc MSG96), hoặc thất bại kèm lỗi
     */
    public ServiceResult<String> requestOrDeleteCustomer(int receptionistAccountId, int customerId, String reason) {
        // B1: Validate lý do xóa (5 - 500 ký tự)
        if (reason == null || reason.trim().length() < 5) {
            return ServiceResult.failField("reason", String.format(Messages.MSG19_TOO_SHORT, 5));
        }
        if (reason.trim().length() > 500) {
            return ServiceResult.failField("reason", String.format(Messages.MSG10_MAX_LENGTH, 500));
        }

        try {
            // B2: Xác thực nhân viên và tài khoản đang thực hiện
            Account receptionistAccount = accountDAO.findById(receptionistAccountId);
            if (receptionistAccount == null) {
                return ServiceResult.fail(Messages.MSG06_SESSION_EXPIRED);
            }
            Employee receptionistEmployee = employeeDAO.findByPersonId(receptionistAccount.getPersonId());
            if (receptionistEmployee == null || receptionistEmployee.getPosition() != Role.RECEPTIONIST) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: Chặn thao tác trên hồ sơ của chính mình (CBR-115, MSG76)
            if (selfServiceGuard.isOwnCustomerProfile(receptionistAccountId, customerId)) {
                return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
            }

            // B4: Mở transaction, khóa dòng khách hàng chống tranh chấp dữ liệu
            Integer customerAccountIdToInvalidate = null;
            try (Connection connection = DBContext.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Customer customer = customerDAO.findById(connection, customerId, true);
                    if (customer == null) {
                        connection.rollback();
                        return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "hồ sơ khách hàng"));
                    }
                    if ("Inactive".equalsIgnoreCase(customer.getStatus())) {
                        connection.rollback();
                        return ServiceResult.fail("Hồ sơ khách hàng này đã ngừng hoạt động (Inactive).");
                    }
                    if (customerDeletionRequestDAO.hasPendingRequest(connection, customerId)) {
                        connection.rollback();
                        return ServiceResult.fail(Messages.MSG101_PROFILE_PENDING_DELETION);
                    }

                    // B5: Đếm số gói đang chạy (Active, Frozen) và số buổi PT Scheduled (Q1, Q2, DBR-54)
                    int activeSubCount = customerDAO.countActiveSubscriptions(connection, customerId);
                    int scheduledSessionCount = customerDAO.countScheduledSessions(connection, customerId);

                    if (activeSubCount > 0 || scheduledSessionCount > 0) {
                        // Luồng A (CBR-16): Còn gói hoặc buổi tập -> Tạo yêu cầu Pending chờ Quản lý duyệt
                        CustomerDeletionRequest deletionRequest = new CustomerDeletionRequest();
                        deletionRequest.setCustomerId(customerId);
                        deletionRequest.setRequestedByEmployeeId(receptionistEmployee.getEmployeeId());
                        deletionRequest.setReason(reason.trim());
                        deletionRequest.setStatus(CustomerDeletionRequest.Status.PENDING);
                        int requestId = customerDeletionRequestDAO.insert(connection, deletionRequest);

                        // Ghi AuditLog
                        auditLogService.logInTransaction(
                                connection,
                                receptionistAccountId,
                                Role.RECEPTIONIST,
                                "Gửi yêu cầu xóa hồ sơ",
                                "CustomerDeletionRequests",
                                requestId,
                                "Status",
                                null,
                                "Pending",
                                reason.trim()
                        );

                        // Gửi Notification chuông tới tất cả Quản lý
                        Person customerPerson = personDAO.findById(customer.getPersonId());
                        String customerName = customerPerson != null ? customerPerson.getFullName() : customer.getCustomerCode();
                        notificationService.notifyRoleInTransaction(
                                connection,
                                Role.MANAGER,
                                "Yêu cầu xóa hồ sơ · " + customer.getCustomerCode(),
                                "Lễ tân đã gửi yêu cầu xóa hồ sơ cho khách hàng " + customerName + " (" + customer.getCustomerCode() + "). Lý do: " + reason.trim()
                        );

                        connection.commit();
                        return ServiceResult.ok(Messages.MSG96_DELETION_REQUEST_SENT);
                    } else {
                        // Luồng B (BR-15): Không còn gói và không còn buổi tập -> Soft delete ngay lập tức
                        customerAccountIdToInvalidate = deactivateCustomer(connection, customer);

                        // Ghi AuditLog
                        auditLogService.logInTransaction(
                                connection,
                                receptionistAccountId,
                                Role.RECEPTIONIST,
                                "Xóa hồ sơ khách hàng",
                                "Customers",
                                customerId,
                                "Status",
                                customer.getStatus(),
                                "Inactive",
                                reason.trim()
                        );

                        connection.commit();
                    }
                } catch (SQLException e) {
                    connection.rollback();
                    if (e.getMessage() != null && e.getMessage().contains("UX_DeletionRequests_OnePending")) {
                        return ServiceResult.fail(Messages.MSG101_PROFILE_PENDING_DELETION);
                    }
                    throw e;
                }
            }

            // B6: Đăng xuất ngay lập tức mọi phiên hoạt động của tài khoản khách hàng sau khi commit (CBR-86)
            if (customerAccountIdToInvalidate != null) {
                SessionRegistryListener.invalidateSessionsOfAccount(customerAccountIdToInvalidate, null);
            }

            return ServiceResult.ok(String.format(Messages.MSG26_DELETED, "hồ sơ khách hàng"));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Luồng Quản lý phê duyệt hoặc từ chối (Màn #32) =====

    /**
     * Quản lý phê duyệt yêu cầu xóa hồ sơ (UC-17 Luồng B).
     *
     * @param managerAccountId mã tài khoản Quản lý đang duyệt
     * @param requestId        mã yêu cầu xóa cần duyệt
     * @return ServiceResult thành công hoặc thất bại
     */
    public ServiceResult<String> approveDeletion(int managerAccountId, int requestId) {
        try {
            // B1: Xác thực Quản lý
            Account managerAccount = accountDAO.findById(managerAccountId);
            if (managerAccount == null) {
                return ServiceResult.fail(Messages.MSG06_SESSION_EXPIRED);
            }
            Employee managerEmployee = employeeDAO.findByPersonId(managerAccount.getPersonId());
            if (managerEmployee == null || (managerEmployee.getPosition() != Role.MANAGER
                    && managerEmployee.getPosition() != Role.ADMIN)) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: Thực hiện phê duyệt trong Transaction với khóa dòng
            Integer customerAccountIdToInvalidate = null;
            try (Connection connection = DBContext.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    CustomerDeletionRequest deletionRequest = customerDeletionRequestDAO.findById(connection, requestId, true);
                    if (deletionRequest == null) {
                        connection.rollback();
                        return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "yêu cầu xóa"));
                    }
                    if (deletionRequest.getStatus() != CustomerDeletionRequest.Status.PENDING) {
                        connection.rollback();
                        return ServiceResult.fail("Yêu cầu này đã được xử lý bởi người khác.");
                    }

                    // Chặn tự duyệt hồ sơ của chính mình (CBR-115, MSG76)
                    if (selfServiceGuard.isOwnCustomerProfile(managerAccountId, deletionRequest.getCustomerId())) {
                        connection.rollback();
                        return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
                    }

                    // B2.1: đọc lại hồ sơ trong transaction trước khi chấp nhận yêu cầu.
                    Customer customer = customerDAO.findById(connection, deletionRequest.getCustomerId(), true);
                    if (customer == null || "Inactive".equals(customer.getStatus())) {
                        connection.rollback();
                        return ServiceResult.fail("Hồ sơ không còn hoạt động. Vui lòng tải lại danh sách.");
                    }

                    // B2.2: chỉ duyệt khi yêu cầu vẫn Pending.
                    boolean approved = customerDeletionRequestDAO.approve(connection, requestId, managerEmployee.getEmployeeId());
                    if (!approved) {
                        connection.rollback();
                        return ServiceResult.fail("Yêu cầu này đã được xử lý bởi người khác.");
                    }

                    // B2.3: ngừng hồ sơ, giữ nguyên giao dịch, gói và buổi tập để tra cứu.
                    customerAccountIdToInvalidate = deactivateCustomer(connection, customer);

                    // Ghi AuditLog
                    auditLogService.logInTransaction(
                            connection,
                            managerAccountId,
                            Role.MANAGER,
                            "Duyệt yêu cầu xóa hồ sơ",
                            "CustomerDeletionRequests",
                            requestId,
                            "Status",
                            "Pending",
                            "Approved",
                            deletionRequest.getReason()
                    );

                    // Gửi Notification chuông thông báo kết quả cho Lễ tân đã gửi yêu cầu (Q7)
                    String customerCode = customer.getCustomerCode();
                    notificationService.notifyEmployeeInTransaction(
                            connection,
                            deletionRequest.getRequestedByEmployeeId(),
                            "Yêu cầu xóa hồ sơ đã được duyệt · " + customerCode,
                            "Quản lý đã duyệt yêu cầu xóa hồ sơ khách hàng " + customerCode + " (Mã yêu cầu #" + requestId + ")."
                    );

                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            }

            // B3: Đăng xuất mọi phiên của tài khoản khách hàng sau khi transaction commit (CBR-86)
            if (customerAccountIdToInvalidate != null) {
                SessionRegistryListener.invalidateSessionsOfAccount(customerAccountIdToInvalidate, null);
            }

            return ServiceResult.ok("Đã duyệt yêu cầu xóa và ngừng hoạt động hồ sơ khách hàng.");

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Quản lý từ chối yêu cầu xóa hồ sơ (UC-17 Luồng C).
     *
     * @param managerAccountId mã tài khoản Quản lý đang từ chối
     * @param requestId        mã yêu cầu xóa cần từ chối
     * @param rejectReason     lý do từ chối (5 - 500 ký tự)
     * @return ServiceResult thành công hoặc thất bại
     */
    public ServiceResult<String> rejectDeletion(int managerAccountId, int requestId, String rejectReason) {
        // B1: Validate lý do từ chối (5 - 500 ký tự)
        if (rejectReason == null || rejectReason.trim().length() < 5) {
            return ServiceResult.failField("rejectReason", String.format(Messages.MSG19_TOO_SHORT, 5));
        }
        if (rejectReason.trim().length() > 500) {
            return ServiceResult.failField("rejectReason", String.format(Messages.MSG10_MAX_LENGTH, 500));
        }

        try {
            // B2: Xác thực Quản lý
            Account managerAccount = accountDAO.findById(managerAccountId);
            if (managerAccount == null) {
                return ServiceResult.fail(Messages.MSG06_SESSION_EXPIRED);
            }
            Employee managerEmployee = employeeDAO.findByPersonId(managerAccount.getPersonId());
            if (managerEmployee == null || (managerEmployee.getPosition() != Role.MANAGER
                    && managerEmployee.getPosition() != Role.ADMIN)) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: Thực hiện từ chối trong Transaction với khóa dòng
            try (Connection connection = DBContext.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    CustomerDeletionRequest deletionRequest = customerDeletionRequestDAO.findById(connection, requestId, true);
                    if (deletionRequest == null) {
                        connection.rollback();
                        return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "yêu cầu xóa"));
                    }
                    if (deletionRequest.getStatus() != CustomerDeletionRequest.Status.PENDING) {
                        connection.rollback();
                        return ServiceResult.fail("Yêu cầu này đã được xử lý bởi người khác.");
                    }

                    // Chặn thao tác trên hồ sơ của chính mình (CBR-115, MSG76)
                    if (selfServiceGuard.isOwnCustomerProfile(managerAccountId, deletionRequest.getCustomerId())) {
                        connection.rollback();
                        return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
                    }

                    // Cập nhật trạng thái sang Rejected
                    boolean rejected = customerDeletionRequestDAO.reject(connection, requestId, managerEmployee.getEmployeeId(), rejectReason.trim());
                    if (!rejected) {
                        connection.rollback();
                        return ServiceResult.fail("Yêu cầu này đã được xử lý bởi người khác.");
                    }

                    // Ghi AuditLog
                    auditLogService.logInTransaction(
                            connection,
                            managerAccountId,
                            Role.MANAGER,
                            "Từ chối yêu cầu xóa hồ sơ",
                            "CustomerDeletionRequests",
                            requestId,
                            "Status",
                            "Pending",
                            "Rejected",
                            rejectReason.trim()
                    );

                    // Gửi Notification chuông kèm lý do từ chối cho Lễ tân đã gửi yêu cầu (Q7)
                    Customer customer = customerDAO.findById(connection, deletionRequest.getCustomerId(), false);
                    String customerCode = customer != null ? customer.getCustomerCode() : "";
                    notificationService.notifyEmployeeInTransaction(
                            connection,
                            deletionRequest.getRequestedByEmployeeId(),
                            "Yêu cầu xóa hồ sơ bị từ chối · " + customerCode,
                            "Quản lý đã từ chối yêu cầu xóa hồ sơ khách hàng " + customerCode
                                    + " (Mã yêu cầu #" + requestId + "). Lý do: " + rejectReason.trim()
                    );

                    connection.commit();
                    return ServiceResult.ok("Đã từ chối yêu cầu xóa hồ sơ.");

                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Hai nhánh xóa dùng chung phần ngừng hồ sơ; mọi DAO đều nhận cùng connection. */
    private Integer deactivateCustomer(Connection connection, Customer customer) throws SQLException {
        boolean deleted = customerDAO.softDeleteCustomer(connection, customer.getCustomerId());
        if (!deleted) {
            throw new SQLException("Hồ sơ đã thay đổi trong lúc xử lý xóa.");
        }
        personDAO.releasePersonContactIfNoActiveRoles(connection, customer.getPersonId());
        accountDAO.deactivateAccountIfNoActiveRoles(connection, customer.getPersonId());
        Account account = accountDAO.findByPersonId(connection, customer.getPersonId());
        if (account == null) {
            return null;
        }
        return account.getAccountId();
    }

    // ===== Đọc dữ liệu: Servlet không gọi DAO hoặc tự mở connection =====

    /** Chi tiết duyệt: thông tin yêu cầu và hai danh sách chỉ được tải khi người dùng mở đơn. */
    public ServiceResult<CustomerDeletionReviewView> getReviewDetail(int accountId, int requestId) {
        try {
            CustomerDeletionReviewView review = customerDeletionRequestDAO.findReviewDetail(requestId, accountId);
            if (review == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "yêu cầu xóa"));
            }
            try (Connection connection = DBContext.getConnection()) {
                review.setActivePackages(customerDAO.findActiveSubscriptionsByCustomer(connection, review.getCustomerId()));
                review.setScheduledSessions(customerDAO.findScheduledSessionsByCustomer(connection, review.getCustomerId()));
            }
            return ServiceResult.ok(review);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Một DTO chi tiết cho cả hai vai trò; lễ tân không được xem hồ sơ Inactive. */
    public ServiceResult<CustomerProfileDetailView> getProfileDetail(int accountId, int customerId, boolean isManager) {
        try (Connection connection = DBContext.getConnection()) {
            // B1: đọc và kiểm tra hồ sơ được phép xem.
            Customer customer = customerDAO.findById(connection, customerId, false);
            if (customer == null || (!isManager && "Inactive".equals(customer.getStatus()))) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "hồ sơ khách hàng"));
            }
            Person person = personDAO.findById(customer.getPersonId());
            Account account = accountDAO.findById(accountId);
            if (person == null || account == null) {
                return ServiceResult.fail(Messages.MSG06_SESSION_EXPIRED);
            }

            // B2: đọc gói, buổi PT và yêu cầu đang chờ để quyết định nút thao tác.
            List<CustomerDAO.ActiveSubscriptionSummary> packages = customerDAO.findActiveSubscriptionsByCustomer(connection, customerId);
            List<CustomerDAO.ScheduledSessionSummary> sessions = customerDAO.findScheduledSessionsByCustomer(connection, customerId);
            CustomerDeletionRequest pending = customerDeletionRequestDAO.findPendingByCustomerId(connection, customerId);
            Integer pendingId = null;
            if (pending != null) {
                pendingId = pending.getCustomerDeletionRequestId();
            }
            CustomerProfileRowView profile = new CustomerProfileRowView(
                    customerId, customer.getCustomerCode(), customer.getPersonId(), person.getFullName(),
                    person.getPhone(), person.getEmail(), customer.getCustomerType(), customer.getStatus(),
                    null, !packages.isEmpty() || !sessions.isEmpty(), pending != null, pendingId,
                    customer.getJoinedAt(), customer.getDeletedAt());
            profile.setOwnProfile(account.getPersonId() == customer.getPersonId());
            return ServiceResult.ok(new CustomerProfileDetailView(profile, packages, sessions, pending));
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Đếm và lấy trang trong cùng một nhánh xử lý lỗi, không biến lỗi DB thành danh sách rỗng. */
    public ServiceResult<PageResult<CustomerProfileRowView>> getProfilesPage(
            int accountId, String keyword, String status, boolean isManager, int page, int pageSize) {
        try {
            // B1: chỉ chấp nhận những trạng thái thật trong hệ thống.
            String statusFilter = normalizeStatus(status, isManager);
            int total = customerDAO.countCustomerProfiles(keyword, statusFilter, isManager);
            int currentPage = clampPage(page, pageSize, total);
            int offset = PaginationUtil.calculateOffset(currentPage, pageSize);
            List<CustomerProfileRowView> profiles = customerDAO.findCustomerProfiles(
                    keyword, statusFilter, isManager, offset, pageSize);

            // B2: chỉ đọc tài khoản một lần; không truy vấn thêm cho từng dòng.
            Account account = accountDAO.findById(accountId);
            if (account == null) {
                return ServiceResult.fail(Messages.MSG06_SESSION_EXPIRED);
            }
            for (CustomerProfileRowView profile : profiles) {
                profile.setOwnProfile(profile.getPersonId() == account.getPersonId());
            }
            return ServiceResult.ok(new PageResult<>(profiles, currentPage, pageSize, total));
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Trang chờ duyệt của quản lý; gửi trước được xem trước. */
    public ServiceResult<PageResult<CustomerDeletionReviewView>> getPendingPage(int accountId, int page, int pageSize) {
        try {
            int total = customerDeletionRequestDAO.countPending();
            int currentPage = clampPage(page, pageSize, total);
            List<CustomerDeletionReviewView> requests = customerDeletionRequestDAO.findPendingViews(
                    accountId, PaginationUtil.calculateOffset(currentPage, pageSize), pageSize);
            return ServiceResult.ok(new PageResult<>(requests, currentPage, pageSize, total));
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Trang lịch sử của lễ tân, gồm cả chờ duyệt, đã duyệt và từ chối. */
    public ServiceResult<PageResult<CustomerDeletionReviewView>> getMyRequestsPage(int accountId, int page, int pageSize) {
        try {
            int total = customerDeletionRequestDAO.countByRequesterAccountId(accountId);
            int currentPage = clampPage(page, pageSize, total);
            List<CustomerDeletionReviewView> requests = customerDeletionRequestDAO.findByRequesterAccountViews(
                    accountId, PaginationUtil.calculateOffset(currentPage, pageSize), pageSize);
            return ServiceResult.ok(new PageResult<>(requests, currentPage, pageSize, total));
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Badge không hiển thị số 0 giả khi truy vấn thất bại. */
    public ServiceResult<Integer> getPendingCount() {
        try {
            return ServiceResult.ok(customerDeletionRequestDAO.countPending());
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /** Bộ lọc dùng cho cả Service và Servlet để giá trị hiển thị khớp dữ liệu truy vấn. */
    public String normalizeStatus(String status, boolean isManager) {
        if ("New".equals(status) || "Active".equals(status) || "Frozen".equals(status) || "Expired".equals(status)) {
            return status;
        }
        if (isManager && "Inactive".equals(status)) {
            return status;
        }
        return "ALL";
    }

    /** URL trang quá lớn được đưa về trang cuối, tránh màn rỗng dù vẫn còn kết quả. */
    private int clampPage(int page, int pageSize, int total) {
        int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
        return Math.max(1, Math.min(page, totalPages));
    }
}
