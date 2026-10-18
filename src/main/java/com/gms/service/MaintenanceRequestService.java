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

    // ===== Tạo yêu cầu =====

    /**
     * Lễ tân tạo yêu cầu bảo trì mới.
     *
     * @param accountId        tài khoản lễ tân
     * @param facilityId       cơ sở vật chất bị lỗi
     * @param priorityValue    mức ưu tiên
     * @param description      mô tả lỗi
     * @param warningTagPlaced đã đặt cảnh báo hay chưa
     * @return mã yêu cầu vừa tạo
     */
    public ServiceResult<String> createRequest(
            int accountId,
            int facilityId,
            String priorityValue,
            String description,
            boolean warningTagPlaced
    ) {

        // B1: kiểm tra dữ liệu đầu vào
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

        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);

            try {
                // B2: xác định lễ tân đang thao tác
                Employee receptionist =
                        findEmployeeForAccount(accountId, Role.RECEPTIONIST);

                if (receptionist == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // B3: kiểm tra cơ sở vật chất còn tồn tại
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

                // B5: ghi AuditLog
                auditLogService.logInTransaction(
                        connection,
                        accountId,
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
     * Manager duyệt yêu cầu Open sang In Progress.
     *
     * @param accountId           tài khoản Manager
     * @param requestId           id yêu cầu
     * @param setUnderMaintenance có chuyển thiết bị sang bảo trì không
     * @return kết quả xử lý
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

        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);

            try {
                // B1: xác định Manager đang thao tác (BR-49)
                Employee manager =
                        findEmployeeForAccount(accountId, Role.MANAGER);

                if (manager == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // B2: khóa và kiểm tra trạng thái hiện tại
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

                // B3: chuyển Open sang InProgress (BR-49)
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

                // B4: cập nhật thiết bị nếu Manager chọn (CBR-101)
                if (setUnderMaintenance) {
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
                }

                // B5: ghi AuditLog
                auditLogService.logInTransaction(
                        connection,
                        accountId,
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
     * Manager từ chối yêu cầu Open.
     *
     * @param accountId    tài khoản Manager
     * @param requestId    id yêu cầu
     * @param rejectReason lý do từ chối
     * @return kết quả xử lý
     */
    public ServiceResult<Void> rejectRequest(
            int accountId,
            int requestId,
            String rejectReason
    ) {

        if (!ValidationUtil.isValidReason(rejectReason)) {
            return ServiceResult.failField(
                    "rejectReason",
                    "Lý do từ chối phải từ 5 đến 500 ký tự."
            );
        }

        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);

            try {
                // B1: xác định Manager đang thao tác (BR-49)
                Employee manager =
                        findEmployeeForAccount(accountId, Role.MANAGER);

                if (manager == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // B2: khóa yêu cầu cần từ chối
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

                // B3: chuyển Open sang Rejected (BR-49)
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

                // B4: ghi AuditLog
                auditLogService.logInTransaction(
                        connection,
                        accountId,
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
     * Lễ tân nghiệm thu và đóng yêu cầu In Progress.
     *
     * @param accountId      tài khoản lễ tân
     * @param requestId      id yêu cầu
     * @param acceptanceNote ghi chú nghiệm thu
     * @return kết quả xử lý
     */
    public ServiceResult<Void> closeRequest(
            int accountId,
            int requestId,
            String acceptanceNote
    ) {

        if (!ValidationUtil.isLengthValid(acceptanceNote, 5, 500)) {
            return ServiceResult.failField(
                    "acceptanceNote",
                    "Ghi chú nghiệm thu phải từ 5 đến 500 ký tự."
            );
        }

        try (Connection connection = DBContext.getConnection()) {
            connection.setAutoCommit(false);

            try {
                // B1: xác định lễ tân đang thao tác (BR-48)
                Employee receptionist =
                        findEmployeeForAccount(accountId, Role.RECEPTIONIST);

                if (receptionist == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // B2: khóa yêu cầu cần đóng
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

                // B3: đóng yêu cầu sau nghiệm thu (BR-48)
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

                // B4: khôi phục thiết bị nếu không còn yêu cầu đang xử lý (CBR-102)
                boolean hasOtherInProgress =
                        maintenanceRequestDAO.hasInProgressRequest(
                                connection,
                                request.getFacilityId()
                        );

                if (!hasOtherInProgress) {
                    facilityDAO.updateStatus(
                            connection,
                            request.getFacilityId(),
                            Facility.Status.AVAILABLE
                    );
                }

                // B5: ghi AuditLog
                auditLogService.logInTransaction(
                        connection,
                        accountId,
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