package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.EquipmentItemDAO;
import com.gms.dao.FacilityDAO;
import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import com.gms.entity.MaintenanceRequest;
import com.gms.util.DBContext;
import com.gms.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service Layer: Encapsulates the core business logic and transaction boundaries
 * for Facility Maintenance Workflows (BF-06 / UC-28, UC-29).
 */
public class MaintenanceRequestService {

    private final MaintenanceRequestDAO maintenanceRequestDAO;
    private final FacilityDAO facilityDAO;
    private final EquipmentItemDAO equipmentItemDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public MaintenanceRequestService() {
        maintenanceRequestDAO = new MaintenanceRequestDAO();
        facilityDAO = new FacilityDAO();
        equipmentItemDAO = new EquipmentItemDAO();
        accountDAO = new AccountDAO();
        employeeDAO = new EmployeeDAO();
        auditLogService = new AuditLogService();
        notificationService = new NotificationService();
    }

    /**
     * Package-private constructor for dependency injection during unit testing.
     */
    MaintenanceRequestService(
            MaintenanceRequestDAO maintenanceRequestDAO,
            FacilityDAO facilityDAO,
            EquipmentItemDAO equipmentItemDAO,
            AccountDAO accountDAO,
            EmployeeDAO employeeDAO,
            AuditLogService auditLogService,
            NotificationService notificationService
    ) {
        this.maintenanceRequestDAO = maintenanceRequestDAO;
        this.facilityDAO = facilityDAO;
        this.equipmentItemDAO = equipmentItemDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    MaintenanceRequestService(
            MaintenanceRequestDAO maintenanceRequestDAO,
            FacilityDAO facilityDAO,
            AccountDAO accountDAO,
            EmployeeDAO employeeDAO,
            AuditLogService auditLogService,
            NotificationService notificationService
    ) {
        this(maintenanceRequestDAO, facilityDAO, new EquipmentItemDAO(), accountDAO, employeeDAO, auditLogService, notificationService);
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
     * Receptionist creates new maintenance requests for one or multiple individual equipment items in a single transaction.
     *
     * @param accountId          authenticated receptionist account ID
     * @param equipmentItemIds   list of targeted individual equipment item IDs
     * @param priorityValue      priority string ("Low", "Normal", "Urgent")
     * @param incidentTypeValue  type of incident
     * @param detectedByValue    detection source
     * @param description        detailed description of malfunction (10-500 characters)
     * @param warningTagPlaced   whether physical warning tag was placed on equipment
     * @param attachedImages     optional image file paths or references
     * @return ServiceResult containing generated request codes (e.g. ["BT-0001", "BT-0002"]) or validation errors
     */
    public ServiceResult<List<String>> createRequests(
            int accountId,
            List<Integer> equipmentItemIds,
            String priorityValue,
            String incidentTypeValue,
            String detectedByValue,
            String description,
            boolean warningTagPlaced,
            String attachedImages
    ) {

        // B1: Kiểm tra danh sách thiết bị
        if (equipmentItemIds == null || equipmentItemIds.isEmpty()) {
            return ServiceResult.failField(
                    "equipmentItemId",
                    "Vui lòng chọn ít nhất một mã máy cụ thể cần báo hỏng."
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

        MaintenanceRequest.IncidentType incidentType =
                MaintenanceRequest.IncidentType.fromDbValue(incidentTypeValue);
        if (incidentType == null) {
            incidentType = MaintenanceRequest.IncidentType.NOT_WORKING;
        }

        MaintenanceRequest.DetectedBy detectedBy =
                MaintenanceRequest.DetectedBy.fromDbValue(detectedByValue);
        if (detectedBy == null) {
            detectedBy = MaintenanceRequest.DetectedBy.RECEPTIONIST;
        }

        if (!ValidationUtil.isLengthValid(description, 10, 500)) {
            return ServiceResult.failField(
                    "description",
                    "Mô tả hỏng hóc phải từ 10 đến 500 ký tự."
            );
        }

        // Loại bỏ trùng lặp id
        List<Integer> uniqueItemIds = equipmentItemIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (uniqueItemIds.isEmpty()) {
            return ServiceResult.failField(
                    "equipmentItemId",
                    "Vui lòng chọn ít nhất một mã máy cụ thể hợp lệ."
            );
        }

        // B2: Mở Transaction boundary
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Xác minh tài khoản thuộc Lễ tân active
                Employee receptionist =
                        findEmployeeForAccount(accountId, Role.RECEPTIONIST);

                if (receptionist == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                List<String> createdCodes = new ArrayList<>();
                List<String> summaryItems = new ArrayList<>();

                for (int itemId : uniqueItemIds) {
                    EquipmentItem equipmentItem =
                            equipmentItemDAO.findById(connection, itemId);

                    if (equipmentItem == null
                            || equipmentItem.getStatus() == EquipmentItem.Status.RETIRED) {

                        connection.rollback();
                        return ServiceResult.failField(
                                "equipmentItemId",
                                "Mã máy không tồn tại hoặc đã ngưng hoạt động."
                        );
                    }

                    Facility facility =
                            facilityDAO.findById(connection, equipmentItem.getFacilityId());
                    String facilityName = facility != null ? facility.getFacilityName() : "Thiết bị";

                    // Tạo đơn bảo trì Open
                    MaintenanceRequest request = new MaintenanceRequest();
                    request.setRequestCode(
                            maintenanceRequestDAO.generateNextRequestCode(connection)
                    );
                    request.setEquipmentItemId(itemId);
                    request.setFacilityId(equipmentItem.getFacilityId());
                    request.setReportedByEmployeeId(receptionist.getEmployeeId());
                    request.setPriority(priority);
                    request.setIncidentType(incidentType);
                    request.setDetectedBy(detectedBy);
                    request.setWarningTagPlaced(warningTagPlaced);
                    request.setDescription(description.trim());
                    request.setAttachedImages(attachedImages);
                    request.setStatus(MaintenanceRequest.Status.OPEN);

                    int requestId = maintenanceRequestDAO.insert(connection, request);

                    // Ghi AuditLog
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
                            "Báo hỏng máy " + equipmentItem.getItemCode()
                    );

                    createdCodes.add(request.getRequestCode());
                    summaryItems.add(equipmentItem.getItemCode() + " (" + facilityName + ")");
                }

                // Gửi Notification cho Manager
                String notifTitle = createdCodes.size() == 1
                        ? "Bảo trì · Yêu cầu mới " + createdCodes.get(0)
                        : "Bảo trì · " + createdCodes.size() + " yêu cầu mới";

                String notifContent = createdCodes.size() == 1
                        ? createdCodes.get(0) + " · " + summaryItems.get(0) + " vừa được Lễ tân báo hỏng."
                        : "Lễ tân vừa báo hỏng " + createdCodes.size() + " máy: " + String.join(", ", summaryItems);

                notificationService.notifyRoleInTransaction(
                        connection,
                        Role.MANAGER,
                        notifTitle,
                        notifContent
                );

                connection.commit();
                return ServiceResult.ok(createdCodes);

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
     * Receptionist creates a new maintenance request for an individual equipment item.
     */
    public ServiceResult<String> createRequest(
            int accountId,
            int equipmentItemId,
            String priorityValue,
            String incidentTypeValue,
            String detectedByValue,
            String description,
            boolean warningTagPlaced,
            String attachedImages
    ) {
        if (equipmentItemId <= 0) {
            return ServiceResult.failField(
                    "equipmentItemId",
                    String.format(Messages.MSG21_SELECT_REQUIRED, "mã máy cụ thể")
            );
        }

        ServiceResult<List<String>> multiResult = createRequests(
                accountId,
                List.of(equipmentItemId),
                priorityValue,
                incidentTypeValue,
                detectedByValue,
                description,
                warningTagPlaced,
                attachedImages
        );

        if (multiResult.isSuccess()) {
            List<String> list = multiResult.getData();
            return ServiceResult.ok(list != null && !list.isEmpty() ? list.get(0) : "");
        }

        if (!multiResult.getFieldErrors().isEmpty()) {
            if (multiResult.getFieldErrors().size() == 1) {
                var entry = multiResult.getFieldErrors().entrySet().iterator().next();
                return ServiceResult.failField(entry.getKey(), entry.getValue());
            }
            return ServiceResult.failFields(multiResult.getFieldErrors());
        }
        return ServiceResult.fail(multiResult.getMessage());
    }

    public ServiceResult<String> createRequest(
            int accountId,
            int equipmentItemId,
            String priorityValue,
            String description,
            boolean warningTagPlaced
    ) {
        return createRequest(
                accountId,
                equipmentItemId,
                priorityValue,
                null,
                null,
                description,
                warningTagPlaced,
                null
        );
    }

    // ===== Duyệt yêu cầu =====

    /**
     * Manager approves an Open maintenance request, transitioning it to InProgress (BR-49).
     *
     * @param accountId           authenticated manager account ID
     * @param requestId           ID of the request to approve
     * @param setUnderMaintenance whether to mark the physical individual equipment item as UnderMaintenance
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

                // Step 4: Conditionally update physical individual EquipmentItem status if manager opted in
                if (setUnderMaintenance) {
                    EquipmentItem equipmentItem =
                            equipmentItemDAO.findById(connection, request.getEquipmentItemId());

                    if (equipmentItem != null
                            && equipmentItem.getStatus() != EquipmentItem.Status.UNDER_MAINTENANCE) {

                        EquipmentItem.Status oldStatus = equipmentItem.getStatus();
                        boolean itemUpdated =
                                equipmentItemDAO.updateStatus(
                                        connection,
                                        request.getEquipmentItemId(),
                                        EquipmentItem.Status.UNDER_MAINTENANCE
                                );

                        if (!itemUpdated) {
                            connection.rollback();
                            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                        }

                        // Write a dedicated AuditLog entry for the EquipmentItem status transition
                        auditLogService.logInTransaction(
                                connection,
                                accountId,
                                Role.MANAGER,
                                "Cập nhật trạng thái máy con",
                                "EquipmentItem",
                                request.getEquipmentItemId(),
                                "Status",
                                oldStatus.getDbValue(),
                                EquipmentItem.Status.UNDER_MAINTENANCE.getDbValue(),
                                "Chuyển máy " + equipmentItem.getItemCode() + " sang bảo trì khi duyệt yêu cầu " + request.getRequestCode()
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
     * <p><b>Equipment Item Recovery Logic:</b> Checks if any OTHER active InProgress requests exist
     * for the same individual machine. If none remain, the machine is automatically restored to {@code Available}.</p>
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

                // Step 4: Check if any other InProgress tickets exist for this individual machine
                boolean hasOtherInProgress =
                        maintenanceRequestDAO.hasInProgressRequest(
                                connection,
                                request.getEquipmentItemId()
                        );

                // If no other in-progress tickets remain for this individual item, restore it to Available
                if (!hasOtherInProgress) {
                    EquipmentItem equipmentItem =
                            equipmentItemDAO.findById(connection, request.getEquipmentItemId());

                    if (equipmentItem != null
                            && equipmentItem.getStatus() != EquipmentItem.Status.AVAILABLE
                            && equipmentItem.getStatus() != EquipmentItem.Status.RETIRED) {

                        EquipmentItem.Status oldStatus = equipmentItem.getStatus();
                        boolean itemUpdated =
                                equipmentItemDAO.updateStatus(
                                        connection,
                                        request.getEquipmentItemId(),
                                        EquipmentItem.Status.AVAILABLE
                                );

                        if (!itemUpdated) {
                            connection.rollback();
                            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                        }

                        // Write a dedicated AuditLog entry for the EquipmentItem status recovery
                        auditLogService.logInTransaction(
                                connection,
                                accountId,
                                Role.RECEPTIONIST,
                                "Khôi phục trạng thái máy con",
                                "EquipmentItem",
                                request.getEquipmentItemId(),
                                "Status",
                                oldStatus.getDbValue(),
                                EquipmentItem.Status.AVAILABLE.getDbValue(),
                                "Khôi phục máy " + equipmentItem.getItemCode() + " khả dụng sau khi đóng yêu cầu " + request.getRequestCode()
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

    public ServiceResult<List<EquipmentItem>> getSelectableEquipmentItems() {
        try {
            return ServiceResult.ok(
                    equipmentItemDAO.findAllSelectable()
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    public ServiceResult<List<EquipmentItem>> getEquipmentItemsByFacilityId(int facilityId) {
        try {
            return ServiceResult.ok(
                    equipmentItemDAO.findByFacilityId(facilityId)
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