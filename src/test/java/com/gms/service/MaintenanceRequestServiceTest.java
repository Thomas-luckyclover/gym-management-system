package com.gms.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Unit Test Suite for BF-06: Individual Equipment Maintenance Request Workflow.
 */
class MaintenanceRequestServiceTest {

    private FakeMaintenanceRequestDAO fakeMaintenanceRequestDAO;
    private FakeFacilityDAO fakeFacilityDAO;
    private FakeEquipmentItemDAO fakeEquipmentItemDAO;
    private FakeAccountDAO fakeAccountDAO;
    private FakeEmployeeDAO fakeEmployeeDAO;
    private RecordingAuditLogService recordingAuditLogService;
    private RecordingNotificationService recordingNotificationService;
    private TestMaintenanceRequestService service;

    private static final int RECEPTIONIST_ACCOUNT_ID = 10;
    private static final int RECEPTIONIST_EMPLOYEE_ID = 100;
    private static final int MANAGER_ACCOUNT_ID = 20;
    private static final int MANAGER_EMPLOYEE_ID = 200;

    @BeforeEach
    void setUp() {
        fakeMaintenanceRequestDAO = new FakeMaintenanceRequestDAO();
        fakeFacilityDAO = new FakeFacilityDAO();
        fakeEquipmentItemDAO = new FakeEquipmentItemDAO();
        fakeAccountDAO = new FakeAccountDAO();
        fakeEmployeeDAO = new FakeEmployeeDAO();
        recordingAuditLogService = new RecordingAuditLogService();
        recordingNotificationService = new RecordingNotificationService();

        service = new TestMaintenanceRequestService(
                fakeMaintenanceRequestDAO,
                fakeFacilityDAO,
                fakeEquipmentItemDAO,
                fakeAccountDAO,
                fakeEmployeeDAO,
                recordingAuditLogService,
                recordingNotificationService
        );

        // Thiết lập tài khoản & nhân viên Lễ tân
        Account recAcc = new Account(
                RECEPTIONIST_ACCOUNT_ID,
                1000,
                "hashed",
                Account.Status.ACTIVE,
                0,
                null,
                null,
                LocalDateTime.now()
        );
        fakeAccountDAO.accounts.put(RECEPTIONIST_ACCOUNT_ID, recAcc);

        Employee recEmp = new Employee(
                RECEPTIONIST_EMPLOYEE_ID,
                1000,
                "NV-001",
                Role.RECEPTIONIST,
                LocalDate.now(),
                null,
                null,
                null,
                Employee.Status.WORKING,
                null,
                null
        );
        fakeEmployeeDAO.employees.put(1000, recEmp);

        // Thiết lập tài khoản & nhân viên Quản lý
        Account mgrAcc = new Account(
                MANAGER_ACCOUNT_ID,
                2000,
                "hashed",
                Account.Status.ACTIVE,
                0,
                null,
                null,
                LocalDateTime.now()
        );
        fakeAccountDAO.accounts.put(MANAGER_ACCOUNT_ID, mgrAcc);

        Employee mgrEmp = new Employee(
                MANAGER_EMPLOYEE_ID,
                2000,
                "NV-002",
                Role.MANAGER,
                LocalDate.now(),
                null,
                null,
                null,
                Employee.Status.WORKING,
                null,
                null
        );
        fakeEmployeeDAO.employees.put(2000, mgrEmp);

        // Thiết lập dữ liệu mẫu Facility & EquipmentItem
        Facility f1 = new Facility();
        f1.setFacilityId(1);
        f1.setFacilityName("Máy chạy bộ Matrix T70");
        f1.setCategory(Facility.Category.CARDIO);
        f1.setLocation("Khu Cardio Tầng 1");
        fakeFacilityDAO.facilities.put(1, f1);

        EquipmentItem item1 = new EquipmentItem(1, 1, "TM-001", EquipmentItem.Status.AVAILABLE);
        fakeEquipmentItemDAO.items.put(1, item1);

        Facility f2 = new Facility();
        f2.setFacilityId(2);
        f2.setFacilityName("Máy ép ngực đa năng");
        f2.setCategory(Facility.Category.MACHINES);
        f2.setLocation("Khu Máy khối Tầng 1");
        fakeFacilityDAO.facilities.put(2, f2);

        EquipmentItem item2 = new EquipmentItem(2, 2, "CP-001", EquipmentItem.Status.UNDER_MAINTENANCE);
        fakeEquipmentItemDAO.items.put(2, item2);
    }

    // ===== 1. TẠO YÊU CẦU BẢO TRÌ =====

    @Test
    @DisplayName("Tạo yêu cầu: Báo lỗi khi equipmentItemId <= 0")
    void createRequest_invalidEquipmentItemId_fails() {
        ServiceResult<String> result = service.createRequest(
                RECEPTIONIST_ACCOUNT_ID,
                0,
                "Normal",
                "Máy chạy bộ kêu to ở băng tải",
                false
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("equipmentItemId"));
        assertTrue(result.getMessage().contains("mã máy"));
    }

    @Test
    @DisplayName("Tạo yêu cầu: Báo lỗi khi priority không hợp lệ")
    void createRequest_invalidPriority_fails() {
        ServiceResult<String> result = service.createRequest(
                RECEPTIONIST_ACCOUNT_ID,
                1,
                "UnknownPriority",
                "Máy chạy bộ kêu to ở băng tải",
                false
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("priority"));
    }

    @Test
    @DisplayName("Tạo yêu cầu: Báo lỗi khi description ngắn hơn 10 ký tự")
    void createRequest_shortDescription_fails() {
        ServiceResult<String> result = service.createRequest(
                RECEPTIONIST_ACCOUNT_ID,
                1,
                "Normal",
                "Hỏng máy",
                false
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("description"));
    }

    @Test
    @DisplayName("Tạo yêu cầu: Báo lỗi khi máy con không tồn tại hoặc đã ngưng hoạt động (Retired)")
    void createRequest_retiredEquipmentItem_fails() {
        EquipmentItem retired = new EquipmentItem(99, 1, "TM-999", EquipmentItem.Status.RETIRED);
        fakeEquipmentItemDAO.items.put(99, retired);

        ServiceResult<String> result = service.createRequest(
                RECEPTIONIST_ACCOUNT_ID,
                99,
                "Normal",
                "Máy chạy bộ cũ đã thanh lý xong",
                false
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("equipmentItemId"));
        assertTrue(result.getMessage().contains("ngưng hoạt động"));
    }

    @Test
    @DisplayName("Tạo yêu cầu: Thành công khi máy con đang ở trạng thái Available")
    void createRequest_availableEquipmentItem_success() {
        ServiceResult<String> result = service.createRequest(
                RECEPTIONIST_ACCOUNT_ID,
                1,
                "Urgent",
                "Máy rung lắc mạnh khi tăng tốc độ",
                true
        );

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getMessage());
        assertEquals("BT-0001", result.getData());

        // Kiểm tra AuditLog được ghi đúng Role.RECEPTIONIST
        assertEquals(1, recordingAuditLogService.records.size());
        AuditRecord record = recordingAuditLogService.records.get(0);
        assertEquals(Role.RECEPTIONIST, record.actingRole);
        assertEquals("Tạo yêu cầu bảo trì", record.action);
        assertEquals("MaintenanceRequest", record.entityName);
        assertEquals("Open", record.newValue);

        // Kiểm tra Notification gửi cho Manager kèm mã máy con
        assertEquals(1, recordingNotificationService.roleNotifications.size());
        assertEquals(Role.MANAGER, recordingNotificationService.roleNotifications.get(0).role);
        assertTrue(recordingNotificationService.roleNotifications.get(0).content.contains("TM-001"));
    }

    @Test
    @DisplayName("Tạo yêu cầu: Hỗ trợ truyền IncidentType, AffectedItemsNote và AttachedImages")
    void createRequest_withIncidentTypeAndDetectedBy_success() {
        ServiceResult<String> result = service.createRequest(
                RECEPTIONIST_ACCOUNT_ID,
                1,
                "Urgent",
                "BrokenOrWorn",
                "Customer",
                "Thành viên phản ánh gãy bàn đạp bên trái",
                true,
                "/uploads/broken.jpg",
                "Quả 6 kg, 8 kg"
        );

        assertTrue(result.isSuccess());
        MaintenanceRequest saved = fakeMaintenanceRequestDAO.requests.get(1);
        assertNotNull(saved);
        assertEquals(MaintenanceRequest.IncidentType.BROKEN_OR_WORN, saved.getIncidentType());
        assertEquals("Quả 6 kg, 8 kg", saved.getAffectedItemsNote());
        assertEquals("/uploads/broken.jpg", saved.getAttachedImages());
    }

    @Test
    @DisplayName("Tạo yêu cầu: Cho phép tạo yêu cầu kể cả khi máy con đang UnderMaintenance")
    void createRequest_underMaintenanceEquipmentItem_allowed() {
        ServiceResult<String> result = service.createRequest(
                RECEPTIONIST_ACCOUNT_ID,
                2,
                "Normal",
                "Phát hiện thêm vết rách ở đệm ghế",
                false
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("Tạo yêu cầu hàng loạt: Hỗ trợ báo hỏng nhiều máy cùng lúc")
    void createRequests_batchSuccess() {
        // Thêm máy con TM-002
        EquipmentItem item3 = new EquipmentItem(3, 1, "TM-002", EquipmentItem.Status.AVAILABLE);
        fakeEquipmentItemDAO.items.put(3, item3);

        ServiceResult<List<String>> result = service.createRequests(
                RECEPTIONIST_ACCOUNT_ID,
                List.of(1, 3),
                "Urgent",
                "NoiseOrVibration",
                "Receptionist",
                "Cả hai máy chạy bộ đều bị rung lắc mạnh khi tăng tốc",
                true,
                null
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
        assertEquals(2, fakeMaintenanceRequestDAO.requests.size());

        // Kiểm tra thông báo gửi cho Manager
        assertFalse(recordingNotificationService.roleNotifications.isEmpty());
        assertTrue(recordingNotificationService.roleNotifications.get(0).title.contains("2 yêu cầu mới"));
        assertTrue(recordingNotificationService.roleNotifications.get(0).content.contains("2 máy"));
    }

    @Test
    @DisplayName("Tạo yêu cầu hàng loạt: Thất bại khi danh sách máy con rỗng")
    void createRequests_batchEmpty_fails() {
        ServiceResult<List<String>> result = service.createRequests(
                RECEPTIONIST_ACCOUNT_ID,
                Collections.emptyList(),
                "Normal",
                "NotWorking",
                "Receptionist",
                "Máy không lên nguồn màn hình",
                false,
                null
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("equipmentItemId"));
    }

    // ===== 2. QUY TẮC TÍNH TOÁN SỐ LƯỢNG THIẾT BỊ =====

    @Test
    @DisplayName("Tính toán số lượng: Pending = Count(Open + InProgress), Available = MAX(0, Total - Pending)")
    void facilityQuantityCalculations() {
        Facility f = new Facility();
        f.setTotalQuantity(5);
        f.setPendingQuantity(2);
        f.setAvailableQuantity(3);

        assertEquals(5, f.getTotalQuantity());
        assertEquals(2, f.getPendingQuantity());
        assertEquals(3, f.getAvailableQuantity());
        assertTrue(f.isAvailable());
    }

    @Test
    @DisplayName("Danh mục thiết bị: Kiểm tra khớp từ khóa tiếng Việt không dấu và tiếng Anh")
    void facilityCategorySearch() {
        assertTrue(Facility.Category.matchByKeyword("ta tu do").contains(Facility.Category.FREE_WEIGHTS));
        assertTrue(Facility.Category.matchByKeyword("may tap").contains(Facility.Category.MACHINES));
        assertTrue(Facility.Category.matchByKeyword("tien ich").contains(Facility.Category.AMENITIES));
        assertTrue(Facility.Category.matchByKeyword("freeweights").contains(Facility.Category.FREE_WEIGHTS));
        assertTrue(Facility.Category.matchByKeyword("machines").contains(Facility.Category.MACHINES));
    }

    @Test
    @DisplayName("Catalog Search: Tìm kiếm theo tên, danh mục tiếng Việt/tiếng Anh, khu vực và bỏ qua bộ lọc cũ")
    void facilityCatalogSearch_multiFieldAndObsoleteFiltersIgnored() {
        fakeFacilityDAO.facilities.clear();

        Facility f1 = new Facility(1, "Máy ép ngực", Facility.Category.MACHINES, "Tầng 2", 2, Facility.Status.AVAILABLE);
        Facility f2 = new Facility(2, "Máy chạy bộ A1", Facility.Category.CARDIO, "Khu Cardio Tầng 1", 5, Facility.Status.AVAILABLE);
        Facility f3 = new Facility(3, "Bộ tạ tay cao su", Facility.Category.FREE_WEIGHTS, "Khu Tạ", 10, Facility.Status.AVAILABLE);

        fakeFacilityDAO.facilities.put(1, f1);
        fakeFacilityDAO.facilities.put(2, f2);
        fakeFacilityDAO.facilities.put(3, f3);

        // 1. Tìm theo tên thiết bị
        ServiceResult<PageResult<Facility>> resName = service.getFacilityCatalogPage("ép ngực", 1, 10);
        assertTrue(resName.isSuccess());
        assertEquals(1, resName.getData().getTotalItems());
        assertEquals("Máy ép ngực", resName.getData().getItems().get(0).getFacilityName());

        // 2. Tìm theo danh mục tiếng Việt (Tạ tự do)
        ServiceResult<PageResult<Facility>> resCat = service.getFacilityCatalogPage("Tạ tự do", 1, 10);
        assertTrue(resCat.isSuccess());
        assertEquals(1, resCat.getData().getTotalItems());
        assertEquals("Bộ tạ tay cao su", resCat.getData().getItems().get(0).getFacilityName());

        // 3. Tìm theo khu vực (Tầng 1)
        ServiceResult<PageResult<Facility>> resLoc = service.getFacilityCatalogPage("Tầng 1", 1, 10);
        assertTrue(resLoc.isSuccess());
        assertEquals(1, resLoc.getData().getTotalItems());
        assertEquals("Máy chạy bộ A1", resLoc.getData().getItems().get(0).getFacilityName());

        // 4. Từ khóa rỗng -> trả về tất cả thiết bị
        ServiceResult<PageResult<Facility>> resEmpty = service.getFacilityCatalogPage("", 1, 10);
        assertTrue(resEmpty.isSuccess());
        assertEquals(3, resEmpty.getData().getTotalItems());

        // 5. URL cũ truyền category và status -> được bỏ qua an toàn
        ServiceResult<PageResult<Facility>> resLegacy = service.getFacilityCatalogPage(
                "Máy ép ngực", "Cardio", "Retired", 1, 10
        );
        assertTrue(resLegacy.isSuccess());
        assertEquals(1, resLegacy.getData().getTotalItems());
        assertEquals("Máy ép ngực", resLegacy.getData().getItems().get(0).getFacilityName());
    }

    // ===== 3. DUYỆT YÊU CẦU BẢO TRÌ (QUẢN LÝ) =====

    @Test
    @DisplayName("Duyệt yêu cầu: Báo lỗi nếu yêu cầu không ở trạng thái Open")
    void approveRequest_notOpen_fails() {
        MaintenanceRequest req = new MaintenanceRequest();
        req.setMaintenanceRequestId(1);
        req.setStatus(MaintenanceRequest.Status.IN_PROGRESS);
        fakeMaintenanceRequestDAO.requests.put(1, req);

        ServiceResult<Void> result = service.approveRequest(MANAGER_ACCOUNT_ID, 1, true);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Chờ duyệt"));
    }

    @Test
    @DisplayName("Duyệt yêu cầu: Cập nhật riêng EquipmentItem sang UnderMaintenance và ghi AuditLog Role.MANAGER")
    void approveRequest_success_updatesEquipmentItemAndAuditLog() {
        EquipmentItem item = new EquipmentItem(10, 1, "TM-010", EquipmentItem.Status.AVAILABLE);
        fakeEquipmentItemDAO.items.put(10, item);

        MaintenanceRequest req = new MaintenanceRequest();
        req.setMaintenanceRequestId(5);
        req.setRequestCode("BT-0005");
        req.setEquipmentItemId(10);
        req.setReportedByEmployeeId(RECEPTIONIST_EMPLOYEE_ID);
        req.setStatus(MaintenanceRequest.Status.OPEN);
        fakeMaintenanceRequestDAO.requests.put(5, req);

        ServiceResult<Void> result = service.approveRequest(MANAGER_ACCOUNT_ID, 5, true);

        assertTrue(result.isSuccess());
        assertEquals(MaintenanceRequest.Status.IN_PROGRESS, req.getStatus());
        assertEquals(EquipmentItem.Status.UNDER_MAINTENANCE, item.getStatus());

        // Kiểm tra có 2 AuditLog: 1 cho EquipmentItem status và 1 cho MaintenanceRequest
        assertEquals(2, recordingAuditLogService.records.size());

        AuditRecord itemAudit = recordingAuditLogService.records.get(0);
        assertEquals(Role.MANAGER, itemAudit.actingRole);
        assertEquals("EquipmentItem", itemAudit.entityName);
        assertEquals("Available", itemAudit.oldValue);
        assertEquals("UnderMaintenance", itemAudit.newValue);

        AuditRecord requestAudit = recordingAuditLogService.records.get(1);
        assertEquals(Role.MANAGER, requestAudit.actingRole);
        assertEquals("MaintenanceRequest", requestAudit.entityName);
        assertEquals("Open", requestAudit.oldValue);
        assertEquals("InProgress", requestAudit.newValue);
    }

    @Test
    @DisplayName("Duyệt yêu cầu: Không ghi AuditLog EquipmentItem nếu máy vốn đã UnderMaintenance")
    void approveRequest_alreadyUnderMaintenance_noDuplicateItemAudit() {
        EquipmentItem item = new EquipmentItem(10, 1, "TM-010", EquipmentItem.Status.UNDER_MAINTENANCE);
        fakeEquipmentItemDAO.items.put(10, item);

        MaintenanceRequest req = new MaintenanceRequest();
        req.setMaintenanceRequestId(6);
        req.setRequestCode("BT-0006");
        req.setEquipmentItemId(10);
        req.setReportedByEmployeeId(RECEPTIONIST_EMPLOYEE_ID);
        req.setStatus(MaintenanceRequest.Status.OPEN);
        fakeMaintenanceRequestDAO.requests.put(6, req);

        ServiceResult<Void> result = service.approveRequest(MANAGER_ACCOUNT_ID, 6, true);

        assertTrue(result.isSuccess());
        // Chỉ có 1 AuditLog cho MaintenanceRequest
        assertEquals(1, recordingAuditLogService.records.size());
        assertEquals("MaintenanceRequest", recordingAuditLogService.records.get(0).entityName);
    }

    // ===== 4. TỪ CHỐI YÊU CẦU BẢO TRÌ (QUẢN LÝ) =====

    @Test
    @DisplayName("Từ chối yêu cầu: Báo lỗi khi lý do từ chối dưới 5 ký tự")
    void rejectRequest_shortReason_fails() {
        ServiceResult<Void> result = service.rejectRequest(MANAGER_ACCOUNT_ID, 1, "Lỗi");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("rejectReason"));
    }

    @Test
    @DisplayName("Từ chối yêu cầu: Thành công chuyển sang Rejected và ghi AuditLog Role.MANAGER kèm lý do")
    void rejectRequest_success() {
        MaintenanceRequest req = new MaintenanceRequest();
        req.setMaintenanceRequestId(7);
        req.setRequestCode("BT-0007");
        req.setReportedByEmployeeId(RECEPTIONIST_EMPLOYEE_ID);
        req.setStatus(MaintenanceRequest.Status.OPEN);
        fakeMaintenanceRequestDAO.requests.put(7, req);

        ServiceResult<Void> result = service.rejectRequest(
                MANAGER_ACCOUNT_ID,
                7,
                "Thiết bị hoạt động bình thường, không có hỏng hóc."
        );

        assertTrue(result.isSuccess());
        assertEquals(MaintenanceRequest.Status.REJECTED, req.getStatus());

        assertEquals(1, recordingAuditLogService.records.size());
        AuditRecord audit = recordingAuditLogService.records.get(0);
        assertEquals(Role.MANAGER, audit.actingRole);
        assertEquals("Từ chối yêu cầu bảo trì", audit.action);
        assertEquals("Rejected", audit.newValue);
        assertEquals("Thiết bị hoạt động bình thường, không có hỏng hóc.", audit.reason);
    }

    // ===== 5. NGHIỆM THU VÀ ĐÓNG YÊU CẦU (LỄ TÂN) =====

    @Test
    @DisplayName("Đóng yêu cầu: Báo lỗi khi ghi chú nghiệm thu dưới 5 ký tự")
    void closeRequest_shortNote_fails() {
        ServiceResult<Void> result = service.closeRequest(RECEPTIONIST_ACCOUNT_ID, 1, "OK");
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("acceptanceNote"));
    }

    @Test
    @DisplayName("Đóng yêu cầu: Báo lỗi khi yêu cầu không ở trạng thái InProgress")
    void closeRequest_notInProgress_fails() {
        MaintenanceRequest req = new MaintenanceRequest();
        req.setMaintenanceRequestId(8);
        req.setStatus(MaintenanceRequest.Status.OPEN);
        fakeMaintenanceRequestDAO.requests.put(8, req);

        ServiceResult<Void> result = service.closeRequest(
                RECEPTIONIST_ACCOUNT_ID,
                8,
                "Đã kiểm tra hoạt động tốt"
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("đang xử lý"));
    }

    @Test
    @DisplayName("Đóng yêu cầu: Khôi phục máy con sang Available khi không còn yêu cầu InProgress khác")
    void closeRequest_restoresEquipmentItemToAvailable() {
        EquipmentItem item = new EquipmentItem(20, 1, "TM-020", EquipmentItem.Status.UNDER_MAINTENANCE);
        fakeEquipmentItemDAO.items.put(20, item);

        MaintenanceRequest req = new MaintenanceRequest();
        req.setMaintenanceRequestId(9);
        req.setRequestCode("BT-0009");
        req.setEquipmentItemId(20);
        req.setReportedByEmployeeId(RECEPTIONIST_EMPLOYEE_ID);
        req.setReviewedByEmployeeId(MANAGER_EMPLOYEE_ID);
        req.setStatus(MaintenanceRequest.Status.IN_PROGRESS);
        fakeMaintenanceRequestDAO.requests.put(9, req);
        fakeMaintenanceRequestDAO.hasOtherInProgress = false;

        ServiceResult<Void> result = service.closeRequest(
                RECEPTIONIST_ACCOUNT_ID,
                9,
                "Kỹ thuật viên đã thay dây cáp và bôi trơn ròng rọc."
        );

        assertTrue(result.isSuccess());
        assertEquals(MaintenanceRequest.Status.CLOSED, req.getStatus());
        assertEquals(EquipmentItem.Status.AVAILABLE, item.getStatus());

        // Kiểm tra AuditLog: 1 cho khôi phục EquipmentItem, 1 cho đóng MaintenanceRequest
        assertEquals(2, recordingAuditLogService.records.size());

        AuditRecord itemAudit = recordingAuditLogService.records.get(0);
        assertEquals(Role.RECEPTIONIST, itemAudit.actingRole);
        assertEquals("EquipmentItem", itemAudit.entityName);
        assertEquals("UnderMaintenance", itemAudit.oldValue);
        assertEquals("Available", itemAudit.newValue);

        AuditRecord requestAudit = recordingAuditLogService.records.get(1);
        assertEquals(Role.RECEPTIONIST, requestAudit.actingRole);
        assertEquals("MaintenanceRequest", requestAudit.entityName);
        assertEquals("Closed", requestAudit.newValue);
    }

    @Test
    @DisplayName("Đóng yêu cầu: Giữ nguyên UnderMaintenance nếu máy con vẫn còn yêu cầu InProgress khác")
    void closeRequest_otherInProgressExists_equipmentItemRemainsUnderMaintenance() {
        EquipmentItem item = new EquipmentItem(20, 1, "TM-020", EquipmentItem.Status.UNDER_MAINTENANCE);
        fakeEquipmentItemDAO.items.put(20, item);

        MaintenanceRequest req = new MaintenanceRequest();
        req.setMaintenanceRequestId(10);
        req.setRequestCode("BT-0010");
        req.setEquipmentItemId(20);
        req.setReportedByEmployeeId(RECEPTIONIST_EMPLOYEE_ID);
        req.setStatus(MaintenanceRequest.Status.IN_PROGRESS);
        fakeMaintenanceRequestDAO.requests.put(10, req);
        fakeMaintenanceRequestDAO.hasOtherInProgress = true;

        ServiceResult<Void> result = service.closeRequest(
                RECEPTIONIST_ACCOUNT_ID,
                10,
                "Đã sửa xong phần nguồn điện."
        );

        assertTrue(result.isSuccess());
        assertEquals(MaintenanceRequest.Status.CLOSED, req.getStatus());
        assertEquals(EquipmentItem.Status.UNDER_MAINTENANCE, item.getStatus());

        // Chỉ có 1 AuditLog cho đóng request, không có AuditLog đổi trạng thái EquipmentItem
        assertEquals(1, recordingAuditLogService.records.size());
        assertEquals("MaintenanceRequest", recordingAuditLogService.records.get(0).entityName);
    }

    // ===== FAKE DAOS & RECORDERS =====

    private static class TestMaintenanceRequestService extends MaintenanceRequestService {
        TestMaintenanceRequestService(
                MaintenanceRequestDAO maintenanceRequestDAO,
                FacilityDAO facilityDAO,
                EquipmentItemDAO equipmentItemDAO,
                AccountDAO accountDAO,
                EmployeeDAO employeeDAO,
                AuditLogService auditLogService,
                NotificationService notificationService
        ) {
            super(maintenanceRequestDAO, facilityDAO, equipmentItemDAO, accountDAO, employeeDAO, auditLogService, notificationService);
        }

        @Override
        protected Connection openConnection() {
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class) return false;
                        if (returnType == int.class) return 0;
                        return null;
                    }
            );
        }
    }

    private static class FakeEquipmentItemDAO extends EquipmentItemDAO {
        final Map<Integer, EquipmentItem> items = new HashMap<>();

        @Override
        public EquipmentItem findById(Connection connection, int equipmentItemId) {
            return items.get(equipmentItemId);
        }

        @Override
        public boolean updateStatus(Connection connection, int equipmentItemId, EquipmentItem.Status status) {
            EquipmentItem item = items.get(equipmentItemId);
            if (item == null) return false;
            item.setStatus(status);
            return true;
        }

        @Override
        public List<EquipmentItem> findByFacilityId(int facilityId) {
            List<EquipmentItem> list = new ArrayList<>();
            for (EquipmentItem it : items.values()) {
                if (it.getFacilityId() == facilityId) list.add(it);
            }
            return list;
        }

        @Override
        public List<EquipmentItem> findAllSelectable() {
            List<EquipmentItem> list = new ArrayList<>();
            for (EquipmentItem it : items.values()) {
                if (it.getStatus() != EquipmentItem.Status.RETIRED) list.add(it);
            }
            return list;
        }
    }

    private static class FakeMaintenanceRequestDAO extends MaintenanceRequestDAO {
        final Map<Integer, MaintenanceRequest> requests = new HashMap<>();
        boolean hasOtherInProgress = false;
        int nextId = 1;

        @Override
        public String generateNextRequestCode(Connection connection) {
            return String.format("BT-%04d", nextId);
        }

        @Override
        public int insert(Connection connection, MaintenanceRequest request) {
            int id = nextId++;
            request.setMaintenanceRequestId(id);
            requests.put(id, request);
            return id;
        }

        @Override
        public MaintenanceRequest findByIdForUpdate(Connection connection, int requestId) {
            return requests.get(requestId);
        }

        @Override
        public boolean approve(Connection connection, int requestId, int managerEmployeeId, boolean setUnderMaintenance) {
            return approve(connection, requestId, managerEmployeeId, setUnderMaintenance, null);
        }

        @Override
        public boolean approve(Connection connection, int requestId, int managerEmployeeId, boolean setUnderMaintenance, String managerNote) {
            MaintenanceRequest req = requests.get(requestId);
            if (req == null) return false;
            req.setStatus(MaintenanceRequest.Status.IN_PROGRESS);
            req.setReviewedByEmployeeId(managerEmployeeId);
            req.setManagerNote(managerNote);
            return true;
        }

        @Override
        public boolean reject(Connection connection, int requestId, int managerEmployeeId, String reason) {
            MaintenanceRequest req = requests.get(requestId);
            if (req == null) return false;
            req.setStatus(MaintenanceRequest.Status.REJECTED);
            req.setReviewedByEmployeeId(managerEmployeeId);
            req.setRejectReason(reason);
            return true;
        }

        @Override
        public boolean close(Connection connection, int requestId, int receptionistEmployeeId, String note) {
            MaintenanceRequest req = requests.get(requestId);
            if (req == null) return false;
            req.setStatus(MaintenanceRequest.Status.CLOSED);
            req.setAcceptanceNote(note);
            return true;
        }

        @Override
        public boolean hasInProgressRequest(Connection connection, int equipmentItemId) {
            return hasOtherInProgress;
        }
    }

    private static class FakeFacilityDAO extends FacilityDAO {
        final Map<Integer, Facility> facilities = new HashMap<>();

        @Override
        public Facility findById(Connection connection, int facilityId) {
            return facilities.get(facilityId);
        }

        @Override
        public boolean updateStatus(Connection connection, int facilityId, Facility.Status status) {
            Facility f = facilities.get(facilityId);
            if (f == null) return false;
            f.setStatus(status);
            return true;
        }

        @Override
        public long countCatalog(String keyword) {
            return filterFacilities(keyword).size();
        }

        @Override
        public List<Facility> findCatalogPage(String keyword, int page, int pageSize) {
            return filterFacilities(keyword);
        }

        private List<Facility> filterFacilities(String keyword) {
            if (keyword == null || keyword.isBlank()) {
                return new ArrayList<>(facilities.values());
            }
            List<Facility.Category> matchedCats = Facility.Category.matchByKeyword(keyword);
            String normKey = keyword.trim().toLowerCase();
            List<Facility> result = new ArrayList<>();
            for (Facility f : facilities.values()) {
                boolean nameMatch = f.getFacilityName() != null && f.getFacilityName().toLowerCase().contains(normKey);
                boolean locMatch = f.getLocation() != null && f.getLocation().toLowerCase().contains(normKey);
                boolean catMatch = matchedCats.contains(f.getCategory());
                if (nameMatch || locMatch || catMatch) {
                    result.add(f);
                }
            }
            return result;
        }
    }

    private static class FakeAccountDAO extends AccountDAO {
        final Map<Integer, Account> accounts = new HashMap<>();

        @Override
        public Account findById(int accountId) {
            return accounts.get(accountId);
        }
    }

    private static class FakeEmployeeDAO extends EmployeeDAO {
        final Map<Integer, Employee> employees = new HashMap<>();

        @Override
        public Employee findByPersonId(int personId) {
            return employees.get(personId);
        }
    }

    private static class AuditRecord {
        final Role actingRole;
        final String action;
        final String entityName;
        final Integer entityId;
        final String fieldChanged;
        final String oldValue;
        final String newValue;
        final String reason;

        AuditRecord(Role actingRole, String action, String entityName, Integer entityId,
                    String fieldChanged, String oldValue, String newValue, String reason) {
            this.actingRole = actingRole;
            this.action = action;
            this.entityName = entityName;
            this.entityId = entityId;
            this.fieldChanged = fieldChanged;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.reason = reason;
        }
    }

    private static class RecordingAuditLogService extends AuditLogService {
        final List<AuditRecord> records = new ArrayList<>();

        @Override
        void logInTransaction(Connection conn, Integer accountId, Role actingRole, String action,
                              String entityName, Integer entityId, String fieldChanged,
                              String oldValue, String newValue, String reason) {
            records.add(new AuditRecord(actingRole, action, entityName, entityId, fieldChanged, oldValue, newValue, reason));
        }
    }

    private static class RoleNotification {
        final Role role;
        final String title;
        final String content;

        RoleNotification(Role role, String title, String content) {
            this.role = role;
            this.title = title;
            this.content = content;
        }
    }

    private static class RecordingNotificationService extends NotificationService {
        final List<RoleNotification> roleNotifications = new ArrayList<>();

        @Override
        public void notifyRoleInTransaction(Connection connection, Role role, String title, String content) {
            roleNotifications.add(new RoleNotification(role, title, content));
        }

        @Override
        public void notifyEmployeeInTransaction(Connection connection, int employeeId, String title, String content) {
        }
    }
}
