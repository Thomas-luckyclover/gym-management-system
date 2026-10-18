package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.FacilityDAO;
import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Facility;
import com.gms.entity.MaintenanceRequest;
import com.gms.util.DBContext;
import com.gms.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service Layer: Encapsulates the core business logic and transaction boundaries
 * for Facility Maintenance Workflows (BF-06 / UC-28, UC-29).
 *
 * <p><b>Architectural Responsibility:</b></p>
 * Sits between the Presentation Layer (Servlets) and the Persistence Layer (DAOs).
 * The Service layer is responsible for:
 * <ul>
 *   <li>Enforcing domain business rules and multi-step workflows.</li>
 *   <li>Managing database transaction lifecycles ({@code setAutoCommit(false)}, {@code commit()}, and {@code rollback()}).</li>
 *   <li>Authorizing employee position roles (Receptionist vs. Manager).</li>
 *   <li>Recording immutable audit logs for regulatory and compliance tracking.</li>
 *   <li>Emitting contextual system notifications.</li>
 * </ul>
 */
public class MaintenanceRequestService {

    private final MaintenanceRequestDAO maintenanceRequestDAO;
    private final FacilityDAO facilityDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public MaintenanceRequestService() {
        maintenanceRequestDAO = new MaintenanceRequestDAO();
        facilityDAO = new FacilityDAO();
        accountDAO = new AccountDAO();
        employeeDAO = new EmployeeDAO();
        auditLogService = new AuditLogService();
        notificationService =
                new NotificationService();
    }

    /**
     * Package-private constructor for dependency injection during unit testing.
     */
    MaintenanceRequestService(
            MaintenanceRequestDAO maintenanceRequestDAO,
            FacilityDAO facilityDAO,
            AccountDAO accountDAO,
            EmployeeDAO employeeDAO,
            AuditLogService auditLogService,
            NotificationService notificationService
    ) {
        this.maintenanceRequestDAO = maintenanceRequestDAO;
        this.facilityDAO = facilityDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    /**
     * Factory method for obtaining a database connection.
     * Overridable by tests to supply connection wrappers or mock connections.
     */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }

    // ===== Tạo yêu cầu =====

    /**
     * Receptionist creates a new maintenance request for gym equipment.
     *
     * <p><b>Business Rules & Constraints (BF-06):</b></p>
     * <ul>
     *   <li><b>Facility Status Rule:</b> Allowed for both {@code Available} and {@code UnderMaintenance}
     *       facilities because equipment may have minor issues while already partially flagged. Only
     *       {@code Retired} (permanently decommissioned) or non-existent facilities are rejected.</li>
     *   <li><b>Zero Available Quantity Rule:</b> Creating requests is NOT blocked when {@code AvailableQuantity = 0}.
     *       In a gym setting, multiple tickets may concern the same physical unit or batch. Front-end displays
     *       a clear warning of existing unfinished requests, but the Receptionist can proceed.</li>
     *   <li><b>Transaction Boundary:</b> All steps (code generation, insertion, audit logging, notification)
     *       execute within a single database transaction. If any step fails, changes are rolled back.</li>
     *   <li><b>Audit Trail:</b> The acting role is strictly recorded as {@code Role.RECEPTIONIST}.</li>
     * </ul>
     *
     * @param accountId        authenticated receptionist account ID
     * @param facilityId       targeted facility ID
     * @param priorityValue    priority string ("Low", "Normal", "Urgent")
     * @param description      detailed description of malfunction (10-500 characters)
     * @param warningTagPlaced whether physical warning tag was placed on equipment
     * @return ServiceResult containing generated request code (e.g. "BT-0001") or validation errors
     */
    public ServiceResult<String> createRequest(
            int accountId,
            int facilityId,
            String priorityValue,
            String description,
            boolean warningTagPlaced
    ) {

        // Step 1: Input validation before acquiring database resources
        if (facilityId <= 0) {
            return ServiceResult.failField(
                    "facilityId",
                    String.format(Messages.MSG21_SELECT_REQUIRED, "thiết bị")
            );
        }

        MaintenanceRequest.Priority priority =
                MaintenanceRequest.Priority.fromDbValue(priorityValue);

        if (priority == null) {
            return ServiceResult.failField(
                    "priority",
                    String.format(Messages.MSG21_SELECT_REQUIRED, "mức ưu tiên")
            );
        }

        if (!ValidationUtil.isLengthValid(description, 10, 500)) {
            return ServiceResult.failField(
                    "description",
                    "Mô tả hỏng hóc phải từ 10 đến 500 ký tự."
            );
        }

        // Step 2: Open transactional boundary (setAutoCommit = false)
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Verify the acting account belongs to an active Receptionist
                Employee receptionist =
                        findEmployeeForAccount(accountId, Role.RECEPTIONIST);

                if (receptionist == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Verify facility exists and is not retired (Available and UnderMaintenance are both allowed)
                Facility facility =
                        facilityDAO.findById(connection, facilityId);

                if (facility == null
                        || facility.getStatus() == Facility.Status.RETIRED) {

                    connection.rollback();

                    return ServiceResult.failField(
                            "facilityId",
                            "Thiết bị không còn khả dụng để tạo yêu cầu bảo trì."
                    );
                }

                // B4: tạo yêu cầu Open
                MaintenanceRequest request =
                        new MaintenanceRequest();

                request.setRequestCode(
                        maintenanceRequestDAO.generateNextRequestCode(connection)
                );
                request.setFacilityId(facilityId);
                request.setReportedByEmployeeId(
                        receptionist.getEmployeeId()
                );
                request.setPriority(priority);
                request.setWarningTagPlaced(warningTagPlaced);
                request.setDescription(description.trim());
                request.setStatus(MaintenanceRequest.Status.OPEN);

                int requestId =
                        maintenanceRequestDAO.insert(connection, request);

                // B5: ghi AuditLog với vai trò Receptionist
                auditLogService.logInTransaction(
                        connection,
                        accountId,
                        Role.RECEPTIONIST,
                        "Tạo yêu cầu bảo trì",
                        "MaintenanceRequest",
                        requestId,
                        "Status",
                        null,
                        MaintenanceRequest.Status.OPEN.getDbValue(),
                        null
                );

                /*
                 * Gửi tới các Manager đang làm việc.
                 *
                 * Không ghi "đang chờ xử lý" để notification
                 * không trở nên sai nghĩa sau khi một Manager
                 * đã xử lý đơn.
                 */
                notificationService.notifyRoleInTransaction(
                        connection,
                        Role.MANAGER,
                        "Bảo trì · Yêu cầu mới",
                        request.getRequestCode()
                                + " · "
                                + facility.getFacilityName()
                                + " vừa được Lễ tân báo hỏng."
                );

                connection.commit();

                return ServiceResult.ok(request.getRequestCode());

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Duyệt yêu cầu =====

    /**
     * Manager approves an Open maintenance request, transitioning it to InProgress (BR-49).
     *
     * <p><b>Business Logic & State Transitions:</b></p>
     * <ul>
     *   <li><b>Role Verification:</b> Only active employees with {@code Role.MANAGER} can approve requests.</li>
     *   <li><b>Pessimistic Row Locking:</b> Uses {@code findByIdForUpdate} to lock the request record,
     *       preventing concurrent approvals or conflicting updates.</li>
     *   <li><b>Precondition:</b> The request must currently be in {@code Status.OPEN}.</li>
     *   <li><b>Facility State Transition (CBR-101):</b> If the manager specifies {@code setUnderMaintenance = true}
     *       and the facility is currently {@code Available}, its status transitions to {@code UnderMaintenance}.</li>
     *   <li><b>Separate Audit Logging:</b> When the facility status changes, a distinct {@code AuditLog} entry
     *       is recorded for the {@code Facility} entity (with {@code Role.MANAGER}) alongside the request's
     *       own status transition audit log.</li>
     *   <li><b>Quantity Impact:</b> Transitioning from {@code Open} to {@code InProgress} maintains the request
     *       within the unfinished set; thus {@code PendingQuantity} and {@code AvailableQuantity} remain stable.</li>
     * </ul>
     *
     * @param accountId           authenticated manager account ID
     * @param requestId           ID of the request to approve
     * @param setUnderMaintenance whether to mark the physical facility as UnderMaintenance
     * @return ServiceResult indicating success or failure message
     */
    public ServiceResult<Void> approveRequest(
            int accountId,
            int requestId,
            boolean setUnderMaintenance
    ) {

        if (requestId <= 0) {
            return ServiceResult.fail(
                    String.format(Messages.MSG08_NOT_FOUND, "yêu cầu bảo trì")
            );
        }

        // Open transaction boundary
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Step 1: Verify manager role (BR-49)
                Employee manager =
                        findEmployeeForAccount(accountId, Role.MANAGER);

                if (manager == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Step 2: Lock and verify current request state to prevent race conditions
                MaintenanceRequest request =
                        maintenanceRequestDAO.findByIdForUpdate(
                                connection,
                                requestId
                        );

                if (request == null) {
                    connection.rollback();
                    return ServiceResult.fail(
                            String.format(
                                    Messages.MSG08_NOT_FOUND,
                                    "yêu cầu bảo trì"
                            )
                    );
                }

                if (!request.isOpen()) {
                    connection.rollback();
                    return ServiceResult.fail(
                            "Chỉ yêu cầu đang Chờ duyệt mới được phê duyệt."
                    );
                }

                // Step 3: Transition request status from Open to InProgress (BR-49)
                boolean updated =
                        maintenanceRequestDAO.approve(
                                connection,
                                requestId,
                                manager.getEmployeeId(),
                                setUnderMaintenance
                        );

                if (!updated) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Step 4: Conditionally update physical Facility status if manager opted in (CBR-101)
                if (setUnderMaintenance) {
                    Facility facility =
                            facilityDAO.findById(connection, request.getFacilityId());

                    if (facility != null
                            && facility.getStatus() != Facility.Status.UNDER_MAINTENANCE) {

                        Facility.Status oldStatus = facility.getStatus();
                        boolean facilityUpdated =
                                facilityDAO.updateStatus(
                                        connection,
                                        request.getFacilityId(),
                                        Facility.Status.UNDER_MAINTENANCE
                                );

                        if (!facilityUpdated) {
                            connection.rollback();
                            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                        }

                        // Write a dedicated AuditLog entry for the Facility status transition
                        auditLogService.logInTransaction(
                                connection,
                                accountId,
                                Role.MANAGER,
                                "Cập nhật trạng thái thiết bị",
                                "Facility",
                                request.getFacilityId(),
                                "Status",
                                oldStatus.getDbValue(),
                                Facility.Status.UNDER_MAINTENANCE.getDbValue(),
                                "Chuyển sang bảo trì khi duyệt yêu cầu " + request.getRequestCode()
                        );
                    }
                }

                // Step 5: Write request AuditLog with Role.MANAGER
                auditLogService.logInTransaction(
                        connection,
                        accountId,
                        Role.MANAGER,
                        "Duyệt yêu cầu bảo trì",
                        "MaintenanceRequest",
                        requestId,
                        "Status",
                        MaintenanceRequest.Status.OPEN.getDbValue(),
                        MaintenanceRequest.Status.IN_PROGRESS.getDbValue(),
                        null
                );

                notificationService.notifyEmployeeInTransaction(
                        connection,
                        request.getReportedByEmployeeId(),
                        "Bảo trì · Đã được duyệt",
                        request.getRequestCode()
                                + " · Yêu cầu đã chuyển sang trạng thái Đang xử lý."
                );

                connection.commit();

                return ServiceResult.ok(null);

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Manager rejects an Open maintenance request with a required justification reason (BR-49).
     *
     * <p><b>Quantity Impact:</b> Transitioning from {@code Open} to {@code Rejected} removes the request
     * from the unfinished set, immediately restoring the facility's {@code AvailableQuantity} on next refresh.</p>
     *
     * @param accountId    authenticated manager account ID
     * @param requestId    ID of the request to reject
     * @param rejectReason justification reason (5-500 characters)
     * @return ServiceResult indicating success or failure
     */
    public ServiceResult<Void> rejectRequest(
            int accountId,
            int requestId,
            String rejectReason
    ) {

        // Validate reason length before acquiring database connection
        if (!ValidationUtil.isValidReason(rejectReason)) {
            return ServiceResult.failField(
                    "rejectReason",
                    "Lý do từ chối phải từ 5 đến 500 ký tự."
            );
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Step 1: Verify manager role (BR-49)
                Employee manager =
                        findEmployeeForAccount(accountId, Role.MANAGER);

                if (manager == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Step 2: Lock request and verify it is currently Open
                MaintenanceRequest request =
                        maintenanceRequestDAO.findByIdForUpdate(
                                connection,
                                requestId
                        );

                if (request == null) {
                    connection.rollback();
                    return ServiceResult.fail(
                            String.format(
                                    Messages.MSG08_NOT_FOUND,
                                    "yêu cầu bảo trì"
                            )
                    );
                }

                if (!request.isOpen()) {
                    connection.rollback();
                    return ServiceResult.fail(
                            "Chỉ yêu cầu đang Chờ duyệt mới được từ chối."
                    );
                }

                // Step 3: Transition from Open to Rejected in DAO (BR-49)
                boolean updated =
                        maintenanceRequestDAO.reject(
                                connection,
                                requestId,
                                manager.getEmployeeId(),
                                rejectReason.trim()
                        );

                if (!updated) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Step 4: Write AuditLog with Role.MANAGER and rejection reason
                auditLogService.logInTransaction(
                        connection,
                        accountId,
                        Role.MANAGER,
                        "Từ chối yêu cầu bảo trì",
                        "MaintenanceRequest",
                        requestId,
                        "Status",
                        MaintenanceRequest.Status.OPEN.getDbValue(),
                        MaintenanceRequest.Status.REJECTED.getDbValue(),
                        rejectReason.trim()
                );

                notificationService.notifyEmployeeInTransaction(
                        connection,
                        request.getReportedByEmployeeId(),
                        "Bảo trì · Bị từ chối",
                        request.getRequestCode()
                                + " · Yêu cầu đã bị từ chối. "
                                + "Mở yêu cầu để xem lý do."
                );

                connection.commit();

                return ServiceResult.ok(null);

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Đóng yêu cầu =====

    /**
     * Receptionist inspects and closes an InProgress maintenance request after physical verification (BR-48).
     *
     * <p><b>Business Logic & State Transitions:</b></p>
     * <ul>
     *   <li><b>Role Verification:</b> Only active employees with {@code Role.RECEPTIONIST} can close requests.</li>
     *   <li><b>Pessimistic Row Locking:</b> Uses {@code findByIdForUpdate} to lock the request record.</li>
     *   <li><b>Precondition:</b> The request must currently be in {@code Status.IN_PROGRESS}.</li>
     *   <li><b>Facility Status Recovery (CBR-102):</b> Checks if any OTHER active InProgress requests exist
     *       for the same facility. If none remain ({@code !hasOtherInProgress}) and the facility is currently
     *       {@code UnderMaintenance}, it is automatically restored to {@code Available}.</li>
     *   <li><b>Separate Audit Logging:</b> If the facility is restored, a separate {@code AuditLog} entry
     *       is written for the {@code Facility} entity with {@code Role.RECEPTIONIST}.</li>
     *   <li><b>Quantity Impact:</b> Closing the request decrements {@code PendingQuantity} and restores
     *       {@code AvailableQuantity} in subsequent catalog calculations.</li>
     * </ul>
     *
     * @param accountId      authenticated receptionist account ID
     * @param requestId      ID of the request to close
     * @param acceptanceNote verification and acceptance notes (5-500 characters)
     * @return ServiceResult indicating success or failure
     */
    public ServiceResult<Void> closeRequest(
            int accountId,
            int requestId,
            String acceptanceNote
    ) {

        // Validate acceptance note length before acquiring database resources
        if (!ValidationUtil.isLengthValid(acceptanceNote, 5, 500)) {
            return ServiceResult.failField(
                    "acceptanceNote",
                    "Ghi chú nghiệm thu phải từ 5 đến 500 ký tự."
            );
        }

        // Open transaction boundary
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Step 1: Verify receptionist role (BR-48)
                Employee receptionist =
                        findEmployeeForAccount(accountId, Role.RECEPTIONIST);

                if (receptionist == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Step 2: Lock request and verify it is currently InProgress
                MaintenanceRequest request =
                        maintenanceRequestDAO.findByIdForUpdate(
                                connection,
                                requestId
                        );

                if (request == null) {
                    connection.rollback();
                    return ServiceResult.fail(
                            String.format(
                                    Messages.MSG08_NOT_FOUND,
                                    "yêu cầu bảo trì"
                            )
                    );
                }

                if (!request.isInProgress()) {
                    connection.rollback();
                    return ServiceResult.fail(
                            "Chỉ yêu cầu đang xử lý mới được nghiệm thu và đóng."
                    );
                }

                // Step 3: Transition request to CLOSED in DAO (BR-48)
                boolean updated =
                        maintenanceRequestDAO.close(
                                connection,
                                requestId,
                                receptionist.getEmployeeId(),
                                acceptanceNote.trim()
                        );

                if (!updated) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Step 4: Check if any other InProgress tickets exist for this facility (CBR-102)
                boolean hasOtherInProgress =
                        maintenanceRequestDAO.hasInProgressRequest(
                                connection,
                                request.getFacilityId()
                        );

                // If no other in-progress tickets remain, restore the facility to Available
                if (!hasOtherInProgress) {
                    Facility facility =
                            facilityDAO.findById(connection, request.getFacilityId());

                    if (facility != null
                            && facility.getStatus() != Facility.Status.AVAILABLE) {

                        Facility.Status oldStatus = facility.getStatus();
                        boolean facilityUpdated =
                                facilityDAO.updateStatus(
                                        connection,
                                        request.getFacilityId(),
                                        Facility.Status.AVAILABLE
                                );

                        if (!facilityUpdated) {
                            connection.rollback();
                            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                        }

                        // Write a dedicated AuditLog entry for the Facility status recovery
                        auditLogService.logInTransaction(
                                connection,
                                accountId,
                                Role.RECEPTIONIST,
                                "Khôi phục trạng thái thiết bị",
                                "Facility",
                                request.getFacilityId(),
                                "Status",
                                oldStatus.getDbValue(),
                                Facility.Status.AVAILABLE.getDbValue(),
                                "Khôi phục khả dụng sau khi đóng yêu cầu " + request.getRequestCode()
                        );
                    }
                }

                // Step 5: Write request closure AuditLog with Role.RECEPTIONIST
                auditLogService.logInTransaction(
                        connection,
                        accountId,
                        Role.RECEPTIONIST,
                        "Đóng yêu cầu bảo trì",
                        "MaintenanceRequest",
                        requestId,
                        "Status",
                        MaintenanceRequest.Status.IN_PROGRESS.getDbValue(),
                        MaintenanceRequest.Status.CLOSED.getDbValue(),
                        acceptanceNote.trim()
                );

                if (request.getReviewedByEmployeeId() != null) {

                    notificationService.notifyEmployeeInTransaction(
                            connection,
                            request.getReviewedByEmployeeId(),
                            "Bảo trì · Đã hoàn tất",
                            request.getRequestCode()
                                    + " · Yêu cầu đã được Lễ tân nghiệm thu và đóng."
                    );
                }

                connection.commit();

                return ServiceResult.ok(null);

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Dữ liệu màn hình =====

    public ServiceResult<List<Facility>> getSelectableFacilities() {
        try {
            return ServiceResult.ok(
                    facilityDAO.findSelectableFacilities()
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    public ServiceResult<List<MaintenanceRequestDAO.UnfinishedRequestSummary>>
    getUnfinishedRequests(int facilityId) {
        try {
            return ServiceResult.ok(
                    maintenanceRequestDAO.findUnfinishedByFacilityId(facilityId)
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Retrieves all active unfinished requests mapped by FacilityId.
     * Serves front-end dynamic warnings when selecting a facility during request creation.
     */
    public ServiceResult<Map<Integer, List<MaintenanceRequestDAO.UnfinishedRequestSummary>>>
    getUnfinishedRequestsMap() {
        try {
            return ServiceResult.ok(
                    maintenanceRequestDAO.findUnfinishedRequestsMap()
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    public ServiceResult<PageResult<MaintenanceRequestDAO.MaintenanceRequestView>>
    getReceptionistPage(
            int accountId,
            String keyword,
            String statusValue,
            int page,
            int pageSize
    ) {
        return getReceptionistPage(accountId, keyword, statusValue, null, null, page, pageSize);
    }

    /**
     * Retrieves a paginated list of maintenance requests for Receptionists with extended filters.
     * Receptionists have full visibility across all tickets to answer member inquiries.
     */
    public ServiceResult<PageResult<MaintenanceRequestDAO.MaintenanceRequestView>>
    getReceptionistPage(
            int accountId,
            String keyword,
            String statusValue,
            String priorityValue,
            LocalDate reportedDate,
            int page,
            int pageSize
    ) {

        try {
            Employee receptionist =
                    findEmployeeForAccount(accountId, Role.RECEPTIONIST);

            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }

            MaintenanceRequest.Status status =
                    MaintenanceRequest.Status.fromDbValue(statusValue);
            MaintenanceRequest.Priority priority =
                    MaintenanceRequest.Priority.fromDbValue(priorityValue);

            long totalItems =
                    maintenanceRequestDAO.countForReceptionist(
                            keyword,
                            status,
                            priority,
                            reportedDate
                    );

            List<MaintenanceRequestDAO.MaintenanceRequestView> items =
                    maintenanceRequestDAO.findForReceptionist(
                            keyword,
                            status,
                            priority,
                            reportedDate,
                            page,
                            pageSize
                    );

            return ServiceResult.ok(
                    new PageResult<>(
                            items,
                            page,
                            pageSize,
                            totalItems
                    )
            );

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    public ServiceResult<PageResult<MaintenanceRequestDAO.MaintenanceRequestView>>
    getManagerPage(
            String keyword,
            String statusValue,
            int page,
            int pageSize
    ) {
        return getManagerPage(keyword, statusValue, null, null, page, pageSize);
    }

    /**
     * Retrieves a paginated list of maintenance requests for Managers with keyword, status, priority, and date filters.
     */
    public ServiceResult<PageResult<MaintenanceRequestDAO.MaintenanceRequestView>>
    getManagerPage(
            String keyword,
            String statusValue,
            String priorityValue,
            LocalDate reportedDate,
            int page,
            int pageSize
    ) {

        try {
            MaintenanceRequest.Status status =
                    MaintenanceRequest.Status.fromDbValue(statusValue);
            MaintenanceRequest.Priority priority =
                    MaintenanceRequest.Priority.fromDbValue(priorityValue);

            long totalItems =
                    maintenanceRequestDAO.countAll(
                            keyword,
                            status,
                            priority,
                            reportedDate
                    );

            List<MaintenanceRequestDAO.MaintenanceRequestView> items =
                    maintenanceRequestDAO.findAll(
                            keyword,
                            status,
                            priority,
                            reportedDate,
                            page,
                            pageSize
                    );

            return ServiceResult.ok(
                    new PageResult<>(
                            items,
                            page,
                            pageSize,
                            totalItems
                    )
            );

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    public ServiceResult<MaintenanceRequestDAO.MaintenanceRequestView>
    getRequestDetail(int requestId) {

        if (requestId <= 0) {
            return ServiceResult.fail(
                    String.format(Messages.MSG08_NOT_FOUND, "yêu cầu bảo trì")
            );
        }

        try {
            MaintenanceRequestDAO.MaintenanceRequestView item =
                    maintenanceRequestDAO.findViewById(requestId);

            if (item == null) {
                return ServiceResult.fail(
                        String.format(
                                Messages.MSG08_NOT_FOUND,
                                "yêu cầu bảo trì"
                        )
                );
            }

            return ServiceResult.ok(item);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    public ServiceResult<Map<String, Integer>> getStatusCounts() {
        try {
            return ServiceResult.ok(
                    maintenanceRequestDAO.countByStatus()
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Retrieves a paginated list of facilities for the receptionist catalog view based on a single search keyword.
     * Obsolete category and status filter parameters from legacy bookmarks or URLs are safely ignored.
     *
     * @param keyword  optional search keyword matching facility name, category (English or Vietnamese), or location
     * @param page     1-based page number
     * @param pageSize number of items per page
     * @return ServiceResult containing paginated facilities
     */
    public ServiceResult<PageResult<Facility>> getFacilityCatalogPage(
            String keyword,
            int page,
            int pageSize
    ) {
        try {
            long totalItems = facilityDAO.countCatalog(keyword);
            List<Facility> facilities = facilityDAO.findCatalogPage(keyword, page, pageSize);

            return ServiceResult.ok(
                    new PageResult<>(
                            facilities,
                            page,
                            pageSize,
                            totalItems
                    )
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Legacy signature preserving backward compatibility while safely ignoring obsolete category/status parameters.
     */
    public ServiceResult<PageResult<Facility>> getFacilityCatalogPage(
            String keyword,
            String categoryValue,
            String statusValue,
            int page,
            int pageSize
    ) {
        // Obsolete category and status filters are intentionally ignored per BF-06 search simplification
        return getFacilityCatalogPage(keyword, page, pageSize);
    }

    private Employee findEmployeeForAccount(
            int accountId,
            Role expectedRole
    ) throws SQLException {

        Account account =
                accountDAO.findById(accountId);

        if (account == null) {
            return null;
        }

        Employee employee =
                employeeDAO.findByPersonId(
                        account.getPersonId()
                );

        if (employee == null
                || employee.getPosition() != expectedRole
                || employee.getStatus() != Employee.Status.WORKING) {
            return null;
        }

        return employee;
    }

}