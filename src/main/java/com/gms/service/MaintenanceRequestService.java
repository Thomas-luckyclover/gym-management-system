package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.EquipmentItemDAO;
import com.gms.dao.FacilityDAO;
import com.gms.dao.MaintenanceRequestDAO;
import com.gms.dao.NotificationDAO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tầng Service xử lý toàn bộ nghiệp vụ và ranh giới transaction cho quy trình bảo trì thiết bị
 * (BF-06 / UC-28, UC-29).
 *
 * <p><b>Cách hoạt động:</b> Tiếp nhận yêu cầu từ Servlet, kiểm tra dữ liệu đầu vào và quyền nhân viên,
 * điều phối giao dịch CSDL giữa các DAO ({@code MaintenanceRequestDAO}, {@code EquipmentItemDAO},
 * {@code FacilityDAO}), đồng thời ghi {@code AuditLog} và gửi {@code Notification} trong cùng transaction.</p>
 */
public class MaintenanceRequestService {

    private static final int BATCH_NOTIFICATION_PREVIEW_LIMIT = 5;

    private final MaintenanceRequestDAO maintenanceRequestDAO;
    private final FacilityDAO facilityDAO;
    private final EquipmentItemDAO equipmentItemDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    /**
     * Khởi tạo Service mặc định cùng đầy đủ các DAO và Service phụ thuộc phục vụ chạy thực tế.
     */
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
     * Constructor phạm vi package cho phép tiêm DAO/Service giả lập khi viết unit test.
     *
     * <p><b>Cách hoạt động:</b> Gán trực tiếp từng dependency được truyền vào cho các trường thành viên
     * thay vì khởi tạo mặc định, giúp kiểm thử độc lập từng luồng nghiệp vụ không cần CSDL thật.</p>
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

    /**
     * Mở kết nối cơ sở dữ liệu phục vụ các thao tác cần transaction trong Service.
     *
     * <p><b>Cách hoạt động:</b> Mặc định gọi {@code DBContext.getConnection()}, được khai báo {@code protected}
     * để lớp test có thể override bằng connection mock hoặc wrapper.</p>
     *
     * @return kết nối {@link Connection} tới CSDL
     * @throws SQLException khi không mở được kết nối
     */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }

    // ===== Tạo yêu cầu =====

    /**
     * Lễ tân tạo yêu cầu bảo trì cho một hoặc nhiều cá thể thiết bị cùng lúc (mặc định không có ghi chú mức tạ/bộ phận).
     *
     * <p><b>Cách hoạt động:</b> Ủy quyền thẳng sang hàm {@link #createRequests(int, List, String, String, String, String, boolean, String, String)}
     * với tham số {@code affectedItemsNote = null}.</p>
     *
     * @param accountId         ID tài khoản Lễ tân đang đăng nhập
     * @param equipmentItemIds  danh sách ID cá thể thiết bị cần báo hỏng
     * @param priorityValue     mức độ ưu tiên ({@code Low}, {@code Normal}, {@code Urgent})
     * @param incidentTypeValue loại sự cố
     * @param detectedByValue   nguồn phát hiện sự cố
     * @param description       mô tả chi tiết sự cố (10-500 ký tự)
     * @param warningTagPlaced  đã gắn biển cảnh báo "Ngừng sử dụng" tại máy hay chưa
     * @param attachedImages    đường dẫn ảnh minh họa đính kèm (tùy chọn)
     * @return {@link ServiceResult} chứa danh sách mã phiếu vừa tạo hoặc thông báo lỗi validate
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
        return createRequests(accountId, equipmentItemIds, priorityValue, incidentTypeValue, detectedByValue, description, warningTagPlaced, attachedImages, null);
    }

    /**
     * Lễ tân tạo yêu cầu bảo trì cho một hoặc nhiều cá thể thiết bị trong cùng một transaction.
     *
     * <p><b>Cách hoạt động:</b> Validate toàn bộ đầu vào (danh sách ID máy, mức ưu tiên, mô tả 10-500 ký tự,
     * ghi chú bộ phận ảnh hưởng <= 100 ký tự), loại bỏ ID trùng, sau đó mở transaction: kiểm tra vai trò Lễ tân,
     * kiểm tra từng máy không bị thanh lý/đang bảo trì/đang có phiếu chờ duyệt, sinh mã phiếu {@code BT-xxxx},
     * insert bản ghi, ghi {@code AuditLog} và gửi một thông báo tổng hợp cho vai trò {@code MANAGER} trước khi commit.</p>
     *
     * @param accountId         ID tài khoản Lễ tân đang đăng nhập
     * @param equipmentItemIds  danh sách ID cá thể thiết bị được chọn
     * @param priorityValue     mức độ ưu tiên
     * @param incidentTypeValue loại sự cố
     * @param detectedByValue   nguồn phát hiện sự cố
     * @param description       mô tả sự cố (10-500 ký tự)
     * @param warningTagPlaced  cờ đã treo biển cảnh báo tại máy
     * @param attachedImages    danh sách đường dẫn ảnh đính kèm
     * @param affectedItemsNote ghi chú máy/mức tạ/bộ phận ảnh hưởng (tùy chọn, tối đa 100 ký tự)
     * @return {@link ServiceResult} chứa danh sách mã phiếu bảo trì mới tạo nếu thành công
     */
    public ServiceResult<List<String>> createRequests(
            int accountId,
            List<Integer> equipmentItemIds,
            String priorityValue,
            String incidentTypeValue,
            String detectedByValue,
            String description,
            boolean warningTagPlaced,
            String attachedImages,
            String affectedItemsNote
    ) {

        // B1: Kiểm tra danh sách thiết bị và dữ liệu đầu vào
        if (equipmentItemIds == null || equipmentItemIds.isEmpty()) {
            return ServiceResult.failField(
                    "equipmentItemId",
                    Messages.MSG232_MAINTENANCE_ITEM_REQUIRED
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
                    Messages.MSG233_MAINTENANCE_DESCRIPTION_LENGTH
            );
        }

        if (affectedItemsNote != null && affectedItemsNote.trim().length() > 100) {
            return ServiceResult.failField(
                    "affectedItemsNote",
                    String.format(Messages.MSG314_NOTE_MAX_LENGTH, 100)
            );
        }

        // Loại bỏ ID trùng lặp hoặc không hợp lệ
        List<Integer> uniqueItemIds = equipmentItemIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (uniqueItemIds.isEmpty()) {
            return ServiceResult.failField(
                    "equipmentItemId",
                    Messages.MSG234_MAINTENANCE_ITEM_INVALID
            );
        }

        // B2: Mở ranh giới transaction để đảm bảo tất cả phiếu, log và thông báo lưu đồng bộ
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Xác minh tài khoản thuộc Lễ tân đang làm việc
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
                                Messages.MSG235_MAINTENANCE_ITEM_INACTIVE
                        );
                    }

                    // Kiểm tra quy tắc BR-105 & GBR-13: Máy đang bảo trì hoặc đã có yêu cầu Open/InProgress thì không được báo hỏng thêm
                    if (equipmentItem.getStatus() == EquipmentItem.Status.UNDER_MAINTENANCE
                            || maintenanceRequestDAO.hasActiveRequest(connection, itemId)) {

                        connection.rollback();
                        return ServiceResult.failField(
                                "equipmentItemId",
                                "Thiết bị " + equipmentItem.getItemCode() + " đang trong quá trình bảo trì hoặc đã có yêu cầu chờ duyệt."
                        );
                    }

                    Facility facility =
                            facilityDAO.findById(connection, equipmentItem.getFacilityId());
                    String facilityName = facility != null ? facility.getFacilityName() : "Thiết bị";

                    // Khởi tạo và lưu đơn bảo trì ở trạng thái Open
                    MaintenanceRequest request = new MaintenanceRequest();
                    request.setRequestCode(
                            maintenanceRequestDAO.generateNextRequestCode(connection)
                    );
                    request.setEquipmentItemId(itemId);
                    request.setFacilityId(equipmentItem.getFacilityId());
                    request.setReportedByEmployeeId(receptionist.getEmployeeId());
                    request.setPriority(priority);
                    request.setIncidentType(incidentType);
                    request.setAffectedItemsNote(affectedItemsNote != null && !affectedItemsNote.isBlank() ? affectedItemsNote.trim() : null);
                    request.setWarningTagPlaced(warningTagPlaced);
                    request.setDescription(description.trim());
                    request.setAttachedImages(attachedImages);
                    request.setStatus(MaintenanceRequest.Status.OPEN);

                    int requestId = maintenanceRequestDAO.insert(connection, request);

                    // Ghi nhật ký thao tác (AuditLog)
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

                // Gửi thông báo (Notification) cho Quản lý về phiếu bảo trì mới (giữ gọn <= 500 ký tự khi báo nhiều máy)
                String notifTitle = createdCodes.size() == 1
                        ? NotificationDAO.safeTruncate(
                                "Bảo trì · Yêu cầu mới " + createdCodes.get(0),
                                NotificationDAO.TITLE_MAX_LENGTH
                        )
                        : NotificationDAO.safeTruncate(
                                "Bảo trì · " + createdCodes.size() + " yêu cầu mới",
                                NotificationDAO.TITLE_MAX_LENGTH
                        );

                String notifContent = createdCodes.size() == 1
                        ? NotificationDAO.safeTruncate(
                                createdCodes.get(0) + " · " + summaryItems.get(0) + " vừa được Lễ tân báo hỏng.",
                                NotificationDAO.CONTENT_MAX_LENGTH
                        )
                        : buildBatchReportNotificationContent(createdCodes.size(), summaryItems);

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
     * Lễ tân tạo một yêu cầu bảo trì đơn lẻ cho một cá thể thiết bị (không truyền ghi chú bộ phận).
     *
     * <p><b>Cách hoạt động:</b> Gọi chuyển tiếp sang phiên bản 9 tham số với {@code affectedItemsNote = null}.</p>
     *
     * @return {@link ServiceResult} chứa mã phiếu bảo trì duy nhất nếu thành công
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
        return createRequest(accountId, equipmentItemId, priorityValue, incidentTypeValue, detectedByValue, description, warningTagPlaced, attachedImages, null);
    }

    /**
     * Lễ tân tạo một yêu cầu bảo trì cho đúng một cá thể thiết bị cụ thể.
     *
     * <p><b>Cách hoạt động:</b> Kiểm tra {@code equipmentItemId > 0}, bọc thành danh sách 1 phần tử để gọi
     * {@link #createRequests(int, List, String, String, String, String, boolean, String, String)} rồi trích xuất
     * mã phiếu đầu tiên hoặc ánh xạ nguyên vẹn lỗi field/lỗi chung trả về cho caller.</p>
     *
     * @return {@link ServiceResult} chứa chuỗi mã phiếu vừa tạo hoặc thông báo lỗi
     */
    public ServiceResult<String> createRequest(
            int accountId,
            int equipmentItemId,
            String priorityValue,
            String incidentTypeValue,
            String detectedByValue,
            String description,
            boolean warningTagPlaced,
            String attachedImages,
            String affectedItemsNote
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
                attachedImages,
                affectedItemsNote
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

    /**
     * Phiên bản rút gọn tạo một yêu cầu bảo trì đơn lẻ (chỉ truyền mức ưu tiên, mô tả và biển cảnh báo).
     *
     * <p><b>Cách hoạt động:</b> Ủy quyền sang {@link #createRequest(int, int, String, String, String, String, boolean, String)}
     * với các trường tùy chọn để giá trị mặc định ({@code null}).</p>
     *
     * @return {@link ServiceResult} chứa mã phiếu bảo trì vừa tạo
     */
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
     * Quản lý duyệt một yêu cầu bảo trì đang {@code Open} chuyển sang {@code InProgress} (phiên bản không ghi chú).
     *
     * <p><b>Cách hoạt động:</b> Gọi tiếp sang {@link #approveRequest(int, int, boolean, String)} với {@code managerNote = null}.</p>
     *
     * @param accountId           ID tài khoản Quản lý đang đăng nhập
     * @param requestId           ID yêu cầu cần duyệt
     * @param setUnderMaintenance có chuyển cá thể thiết bị sang {@code UnderMaintenance} hay không
     * @return {@link ServiceResult} thông báo kết quả duyệt
     */
    public ServiceResult<Void> approveRequest(
            int accountId,
            int requestId,
            boolean setUnderMaintenance
    ) {
        return approveRequest(accountId, requestId, setUnderMaintenance, null);
    }

    /**
     * Quản lý duyệt một yêu cầu bảo trì từ {@code Open} sang {@code InProgress} trong transaction (BR-49).
     *
     * <p><b>Cách hoạt động:</b> Kiểm tra ID và độ dài {@code managerNote} (<= 500 ký tự); trong transaction:
     * xác minh nhân viên thuộc vai trò {@code MANAGER}, khóa dòng yêu cầu bằng {@code findByIdForUpdate} và kiểm tra
     * đang {@code Open}, gọi DAO cập nhật trạng thái sang {@code InProgress}, nếu chọn {@code setUnderMaintenance}
     * thì đổi trạng thái cá thể máy sang {@code UnderMaintenance} kèm audit log riêng, ghi audit log phiếu và
     * gửi thông báo về cho Lễ tân đã báo hỏng.</p>
     *
     * @param accountId           ID tài khoản Quản lý đang đăng nhập
     * @param requestId           ID phiếu bảo trì cần duyệt
     * @param setUnderMaintenance cờ đánh dấu đưa máy vào trạng thái bảo trì
     * @param managerNote         ghi chú xử lý của Quản lý (tối đa 500 ký tự, có thể {@code null})
     * @return {@link ServiceResult} thành công hoặc thông báo lỗi nghiệp vụ
     */
    public ServiceResult<Void> approveRequest(
            int accountId,
            int requestId,
            boolean setUnderMaintenance,
            String managerNote
    ) {

        if (requestId <= 0) {
            return ServiceResult.fail(
                    String.format(Messages.MSG08_NOT_FOUND, "yêu cầu bảo trì")
            );
        }

        if (managerNote != null && managerNote.trim().length() > 500) {
            return ServiceResult.failField(
                    "managerNote",
                    String.format(Messages.MSG314_NOTE_MAX_LENGTH, 500)
            );
        }

        // Mở ranh giới transaction
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Bước 1: Xác minh quyền Quản lý (BR-49)
                Employee manager =
                        findEmployeeForAccount(accountId, Role.MANAGER);

                if (manager == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Bước 2: Khóa bản ghi và kiểm tra trạng thái Open để chống duyệt trùng
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
                            Messages.MSG236_MAINTENANCE_APPROVE_ONLY_PENDING
                    );
                }

                // Bước 3: Chuyển trạng thái phiếu từ Open sang InProgress trong DAO
                boolean updated =
                        maintenanceRequestDAO.approve(
                                connection,
                                requestId,
                                manager.getEmployeeId(),
                                setUnderMaintenance,
                                managerNote
                        );

                if (!updated) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Bước 4: Cập nhật trạng thái cá thể máy sang UnderMaintenance nếu Quản lý tích chọn
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

                        // Ghi AuditLog riêng cho lần chuyển trạng thái máy cá thể
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

                // Bước 5: Ghi AuditLog cho phiếu bảo trì dưới vai trò MANAGER
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

                String deviceDetail = buildDeviceDetail(connection, request.getEquipmentItemId(), true);
                String baseContent = request.getRequestCode()
                        + " · "
                        + deviceDetail
                        + ": Quản lý đã duyệt yêu cầu, chuyển sang trạng thái Đang xử lý.";
                String notifContent = buildNotificationWithOptionalNote(
                        baseContent,
                        "\n\nGhi chú: ",
                        managerNote
                );

                notificationService.notifyEmployeeInTransaction(
                        connection,
                        request.getReportedByEmployeeId(),
                        "Bảo trì · Đã được duyệt",
                        notifContent
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
     * Quản lý duyệt hàng loạt nhiều yêu cầu bảo trì đang {@code Open} trong cùng một transaction.
     *
     * <p><b>Cách hoạt động:</b> Lọc danh sách ID hợp lệ, kiểm tra độ dài ghi chú (<= 500 ký tự), mở transaction
     * và duyệt lần lượt từng phiếu còn {@code Open}: chuyển sang {@code InProgress}, cập nhật trạng thái máy nếu
     * {@code setUnderMaintenance = true}, ghi audit log và thông báo người báo hỏng; nếu không có phiếu nào
     * được duyệt thì rollback và báo lỗi.</p>
     *
     * @param accountId           ID tài khoản Quản lý
     * @param requestIds          danh sách ID các phiếu cần duyệt hàng loạt
     * @param setUnderMaintenance cờ đánh dấu chuyển máy sang trạng thái bảo trì
     * @param managerNote         ghi chú chung áp dụng cho các phiếu được duyệt
     * @return {@link ServiceResult} chứa số lượng phiếu duyệt thành công
     */
    public ServiceResult<Integer> approveRequestsBatch(
            int accountId,
            List<Integer> requestIds,
            boolean setUnderMaintenance,
            String managerNote
    ) {
        if (managerNote != null && managerNote.trim().length() > 500) {
            return ServiceResult.fail(String.format(Messages.MSG314_NOTE_MAX_LENGTH, 500));
        }

        if (requestIds == null || requestIds.isEmpty()) {
            return ServiceResult.fail("Vui lòng chọn ít nhất một yêu cầu bảo trì để duyệt.");
        }

        List<Integer> validIds = requestIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (validIds.isEmpty()) {
            return ServiceResult.fail("Vui lòng chọn ít nhất một yêu cầu bảo trì để duyệt.");
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                Employee manager = findEmployeeForAccount(accountId, Role.MANAGER);
                if (manager == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                int approvedCount = 0;
                Map<Integer, List<BatchNotificationEntry>> approvedByReporter = new LinkedHashMap<>();

                for (int reqId : validIds) {
                    MaintenanceRequest request = maintenanceRequestDAO.findByIdForUpdate(connection, reqId);
                    if (request == null || !request.isOpen()) {
                        continue;
                    }

                    boolean updated = maintenanceRequestDAO.approve(
                            connection,
                            reqId,
                            manager.getEmployeeId(),
                            setUnderMaintenance,
                            managerNote
                    );

                    if (!updated) {
                        continue;
                    }

                    if (setUnderMaintenance) {
                        EquipmentItem equipmentItem =
                                equipmentItemDAO.findById(connection, request.getEquipmentItemId());

                        if (equipmentItem != null
                                && equipmentItem.getStatus() != EquipmentItem.Status.UNDER_MAINTENANCE) {

                            EquipmentItem.Status oldStatus = equipmentItem.getStatus();
                            boolean itemUpdated = equipmentItemDAO.updateStatus(
                                    connection,
                                    request.getEquipmentItemId(),
                                    EquipmentItem.Status.UNDER_MAINTENANCE
                            );

                            if (itemUpdated) {
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
                                        "Chuyển máy " + equipmentItem.getItemCode() + " sang bảo trì khi duyệt hàng loạt yêu cầu " + request.getRequestCode()
                                );
                            }
                        }
                    }

                    auditLogService.logInTransaction(
                            connection,
                            accountId,
                            Role.MANAGER,
                            "Duyệt yêu cầu bảo trì",
                            "MaintenanceRequest",
                            reqId,
                            "Status",
                            MaintenanceRequest.Status.OPEN.getDbValue(),
                            MaintenanceRequest.Status.IN_PROGRESS.getDbValue(),
                            managerNote
                    );

                    String deviceDetail = buildDeviceDetail(connection, request.getEquipmentItemId(), true);
                    approvedByReporter
                            .computeIfAbsent(request.getReportedByEmployeeId(), k -> new ArrayList<>())
                            .add(new BatchNotificationEntry(request.getRequestCode(), deviceDetail));

                    approvedCount++;
                }

                if (approvedCount == 0) {
                    connection.rollback();
                    return ServiceResult.fail("Không có yêu cầu bảo trì nào ở trạng thái Chờ duyệt được chọn.");
                }

                for (Map.Entry<Integer, List<BatchNotificationEntry>> group : approvedByReporter.entrySet()) {
                    List<BatchNotificationEntry> entries = group.getValue();
                    String notifTitle;
                    String baseContent;

                    if (entries.size() == 1) {
                        BatchNotificationEntry single = entries.get(0);
                        notifTitle = "Bảo trì · Đã được duyệt";
                        baseContent = single.requestCode
                                + " · "
                                + single.deviceDetail
                                + ": Quản lý đã duyệt yêu cầu, chuyển sang trạng thái Đang xử lý.";
                    } else {
                        notifTitle = NotificationDAO.safeTruncate(
                                "Bảo trì · " + entries.size() + " yêu cầu đã duyệt",
                                NotificationDAO.TITLE_MAX_LENGTH
                        );
                        baseContent = buildBatchStatusNotificationContent(
                                "Quản lý đã duyệt ",
                                ", chuyển sang trạng thái Đang xử lý.",
                                entries
                        );
                    }

                    notificationService.notifyEmployeeInTransaction(
                            connection,
                            group.getKey(),
                            notifTitle,
                            buildNotificationWithOptionalNote(baseContent, "\n\nGhi chú: ", managerNote)
                    );
                }

                connection.commit();
                return ServiceResult.ok(approvedCount);

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
     * Quản lý từ chối một yêu cầu bảo trì đang {@code Open} kèm lý do bắt buộc (BR-49).
     *
     * <p><b>Cách hoạt động:</b> Validate độ dài lý do từ chối trước khi mở kết nối; trong transaction,
     * xác minh vai trò {@code MANAGER}, khóa phiếu kiểm tra đang {@code Open}, gọi {@code maintenanceRequestDAO.reject},
     * ghi audit log lý do từ chối và gửi thông báo lại cho Lễ tân đã lập phiếu.</p>
     *
     * @param accountId    ID tài khoản Quản lý
     * @param requestId    ID yêu cầu bảo trì cần từ chối
     * @param rejectReason lý do từ chối
     * @return {@link ServiceResult} kết quả xử lý
     */
    public ServiceResult<Void> rejectRequest(
            int accountId,
            int requestId,
            String rejectReason
    ) {

        // Kiểm tra độ dài lý do từ chối trước khi mở kết nối CSDL
        if (!ValidationUtil.isValidReason(rejectReason)) {
            return ServiceResult.failField(
                    "rejectReason",
                    Messages.MSG212_REJECT_REASON_LENGTH
            );
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Bước 1: Xác minh quyền Quản lý (BR-49)
                Employee manager =
                        findEmployeeForAccount(accountId, Role.MANAGER);

                if (manager == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Bước 2: Khóa bản ghi và kiểm tra phiếu đang ở trạng thái Open
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
                            Messages.MSG238_MAINTENANCE_REJECT_ONLY_PENDING
                    );
                }

                // Bước 3: Chuyển trạng thái phiếu sang Rejected trong DAO
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

                // Bước 4: Ghi AuditLog từ chối và thông báo cho người báo hỏng
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

                String rejectDeviceDetail = buildDeviceDetail(connection, request.getEquipmentItemId(), false);
                String rejectBaseContent = request.getRequestCode()
                        + " · "
                        + rejectDeviceDetail
                        + ": Quản lý đã từ chối yêu cầu.";

                notificationService.notifyEmployeeInTransaction(
                        connection,
                        request.getReportedByEmployeeId(),
                        "Bảo trì · Bị từ chối",
                        buildNotificationWithOptionalNote(rejectBaseContent, "\n\nLý do: ", rejectReason)
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
     * Lễ tân nghiệm thu thực tế và đóng một yêu cầu bảo trì đang {@code InProgress} (BR-48).
     *
     * <p><b>Cách hoạt động:</b> Validate ghi chú nghiệm thu (5-500 ký tự); trong transaction: xác minh quyền
     * {@code RECEPTIONIST}, khóa phiếu và kiểm tra trạng thái {@code InProgress}, chuyển sang {@code Closed},
     * sau đó kiểm tra nếu thiết bị cá thể không còn phiếu {@code InProgress} nào khác và chưa bị thanh lý thì
     * khôi phục trạng thái máy về {@code Available} (kèm audit log), cuối cùng ghi log đóng phiếu và báo cho Quản lý đã duyệt.</p>
     *
     * @param accountId      ID tài khoản Lễ tân
     * @param requestId      ID phiếu bảo trì cần đóng
     * @param acceptanceNote ghi chú nghiệm thu (5-500 ký tự)
     * @return {@link ServiceResult} kết quả xử lý
     */
    public ServiceResult<Void> closeRequest(
            int accountId,
            int requestId,
            String acceptanceNote
    ) {

        // Kiểm tra độ dài ghi chú nghiệm thu trước khi mở kết nối CSDL
        if (!ValidationUtil.isLengthValid(acceptanceNote, 5, 500)) {
            return ServiceResult.failField(
                    "acceptanceNote",
                    Messages.MSG239_MAINTENANCE_CLOSE_NOTE_LENGTH
            );
        }

        // Mở ranh giới transaction
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                // Bước 1: Xác minh quyền Lễ tân (BR-48)
                Employee receptionist =
                        findEmployeeForAccount(accountId, Role.RECEPTIONIST);

                if (receptionist == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                // Bước 2: Khóa phiếu và xác nhận đang ở trạng thái InProgress
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
                            Messages.MSG240_MAINTENANCE_CLOSE_ONLY_IN_PROGRESS
                    );
                }

                // Bước 3: Chuyển trạng thái phiếu sang Closed trong DAO
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

                // Bước 4: Kiểm tra cá thể máy có còn phiếu InProgress khác không để khôi phục về Available
                boolean hasOtherInProgress =
                        maintenanceRequestDAO.hasInProgressRequest(
                                connection,
                                request.getEquipmentItemId()
                        );

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

                        // Ghi AuditLog khôi phục trạng thái máy cá thể
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

                // Bước 5: Ghi AuditLog đóng phiếu và gửi thông báo cho Quản lý đã duyệt
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
                    String closeDeviceDetail = buildDeviceDetail(connection, request.getEquipmentItemId(), false);
                    String closeBaseContent = request.getRequestCode()
                            + " · "
                            + closeDeviceDetail
                            + ": Yêu cầu đã được Lễ tân nghiệm thu và đóng.";

                    notificationService.notifyEmployeeInTransaction(
                            connection,
                            request.getReviewedByEmployeeId(),
                            "Bảo trì · Đã hoàn tất",
                            buildNotificationWithOptionalNote(closeBaseContent, "\n\nNghiệm thu: ", acceptanceNote)
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

    /**
     * Lễ tân nghiệm thu và đóng hàng loạt nhiều phiếu bảo trì đang {@code InProgress} trong một transaction.
     *
     * <p><b>Cách hoạt động:</b> Validate ghi chú nghiệm thu chung (5-500 ký tự) và danh sách ID chọn, mở
     * transaction và lặp từng phiếu còn {@code InProgress}: đóng phiếu, khôi phục máy về {@code Available} nếu
     * không còn phiếu đang xử lý nào khác, ghi audit log và thông báo Quản lý; nếu đóng được 0 phiếu thì rollback.</p>
     *
     * @param accountId      ID tài khoản Lễ tân
     * @param requestIds     danh sách ID các phiếu cần nghiệm thu hàng loạt
     * @param acceptanceNote ghi chú nghiệm thu áp dụng chung (5-500 ký tự)
     * @return {@link ServiceResult} chứa số lượng phiếu đã đóng thành công
     */
    public ServiceResult<Integer> closeRequestsBatch(
            int accountId,
            List<Integer> requestIds,
            String acceptanceNote
    ) {
        if (!ValidationUtil.isLengthValid(acceptanceNote, 5, 500)) {
            return ServiceResult.failField(
                    "acceptanceNote",
                    Messages.MSG239_MAINTENANCE_CLOSE_NOTE_LENGTH
            );
        }

        if (requestIds == null || requestIds.isEmpty()) {
            return ServiceResult.fail("Vui lòng chọn ít nhất một yêu cầu bảo trì để nghiệm thu.");
        }

        List<Integer> validIds = requestIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (validIds.isEmpty()) {
            return ServiceResult.fail("Vui lòng chọn ít nhất một yêu cầu bảo trì để nghiệm thu.");
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                Employee receptionist = findEmployeeForAccount(accountId, Role.RECEPTIONIST);
                if (receptionist == null) {
                    connection.rollback();
                    return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }

                int closedCount = 0;
                Map<Integer, List<BatchNotificationEntry>> closedByReviewer = new LinkedHashMap<>();

                for (int reqId : validIds) {
                    MaintenanceRequest request = maintenanceRequestDAO.findByIdForUpdate(connection, reqId);
                    if (request == null || !request.isInProgress()) {
                        continue;
                    }

                    boolean updated = maintenanceRequestDAO.close(
                            connection,
                            reqId,
                            receptionist.getEmployeeId(),
                            acceptanceNote.trim()
                    );

                    if (!updated) {
                        continue;
                    }

                    boolean hasOtherInProgress = maintenanceRequestDAO.hasInProgressRequest(
                            connection,
                            request.getEquipmentItemId()
                    );

                    if (!hasOtherInProgress) {
                        EquipmentItem equipmentItem =
                                equipmentItemDAO.findById(connection, request.getEquipmentItemId());

                        if (equipmentItem != null
                                && equipmentItem.getStatus() != EquipmentItem.Status.AVAILABLE
                                && equipmentItem.getStatus() != EquipmentItem.Status.RETIRED) {

                            EquipmentItem.Status oldStatus = equipmentItem.getStatus();
                            boolean itemUpdated = equipmentItemDAO.updateStatus(
                                    connection,
                                    request.getEquipmentItemId(),
                                    EquipmentItem.Status.AVAILABLE
                            );

                            if (itemUpdated) {
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
                                        "Khôi phục máy " + equipmentItem.getItemCode() + " khả dụng sau khi nghiệm thu hàng loạt yêu cầu " + request.getRequestCode()
                                );
                            }
                        }
                    }

                    auditLogService.logInTransaction(
                            connection,
                            accountId,
                            Role.RECEPTIONIST,
                            "Đóng yêu cầu bảo trì",
                            "MaintenanceRequest",
                            reqId,
                            "Status",
                            MaintenanceRequest.Status.IN_PROGRESS.getDbValue(),
                            MaintenanceRequest.Status.CLOSED.getDbValue(),
                            acceptanceNote.trim()
                    );

                    if (request.getReviewedByEmployeeId() != null) {
                        String closeDeviceDetail = buildDeviceDetail(connection, request.getEquipmentItemId(), false);
                        closedByReviewer
                                .computeIfAbsent(request.getReviewedByEmployeeId(), k -> new ArrayList<>())
                                .add(new BatchNotificationEntry(request.getRequestCode(), closeDeviceDetail));
                    }

                    closedCount++;
                }

                if (closedCount == 0) {
                    connection.rollback();
                    return ServiceResult.fail("Không có yêu cầu bảo trì nào ở trạng thái Đang xử lý được chọn.");
                }

                for (Map.Entry<Integer, List<BatchNotificationEntry>> group : closedByReviewer.entrySet()) {
                    List<BatchNotificationEntry> entries = group.getValue();
                    String notifTitle;
                    String baseContent;

                    if (entries.size() == 1) {
                        BatchNotificationEntry single = entries.get(0);
                        notifTitle = "Bảo trì · Đã hoàn tất";
                        baseContent = single.requestCode
                                + " · "
                                + single.deviceDetail
                                + ": Yêu cầu đã được Lễ tân nghiệm thu và đóng.";
                    } else {
                        notifTitle = NotificationDAO.safeTruncate(
                                "Bảo trì · " + entries.size() + " yêu cầu đã hoàn tất",
                                NotificationDAO.TITLE_MAX_LENGTH
                        );
                        baseContent = buildBatchStatusNotificationContent(
                                "Lễ tân đã nghiệm thu và đóng ",
                                ".",
                                entries
                        );
                    }

                    notificationService.notifyEmployeeInTransaction(
                            connection,
                            group.getKey(),
                            notifTitle,
                            buildNotificationWithOptionalNote(baseContent, "\n\nNghiệm thu: ", acceptanceNote)
                    );
                }

                connection.commit();
                return ServiceResult.ok(closedCount);

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

    /**
     * Lấy danh sách toàn bộ cá thể thiết bị có thể chọn khi Lễ tân lập phiếu báo hỏng.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code equipmentItemDAO.findAllSelectable()} để lấy các máy chưa bị thanh lý
     * thuộc dòng thiết bị đang hoạt động, kèm cờ đang có phiếu bảo trì hay không.</p>
     *
     * @return {@link ServiceResult} chứa danh sách {@link EquipmentItem} hợp lệ
     */
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

    /**
     * Lấy danh sách yêu cầu bảo trì có phân trang và bộ lọc đầy đủ cho màn hình Lễ tân.
     *
     * <p><b>Cách hoạt động:</b> Xác minh tài khoản là Lễ tân đang làm việc, quy đổi chuỗi {@code statusValue} và
     * {@code priorityValue} sang enum, gọi {@code countForReceptionist} + {@code findForReceptionist} của DAO
     * rồi đóng gói thành {@link PageResult}.</p>
     *
     * @param accountId     ID tài khoản Lễ tân
     * @param keyword       từ khóa tìm kiếm
     * @param statusValue   giá trị trạng thái cần lọc
     * @param priorityValue giá trị mức ưu tiên cần lọc
     * @param reportedDate  ngày báo cáo cần lọc
     * @param page          số trang hiện tại
     * @param pageSize      kích thước trang
     * @return {@link ServiceResult} chứa {@link PageResult} các phiếu bảo trì
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

    /**
     * Lấy danh sách yêu cầu bảo trì có phân trang và bộ lọc đầy đủ cho màn hình Quản lý duyệt.
     *
     * <p><b>Cách hoạt động:</b> Chuyển chuỗi {@code statusValue}/{@code priorityValue} sang enum, gọi
     * {@code countAll} và {@code findAll} của DAO với từ khóa, trạng thái, mức ưu tiên, ngày báo cáo rồi
     * gói kết quả vào {@link PageResult}.</p>
     *
     * @param keyword       từ khóa tìm kiếm
     * @param statusValue   trạng thái cần lọc
     * @param priorityValue mức ưu tiên cần lọc
     * @param reportedDate  ngày báo cáo cần lọc
     * @param page          số trang hiện tại
     * @param pageSize      kích thước trang
     * @return {@link ServiceResult} chứa {@link PageResult} danh sách phiếu
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

    /**
     * Lấy thông tin chi tiết đầy đủ của một yêu cầu bảo trì theo ID.
     *
     * <p><b>Cách hoạt động:</b> Kiểm tra {@code requestId > 0}, gọi {@code maintenanceRequestDAO.findViewById}
     * và trả về lỗi không tìm thấy nếu bản ghi trả về {@code null}.</p>
     *
     * @param requestId ID yêu cầu bảo trì
     * @return {@link ServiceResult} chứa {@link MaintenanceRequestDAO.MaintenanceRequestView}
     */
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

    /**
     * Lấy thống kê số lượng yêu cầu bảo trì theo từng trạng thái.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code maintenanceRequestDAO.countByStatus()} để lấy Map số đếm
     * từng trạng thái ({@code Open}, {@code InProgress}, {@code Rejected}, {@code Closed}) phục vụ thẻ KPI.</p>
     *
     * @return {@link ServiceResult} chứa Map thống kê theo trạng thái
     */
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
     * Lấy danh sách dòng thiết bị phân trang cho màn hình tra cứu của Lễ tân (lọc theo từ khóa).
     *
     * <p><b>Cách hoạt động:</b> Ủy quyền sang {@link #getFacilityCatalogPage(String, String, int, int)}
     * với {@code location = null}.</p>
     *
     * @param keyword  từ khóa tìm kiếm theo tên, loại hoặc khu vực đặt thiết bị
     * @param page     số trang hiện tại (bắt đầu từ 1)
     * @param pageSize số bản ghi mỗi trang
     * @return {@link ServiceResult} chứa {@link PageResult} dòng thiết bị
     */
    public ServiceResult<PageResult<Facility>> getFacilityCatalogPage(
            String keyword,
            int page,
            int pageSize
    ) {
        return getFacilityCatalogPage(keyword, null, page, pageSize);
    }

    /**
     * Lấy danh sách dòng thiết bị phân trang cho màn hình Lễ tân theo từ khóa và khu vực đặt máy.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code facilityDAO.countCatalog} và {@code facilityDAO.findCatalogPage}
     * (đã tính sẵn thống kê số máy khả dụng / đang bảo trì) rồi đóng gói vào {@link PageResult}.</p>
     *
     * @param keyword  từ khóa tìm kiếm (tùy chọn)
     * @param location tên khu vực/vị trí cần lọc (tùy chọn)
     * @param page     số trang hiện tại
     * @param pageSize kích thước trang
     * @return {@link ServiceResult} chứa danh sách phân trang {@link Facility}
     */
    public ServiceResult<PageResult<Facility>> getFacilityCatalogPage(
            String keyword,
            String location,
            int page,
            int pageSize
    ) {
        try {
            long totalItems = facilityDAO.countCatalog(keyword, location);
            List<Facility> facilities = facilityDAO.findCatalogPage(keyword, location, page, pageSize);

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
     * Chữ ký tương thích ngược cho màn hình danh mục Lễ tân (bỏ qua tham số phân loại và trạng thái cũ).
     *
     * <p><b>Cách hoạt động:</b> Bỏ qua {@code categoryValue} và {@code statusValue} không còn dùng trong bộ lọc mới,
     * chỉ giữ {@code keyword}, {@code page}, {@code pageSize} chuyển sang {@link #getFacilityCatalogPage(String, int, int)}.</p>
     */
    public ServiceResult<PageResult<Facility>> getFacilityCatalogPage(
            String keyword,
            String categoryValue,
            String statusValue,
            int page,
            int pageSize
    ) {
        // Bỏ qua bộ lọc category/status cũ do màn hình Lễ tân đã đơn giản hóa thanh tìm kiếm
        return getFacilityCatalogPage(keyword, page, pageSize);
    }

    /**
     * Tra cứu phiếu bảo trì đã đóng ({@code Closed}) gần nhất của cùng cá thể thiết bị.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code maintenanceRequestDAO.findLastClosedRequest} loại trừ ID phiếu
     * hiện tại để hiển thị thông tin "Lần hỏng trước của máy này" trên màn hình Quản lý duyệt.</p>
     *
     * @param equipmentItemId  ID cá thể thiết bị
     * @param currentRequestId ID phiếu đang xem để loại trừ
     * @return {@link ServiceResult} chứa phiếu đã đóng gần nhất (hoặc {@code null} nếu chưa có)
     */
    public ServiceResult<MaintenanceRequestDAO.MaintenanceRequestView> getLastClosedRequest(int equipmentItemId, int currentRequestId) {
        try {
            MaintenanceRequestDAO.MaintenanceRequestView view =
                    maintenanceRequestDAO.findLastClosedRequest(equipmentItemId, currentRequestId);
            return ServiceResult.ok(view);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy thông tin chi tiết một dòng thiết bị theo {@code FacilityId} kèm thống kê số lượng khả dụng.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code facilityDAO.findById(facilityId)}, nếu không tồn tại thì trả về
     * thông báo không tìm thấy thiết bị.</p>
     *
     * @param facilityId ID dòng thiết bị
     * @return {@link ServiceResult} chứa đối tượng {@link Facility} hoặc lỗi
     */
    public ServiceResult<Facility> getFacilityById(int facilityId) {
        try {
            Facility facility = facilityDAO.findById(facilityId);
            if (facility == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "thiết bị"));
            }
            return ServiceResult.ok(facility);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy danh sách các vị trí / khu vực đặt thiết bị không trùng lặp đang có trong hệ thống.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code facilityDAO.findDistinctLocations()} để trả về danh sách khu vực
     * phục vụ dropdown lọc trên danh mục thiết bị Lễ tân.</p>
     *
     * @return {@link ServiceResult} chứa danh sách tên khu vực
     */
    public ServiceResult<List<String>> getDistinctLocations() {
        try {
            return ServiceResult.ok(facilityDAO.findDistinctLocations());
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Tìm bản ghi nhân viên đang làm việc khớp với tài khoản và vai trò kỳ vọng.
     *
     * <p><b>Cách hoạt động:</b> Tra cứu {@code Account} từ {@code accountId}, lấy {@code Employee} tương ứng qua
     * {@code personId}, rồi đối chiếu chức vụ ({@code expectedRole}) và trạng thái phải đang {@code WORKING}.</p>
     *
     * @param accountId    ID tài khoản cần xác minh
     * @param expectedRole vai trò bắt buộc ({@code RECEPTIONIST} hoặc {@code MANAGER})
     * @return đối tượng {@link Employee} hợp lệ, hoặc {@code null} nếu không thỏa điều kiện
     * @throws SQLException khi truy vấn CSDL lỗi
     */
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

    /**
     * Dựng chuỗi mô tả ngắn gọn cá thể máy kèm tên nhóm máy phục vụ hiển thị thông báo.
     */
    private String buildDeviceDetail(
            Connection connection,
            int equipmentItemId,
            boolean includeItemLabel
    ) throws SQLException {
        EquipmentItem item = equipmentItemDAO.findById(connection, equipmentItemId);
        if (item == null) {
            return "Mã máy #" + equipmentItemId;
        }
        Facility facility = facilityDAO.findById(connection, item.getFacilityId());
        String facName = (facility != null && facility.getFacilityName() != null)
                ? facility.getFacilityName()
                : "";
        String itemCode = item.getItemCode() != null ? item.getItemCode() : "";
        String itemLabel = item.getItemLabel() != null ? item.getItemLabel() : "";

        String detail;
        if (includeItemLabel && !itemLabel.isBlank() && !itemLabel.equalsIgnoreCase(itemCode)) {
            detail = facName + " (" + itemCode + " - " + itemLabel + ")";
        } else {
            detail = facName + " (" + itemCode + ")";
        }

        return detail.isBlank() ? ("Mã máy #" + equipmentItemId) : detail;
    }

    /**
     * Tạo nội dung thông báo báo hỏng hàng loạt luôn nằm trong giới hạn 500 ký tự của {@code Notifications.Content}.
     *
     * <p><b>Cách hoạt động:</b> Hiển thị toàn bộ khi {@code <= 5} máy và đủ độ dài; với số lượng lớn (như 60 máy),
     * chỉ liệt kê tối đa {@value #BATCH_NOTIFICATION_PREVIEW_LIMIT} máy đầu vừa khung ký tự kèm phần tóm tắt
     * {@code " và N máy khác."} để tránh lỗi cắt chuỗi từ SQL Server.</p>
     */
    private static String buildBatchReportNotificationContent(
            int totalCount,
            List<String> summaryItems
    ) {
        String prefix = "Lễ tân vừa báo hỏng " + totalCount + " máy: ";
        if (summaryItems.size() <= BATCH_NOTIFICATION_PREVIEW_LIMIT) {
            String full = prefix + String.join(", ", summaryItems);
            if (full.length() <= NotificationDAO.CONTENT_MAX_LENGTH) {
                return full;
            }
        }

        int maxPreview = Math.min(BATCH_NOTIFICATION_PREVIEW_LIMIT, summaryItems.size());
        StringBuilder preview = new StringBuilder();
        int included = 0;

        for (int i = 0; i < maxPreview; i++) {
            String item = summaryItems.get(i);
            int remaining = totalCount - (i + 1);
            String suffix = remaining > 0 ? " và " + remaining + " máy khác." : "";
            String candidate = preview.length() == 0 ? item : preview + ", " + item;
            if (prefix.length() + candidate.length() + suffix.length() <= NotificationDAO.CONTENT_MAX_LENGTH) {
                if (preview.length() > 0) {
                    preview.append(", ");
                }
                preview.append(item);
                included = i + 1;
            } else {
                break;
            }
        }

        if (included == 0 && !summaryItems.isEmpty()) {
            int remaining = totalCount - 1;
            String suffix = remaining > 0 ? " và " + remaining + " máy khác." : "";
            int available = NotificationDAO.CONTENT_MAX_LENGTH - prefix.length() - suffix.length();
            String firstItem = available > 3 ? NotificationDAO.safeTruncate(summaryItems.get(0), available) : "";
            return NotificationDAO.safeTruncate(prefix + firstItem + suffix, NotificationDAO.CONTENT_MAX_LENGTH);
        }

        int remaining = totalCount - included;
        String suffix = remaining > 0 ? " và " + remaining + " máy khác." : "";
        return NotificationDAO.safeTruncate(prefix + preview + suffix, NotificationDAO.CONTENT_MAX_LENGTH);
    }

    /**
     * Dựng nội dung tóm tắt cho thông báo duyệt hoặc đóng hàng loạt nhiều yêu cầu bảo trì.
     */
    private static String buildBatchStatusNotificationContent(
            String actionPrefix,
            String actionSuffix,
            List<BatchNotificationEntry> entries
    ) {
        int total = entries.size();
        int maxPreview = Math.min(BATCH_NOTIFICATION_PREVIEW_LIMIT, total);
        StringBuilder codes = new StringBuilder();
        for (int i = 0; i < maxPreview; i++) {
            if (i > 0) {
                codes.append(", ");
            }
            codes.append(entries.get(i).requestCode);
        }
        String overflow = total > maxPreview ? ", ..." : "";
        String content = actionPrefix + total + " yêu cầu (" + codes + overflow + ")" + actionSuffix;
        return NotificationDAO.safeTruncate(content, NotificationDAO.CONTENT_MAX_LENGTH);
    }

    /**
     * Ghép thêm ghi chú/lý do tùy chọn vào nội dung thông báo và bảo đảm tổng độ dài không vượt quá 500 ký tự.
     */
    private static String buildNotificationWithOptionalNote(
            String baseContent,
            String notePrefix,
            String rawNote
    ) {
        String safeBase = NotificationDAO.safeTruncate(baseContent, NotificationDAO.CONTENT_MAX_LENGTH);
        if (rawNote == null || rawNote.isBlank()) {
            return safeBase;
        }
        String trimmedNote = rawNote.trim();
        if (safeBase.length() + notePrefix.length() + trimmedNote.length() <= NotificationDAO.CONTENT_MAX_LENGTH) {
            return safeBase + notePrefix + trimmedNote;
        }
        int availableForNote = NotificationDAO.CONTENT_MAX_LENGTH - safeBase.length() - notePrefix.length();
        if (availableForNote < 10) {
            return safeBase;
        }
        return safeBase + notePrefix + NotificationDAO.safeTruncate(trimmedNote, availableForNote);
    }

    private record BatchNotificationEntry(String requestCode, String deviceDetail) {
    }

}
