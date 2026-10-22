package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.CustomerDeletionRequestDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.CustomerDeletionReviewView;
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
import java.util.Collections;
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
            if (receptionistEmployee == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: Chặn thao tác trên hồ sơ của chính mình (CBR-115, MSG76)
            if (selfServiceGuard.isOwnCustomerProfile(receptionistAccountId, customerId)) {
                return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
            }

            // B4: Mở transaction, khóa dòng khách hàng chống tranh chấp dữ liệu
            Integer customerAccountIdToInvalidate = null;
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    Customer customer = customerDAO.findById(conn, customerId, true);
                    if (customer == null) {
                        conn.rollback();
                        return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "hồ sơ khách hàng"));
                    }
                    if ("Inactive".equalsIgnoreCase(customer.getStatus())) {
                        conn.rollback();
                        return ServiceResult.fail("Hồ sơ khách hàng này đã ngừng hoạt động (Inactive).");
                    }
                    if (customerDeletionRequestDAO.hasPendingRequest(conn, customerId)) {
                        conn.rollback();
                        return ServiceResult.fail(Messages.MSG101_PROFILE_PENDING_DELETION);
                    }

                    // B5: Đếm số gói đang chạy (Active, Frozen) và số buổi PT Scheduled (Q1, Q2, DBR-54)
                    int activeSubCount = customerDAO.countActiveSubscriptions(conn, customerId);
                    int scheduledSessionCount = customerDAO.countScheduledSessions(conn, customerId);

                    if (activeSubCount > 0 || scheduledSessionCount > 0) {
                        // Luồng A (CBR-16): Còn gói hoặc buổi tập -> Tạo yêu cầu Pending chờ Quản lý duyệt
                        CustomerDeletionRequest req = new CustomerDeletionRequest();
                        req.setCustomerId(customerId);
                        req.setRequestedByEmployeeId(receptionistEmployee.getEmployeeId());
                        req.setReason(reason.trim());
                        req.setStatus(CustomerDeletionRequest.Status.PENDING);
                        int requestId = customerDeletionRequestDAO.insert(conn, req);

                        // Ghi AuditLog
                        auditLogService.logInTransaction(
                                conn,
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
                                conn,
                                Role.MANAGER,
                                "Yêu cầu xóa hồ sơ · " + customer.getCustomerCode(),
                                "Lễ tân đã gửi yêu cầu xóa hồ sơ cho khách hàng " + customerName + " (" + customer.getCustomerCode() + "). Lý do: " + reason.trim()
                        );

                        conn.commit();
                        return ServiceResult.ok(Messages.MSG96_DELETION_REQUEST_SENT);
                    } else {
                        // Luồng B (BR-15): Không còn gói và không còn buổi tập -> Soft delete ngay lập tức
                        customerDAO.softDeleteCustomer(conn, customerId);
                        personDAO.releasePersonContactIfNoActiveRoles(conn, customer.getPersonId());
                        accountDAO.deactivateAccountIfNoActiveRoles(conn, customer.getPersonId());

                        Account customerAccount = accountDAO.findByPersonId(conn, customer.getPersonId());
                        if (customerAccount != null) {
                            customerAccountIdToInvalidate = customerAccount.getAccountId();
                        }

                        // Ghi AuditLog
                        auditLogService.logInTransaction(
                                conn,
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

                        conn.commit();
                    }
                } catch (SQLException e) {
                    conn.rollback();
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
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    CustomerDeletionRequest req = customerDeletionRequestDAO.findById(conn, requestId, true);
                    if (req == null) {
                        conn.rollback();
                        return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "yêu cầu xóa"));
                    }
                    if (req.getStatus() != CustomerDeletionRequest.Status.PENDING) {
                        conn.rollback();
                        return ServiceResult.fail("Yêu cầu này đã được xử lý bởi người khác.");
                    }

                    // Chặn tự duyệt hồ sơ của chính mình (CBR-115, MSG76)
                    if (selfServiceGuard.isOwnCustomerProfile(managerAccountId, req.getCustomerId())) {
                        conn.rollback();
                        return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
                    }

                    // Cập nhật trạng thái yêu cầu sang Approved
                    boolean approved = customerDeletionRequestDAO.approve(conn, requestId, managerEmployee.getEmployeeId());
                    if (!approved) {
                        conn.rollback();
                        return ServiceResult.fail("Yêu cầu này đã được xử lý bởi người khác.");
                    }

                    // Soft delete hồ sơ khách hàng
                    Customer customer = customerDAO.findById(conn, req.getCustomerId(), true);
                    if (customer != null) {
                        customerDAO.softDeleteCustomer(conn, customer.getCustomerId());
                        personDAO.releasePersonContactIfNoActiveRoles(conn, customer.getPersonId());
                        accountDAO.deactivateAccountIfNoActiveRoles(conn, customer.getPersonId());

                        Account customerAccount = accountDAO.findByPersonId(conn, customer.getPersonId());
                        if (customerAccount != null) {
                            customerAccountIdToInvalidate = customerAccount.getAccountId();
                        }
                    }

                    // Ghi AuditLog
                    auditLogService.logInTransaction(
                            conn,
                            managerAccountId,
                            Role.MANAGER,
                            "Duyệt yêu cầu xóa hồ sơ",
                            "CustomerDeletionRequests",
                            requestId,
                            "Status",
                            "Pending",
                            "Approved",
                            req.getReason()
                    );

                    // Gửi Notification chuông thông báo kết quả cho Lễ tân đã gửi yêu cầu (Q7)
                    String customerCode = customer != null ? customer.getCustomerCode() : "";
                    notificationService.notifyEmployeeInTransaction(
                            conn,
                            req.getRequestedByEmployeeId(),
                            "Yêu cầu xóa hồ sơ đã được duyệt · " + customerCode,
                            "Quản lý đã duyệt yêu cầu xóa hồ sơ khách hàng " + customerCode + " (Mã yêu cầu #" + requestId + ")."
                    );

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
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
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    CustomerDeletionRequest req = customerDeletionRequestDAO.findById(conn, requestId, true);
                    if (req == null) {
                        conn.rollback();
                        return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "yêu cầu xóa"));
                    }
                    if (req.getStatus() != CustomerDeletionRequest.Status.PENDING) {
                        conn.rollback();
                        return ServiceResult.fail("Yêu cầu này đã được xử lý bởi người khác.");
                    }

                    // Chặn thao tác trên hồ sơ của chính mình (CBR-115, MSG76)
                    if (selfServiceGuard.isOwnCustomerProfile(managerAccountId, req.getCustomerId())) {
                        conn.rollback();
                        return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
                    }

                    // Cập nhật trạng thái sang Rejected
                    boolean rejected = customerDeletionRequestDAO.reject(conn, requestId, managerEmployee.getEmployeeId(), rejectReason.trim());
                    if (!rejected) {
                        conn.rollback();
                        return ServiceResult.fail("Yêu cầu này đã được xử lý bởi người khác.");
                    }

                    // Ghi AuditLog
                    auditLogService.logInTransaction(
                            conn,
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
                    Customer customer = customerDAO.findById(conn, req.getCustomerId(), false);
                    String customerCode = customer != null ? customer.getCustomerCode() : "";
                    notificationService.notifyEmployeeInTransaction(
                            conn,
                            req.getRequestedByEmployeeId(),
                            "Yêu cầu xóa hồ sơ bị từ chối · " + customerCode,
                            "Quản lý đã từ chối yêu cầu xóa hồ sơ khách hàng " + customerCode
                                    + " (Mã yêu cầu #" + requestId + "). Lý do: " + rejectReason.trim()
                    );

                    conn.commit();
                    return ServiceResult.ok("Đã từ chối yêu cầu xóa hồ sơ.");

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Truy vấn dữ liệu hiển thị (Màn #28, #30, #31, #32) =====

    /**
     * Lấy chi tiết thông tin yêu cầu xóa cho Quản lý xem xét và phê duyệt (Màn #32).
     *
     * @param managerAccountId mã tài khoản Quản lý đang duyệt (để tính cờ isOwnProfile theo CBR-115)
     * @param requestId        mã yêu cầu xóa
     * @return ServiceResult chứa CustomerDeletionReviewView
     */
    public ServiceResult<CustomerDeletionReviewView> getReviewDetail(int managerAccountId, int requestId) {
        try {
            CustomerDeletionReviewView view = customerDeletionRequestDAO.findReviewDetail(requestId, managerAccountId);
            if (view == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "yêu cầu xóa"));
            }
            return ServiceResult.ok(view);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy danh sách phân trang các yêu cầu xóa Pending cho Quản lý duyệt (#32 hoặc Tab trên #30).
     */
    public ServiceResult<List<CustomerDeletionReviewView>> getPendingReviewList(int managerAccountId, int page, int pageSize) {
        try {
            int offset = Math.max(0, (page - 1) * pageSize);
            List<CustomerDeletionReviewView> list = customerDeletionRequestDAO.findPendingViews(managerAccountId, offset, pageSize);
            return ServiceResult.ok(list != null ? list : Collections.emptyList());
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đếm tổng số lượng yêu cầu xóa đang Pending (để gắn huy hiệu badge trên sidebar Quản lý).
     */
    public int countPendingReviews() {
        try {
            return customerDeletionRequestDAO.countPending();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Lấy danh sách các yêu cầu xóa do chính Lễ tân gửi (Q8 - Tab Yêu cầu của tôi trên Màn #28).
     */
    public ServiceResult<List<CustomerDeletionReviewView>> getMyRequestsList(int receptionistAccountId, int page, int pageSize) {
        try {
            int offset = Math.max(0, (page - 1) * pageSize);
            List<CustomerDeletionReviewView> list = customerDeletionRequestDAO.findByRequesterAccountViews(receptionistAccountId, offset, pageSize);
            return ServiceResult.ok(list != null ? list : Collections.emptyList());
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đếm tổng số lượng yêu cầu xóa do chính Lễ tân gửi.
     */
    public int countMyRequests(int receptionistAccountId) {
        try {
            return customerDeletionRequestDAO.countByRequesterAccountId(receptionistAccountId);
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Tìm kiếm và phân trang danh sách hồ sơ khách hàng cho Lễ tân (#28) và Quản lý (#30).
     */
    public ServiceResult<List<CustomerProfileRowView>> getCustomerProfiles(
            String keyword,
            String statusFilter,
            Boolean pendingOnly,
            boolean isManager,
            int page,
            int pageSize) {
        try {
            int offset = Math.max(0, (page - 1) * pageSize);
            List<CustomerProfileRowView> list = customerDAO.findCustomerProfiles(
                    keyword,
                    statusFilter,
                    pendingOnly,
                    isManager,
                    offset,
                    pageSize
            );
            return ServiceResult.ok(list != null ? list : Collections.emptyList());
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Đếm tổng số lượng hồ sơ khách hàng thỏa mãn bộ lọc.
     */
    public int countCustomerProfiles(
            String keyword,
            String statusFilter,
            Boolean pendingOnly,
            boolean isManager) {
        try {
            return customerDAO.countCustomerProfiles(keyword, statusFilter, pendingOnly, isManager);
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
