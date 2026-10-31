package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.PackageDAO;
import com.gms.dto.PackageFormInput;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test Suite cho PackageService kiểm tra các ràng buộc nghiệp vụ:
 * - Gói hội viên & Vé ngày (DBR-06, DBR-11, DBR-15, P-04).
 * - Gói PT & Buổi lẻ (DBR-06, DBR-11, DBR-15, P-21, CK_Packages_EstSession).
 * - Kiểm soát đồng thời (optimistic concurrency - MSG22) & Thay đổi trạng thái bán (Commit 20).
 */
class PackageServiceTest {

    private FakePackageDAO fakePackageDAO;
    private RecordingAuditLogService recordingAuditLogService;
    private TestablePackageService service;

    private static final int MANAGER_ACCOUNT_ID = 20;

    @BeforeEach
    void setUp() {
        fakePackageDAO = new FakePackageDAO();
        recordingAuditLogService = new RecordingAuditLogService();
        service = new TestablePackageService(fakePackageDAO, recordingAuditLogService);
    }

    @Test
    @DisplayName("Thêm gói hội viên hợp lệ (1 tháng, 500.000 đ) thành công")
    void testCreateMembershipPackageSuccess() {
        PackageFormInput input = new PackageFormInput();
        input.setName("Gói Tập Tiêu Chuẩn 1 Tháng");
        input.setType("Membership");
        input.setPrice("500000");
        input.setDurationUnit("Month");
        input.setDurationValue("1");
        input.setActive(true);
        input.setUnlocksExerciseLibrary(true);
        input.setDescription("Gói tập 1 tháng tiêu chuẩn");
        input.setBenefits("Tập luyện không giới hạn khung giờ\nMiễn phí tủ đồ thông minh\nSử dụng phòng tắm nước nóng");

        ServiceResult<Integer> result = service.createMembershipPackage(MANAGER_ACCOUNT_ID, input);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("Thêm gói dịch vụ thành công.", result.getMessage());
        assertEquals(1, recordingAuditLogService.records.size());
        assertEquals("CREATE", recordingAuditLogService.records.get(0).action);
    }

    @Test
    @DisplayName("Tên gói dưới 3 ký tự hoặc trên 50 ký tự bị từ chối (DBR-06)")
    void testPackageNameLengthValidation() {
        // Tên quá ngắn (< 3 ký tự)
        PackageFormInput inputShort = new PackageFormInput();
        inputShort.setName("AB");
        inputShort.setType("Membership");
        inputShort.setPrice("500000");
        inputShort.setDurationUnit("Month");
        inputShort.setDurationValue("1");

        ServiceResult<Integer> resultShort = service.createMembershipPackage(MANAGER_ACCOUNT_ID, inputShort);
        assertFalse(resultShort.isSuccess());
        assertTrue(resultShort.getFieldErrors().containsKey("name"));

        // Tên quá dài (> 50 ký tự)
        PackageFormInput inputLong = new PackageFormInput();
        inputLong.setName("Gói tập thể hình siêu cấp vip pro dành cho gymer năm 2026 vượt quá 50 ký tự");
        inputLong.setType("Membership");
        inputLong.setPrice("500000");
        inputLong.setDurationUnit("Month");
        inputLong.setDurationValue("1");

        ServiceResult<Integer> resultLong = service.createMembershipPackage(MANAGER_ACCOUNT_ID, inputLong);
        assertFalse(resultLong.isSuccess());
        assertTrue(resultLong.getFieldErrors().containsKey("name"));
    }

    @Test
    @DisplayName("Giá gói ngoài khoảng 10.000 đ - 500.000.000 đ hoặc không phải bội số 1.000 đ bị từ chối (DBR-11)")
    void testPackagePriceValidation() {
        // Giá nhỏ hơn 10.000 đ
        PackageFormInput inputTooLow = new PackageFormInput();
        inputTooLow.setName("Gói Giá Rẻ");
        inputTooLow.setType("Membership");
        inputTooLow.setPrice("5000");
        inputTooLow.setDurationUnit("Month");
        inputTooLow.setDurationValue("1");

        ServiceResult<Integer> resLow = service.createMembershipPackage(MANAGER_ACCOUNT_ID, inputTooLow);
        assertFalse(resLow.isSuccess());
        assertTrue(resLow.getFieldErrors().containsKey("price"));

        // Giá không phải bội số của 1.000 đ (lẻ 500 đ)
        PackageFormInput inputNotMultiple = new PackageFormInput();
        inputNotMultiple.setName("Gói Lẻ Tiền");
        inputNotMultiple.setType("Membership");
        inputNotMultiple.setPrice("100500");
        inputNotMultiple.setDurationUnit("Month");
        inputNotMultiple.setDurationValue("1");

        ServiceResult<Integer> resNotMult = service.createMembershipPackage(MANAGER_ACCOUNT_ID, inputNotMultiple);
        assertFalse(resNotMult.isSuccess());
        assertTrue(resNotMult.getFieldErrors().containsKey("price"));
    }

    @Test
    @DisplayName("Thời hạn gói hội viên không nằm trong P-04 (1, 3, 6, 12, 24, 36) bị từ chối")
    void testMembershipDurationWhitelistValidation() {
        PackageFormInput input = new PackageFormInput();
        input.setName("Gói 5 Tháng Thử Nghiệm");
        input.setType("Membership");
        input.setPrice("2000000");
        input.setDurationUnit("Month");
        input.setDurationValue("5"); // 5 tháng không nằm trong P-04

        ServiceResult<Integer> result = service.createMembershipPackage(MANAGER_ACCOUNT_ID, input);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("durationValue"));
    }

    @Test
    @DisplayName("Vé ngày bắt buộc thời hạn là 1 Ngày theo P-04")
    void testDayPassDurationValidation() {
        PackageFormInput inputInvalidDay = new PackageFormInput();
        inputInvalidDay.setName("Vé Tập 2 Ngày");
        inputInvalidDay.setType("DayPass");
        inputInvalidDay.setPrice("100000");
        inputInvalidDay.setDurationUnit("Day");
        inputInvalidDay.setDurationValue("2");

        ServiceResult<Integer> result = service.createMembershipPackage(MANAGER_ACCOUNT_ID, inputInvalidDay);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("durationValue"));

        // Hợp lệ: 1 ngày
        PackageFormInput inputValidDay = new PackageFormInput();
        inputValidDay.setName("Vé Tập 1 Ngày Chuẩn");
        inputValidDay.setType("DayPass");
        inputValidDay.setPrice("80000");
        inputValidDay.setDurationUnit("Day");
        inputValidDay.setDurationValue("1");

        ServiceResult<Integer> resultOk = service.createMembershipPackage(MANAGER_ACCOUNT_ID, inputValidDay);
        assertTrue(resultOk.isSuccess());
    }

    @Test
    @DisplayName("Trùng tên gói đang bán bị từ chối (DBR-06)")
    void testDuplicateActivePackageName() {
        fakePackageDAO.activeNames.add("Gói Tập Gym 1 Tháng");

        PackageFormInput input = new PackageFormInput();
        input.setName("Gói Tập Gym 1 Tháng");
        input.setType("Membership");
        input.setPrice("600000");
        input.setDurationUnit("Month");
        input.setDurationValue("1");
        input.setActive(true);

        ServiceResult<Integer> result = service.createMembershipPackage(MANAGER_ACCOUNT_ID, input);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("name"));
    }

    // ===== Tests cho Gói PT & Buổi lẻ (Commit 19) =====

    @Test
    @DisplayName("Tạo mới gói PT 1:1 chuẩn (3 tháng, 65 buổi, 12.000.000 đ) thành công")
    void testCreatePtPackageSuccess() {
        PackageFormInput input = new PackageFormInput();
        input.setName("PT 1:1 Tăng Cơ Giảm Mỡ 3 Tháng");
        input.setType("PTSingle");
        input.setPrice("12000000");
        input.setDurationUnit("Month");
        input.setDurationValue("3");
        input.setEstimatedSessionCount("65");
        input.setActive(true);
        input.setBenefits("1 Huấn luyện viên kèm riêng 1:1\nLên giáo án dinh dưỡng cá nhân hóa\nĐo chỉ số InBody định kỳ");

        ServiceResult<Integer> result = service.createPtPackage(MANAGER_ACCOUNT_ID, input);
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("Thêm gói dịch vụ thành công.", result.getMessage());
    }

    @Test
    @DisplayName("Không cho phép tạo mới trực tiếp gói Buổi PT lẻ để tránh trùng gói đơn lẻ")
    void testCannotCreateSingleSessionDirectly() {
        PackageFormInput input = new PackageFormInput();
        input.setName("Buổi PT Lẻ Thứ Hai");
        input.setType("SingleSession");
        input.setPrice("300000");

        ServiceResult<Integer> result = service.createPtPackage(MANAGER_ACCOUNT_ID, input);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("type"));
    }

    @Test
    @DisplayName("Thời hạn gói PT không thuộc P-21 (1, 3, 6, 12 tháng) bị từ chối")
    void testPtDurationWhitelistValidation() {
        PackageFormInput input = new PackageFormInput();
        input.setName("PT 24 Tháng");
        input.setType("PTSingle");
        input.setPrice("50000000");
        input.setDurationUnit("Month");
        input.setDurationValue("24"); // 24 tháng không có trong P-21
        input.setEstimatedSessionCount("200");

        ServiceResult<Integer> result = service.createPtPackage(MANAGER_ACCOUNT_ID, input);
        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("durationValue"));
    }

    @Test
    @DisplayName("Số buổi PT ước tính ngoài khoảng 1 - 255 bị từ chối (DB TINYINT & CK_Packages_EstSession)")
    void testPtEstimatedSessionCountValidation() {
        // 0 buổi
        PackageFormInput inputZero = new PackageFormInput();
        inputZero.setName("PT 1 Tháng Không Có Buổi");
        inputZero.setType("PTSingle");
        inputZero.setPrice("4000000");
        inputZero.setDurationUnit("Month");
        inputZero.setDurationValue("1");
        inputZero.setEstimatedSessionCount("0");

        ServiceResult<Integer> resZero = service.createPtPackage(MANAGER_ACCOUNT_ID, inputZero);
        assertFalse(resZero.isSuccess());
        assertTrue(resZero.getFieldErrors().containsKey("estimatedSessionCount"));

        // 300 buổi (> 255)
        PackageFormInput inputTooHigh = new PackageFormInput();
        inputTooHigh.setName("PT 12 Tháng Quá Nhiều Buổi");
        inputTooHigh.setType("PTCouple");
        inputTooHigh.setPrice("45000000");
        inputTooHigh.setDurationUnit("Month");
        inputTooHigh.setDurationValue("12");
        inputTooHigh.setEstimatedSessionCount("300");

        ServiceResult<Integer> resTooHigh = service.createPtPackage(MANAGER_ACCOUNT_ID, inputTooHigh);
        assertFalse(resTooHigh.isSuccess());
        assertTrue(resTooHigh.getFieldErrors().containsKey("estimatedSessionCount"));
    }

    @Test
    @DisplayName("Quyền lợi vượt quá 10 dòng hoặc mỗi dòng ngoài 3-100 ký tự bị từ chối (DBR-15)")
    void testBenefitsValidationRules() {
        // Dòng quá ngắn (< 3 ký tự)
        PackageFormInput inputShortLine = new PackageFormInput();
        inputShortLine.setName("Gói PT Lỗi Quyền Lợi");
        inputShortLine.setType("PTSingle");
        inputShortLine.setPrice("5000000");
        inputShortLine.setDurationUnit("Month");
        inputShortLine.setDurationValue("1");
        inputShortLine.setEstimatedSessionCount("20");
        inputShortLine.setBenefits("OK\nDòng thứ hai hợp lệ");

        ServiceResult<Integer> resShort = service.createPtPackage(MANAGER_ACCOUNT_ID, inputShortLine);
        assertFalse(resShort.isSuccess());
        assertTrue(resShort.getFieldErrors().containsKey("benefits"));

        // Hơn 10 dòng
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 11; i++) {
            sb.append("Dòng quyền lợi số ").append(i).append("\n");
        }
        PackageFormInput input11Lines = new PackageFormInput();
        input11Lines.setName("Gói PT 11 Dòng Quyền Lợi");
        input11Lines.setType("PTSingle");
        input11Lines.setPrice("5000000");
        input11Lines.setDurationUnit("Month");
        input11Lines.setDurationValue("1");
        input11Lines.setEstimatedSessionCount("20");
        input11Lines.setBenefits(sb.toString());

        ServiceResult<Integer> res11 = service.createPtPackage(MANAGER_ACCOUNT_ID, input11Lines);
        assertFalse(res11.isSuccess());
        assertTrue(res11.getFieldErrors().containsKey("benefits"));
    }

    // ===== Tests cho Optimistic Locking & Status Toggling (Commit 20) =====

    @Test
    @DisplayName("Xung đột phiên bản đồng thời (RowVer) trả về thông báo MSG22")
    void testOptimisticLockingConflict() {
        Package pkg = new Package(1);
        pkg.setPackageName("Gói 1 Tháng");
        pkg.setPackageType(Package.Type.MEMBERSHIP);
        pkg.setDurationUnit("Month");
        pkg.setDurationValue(1);
        pkg.setPrice(600000);
        pkg.setActive(true);
        byte[] originalRowVer = new byte[]{0, 0, 0, 0, 0, 0, 0, 1};
        pkg.setRowVersion(originalRowVer);
        fakePackageDAO.packages.put(1, pkg);

        // Giả lập xung đột: fakePackageDAO.update trả về false (do RowVer trên DB đã bị người khác đổi)
        fakePackageDAO.simulateConcurrencyConflict = true;

        PackageFormInput input = new PackageFormInput();
        input.setPackageId(1);
        input.setName("Gói 1 Tháng Cập Nhật");
        input.setType("Membership");
        input.setPrice("650000");
        input.setDurationUnit("Month");
        input.setDurationValue("1");
        input.setRowVersion(Base64.getEncoder().encodeToString(originalRowVer));

        ServiceResult<Integer> result = service.updateMembershipPackage(MANAGER_ACCOUNT_ID, input);
        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG22_CONCURRENT_EDIT, result.getMessage());
    }

    @Test
    @DisplayName("Cập nhật giá gói ghi nhận AuditLog riêng biệt UPDATE_PRICE")
    void testPriceUpdateAuditLogging() {
        Package pkg = new Package(2);
        pkg.setPackageName("Gói 3 Tháng");
        pkg.setPackageType(Package.Type.MEMBERSHIP);
        pkg.setDurationUnit("Month");
        pkg.setDurationValue(3);
        pkg.setPrice(1500000);
        pkg.setActive(true);
        byte[] rowVer = new byte[]{0, 0, 0, 0, 0, 0, 0, 2};
        pkg.setRowVersion(rowVer);
        fakePackageDAO.packages.put(2, pkg);

        PackageFormInput input = new PackageFormInput();
        input.setPackageId(2);
        input.setName("Gói 3 Tháng");
        input.setType("Membership");
        input.setPrice("1800000"); // Đổi giá từ 1.500.000 -> 1.800.000
        input.setDurationUnit("Month");
        input.setDurationValue("3");
        input.setRowVersion(Base64.getEncoder().encodeToString(rowVer));

        ServiceResult<Integer> result = service.updateMembershipPackage(MANAGER_ACCOUNT_ID, input);
        assertTrue(result.isSuccess());

        boolean hasPriceAudit = recordingAuditLogService.records.stream()
                .anyMatch(r -> "UPDATE_PRICE".equals(r.action) && "Price".equals(r.fieldChanged));
        assertTrue(hasPriceAudit, "Phải ghi nhận AuditLog UPDATE_PRICE khi đổi giá");
    }

    @Test
    @DisplayName("Ngừng bán gói thành công và ghi AuditLog DEACTIVATE")
    void testDeactivatePackage() {
        Package pkg = new Package(3);
        pkg.setPackageName("Gói Ngừng Bán Demo");
        pkg.setPackageType(Package.Type.MEMBERSHIP);
        pkg.setDurationUnit("Month");
        pkg.setDurationValue(6);
        pkg.setPrice(2500000);
        pkg.setActive(true);
        byte[] rowVer = new byte[]{0, 0, 0, 0, 0, 0, 0, 3};
        pkg.setRowVersion(rowVer);
        fakePackageDAO.packages.put(3, pkg);

        ServiceResult<Void> result = service.togglePackageStatus(
                MANAGER_ACCOUNT_ID,
                3,
                false,
                Base64.getEncoder().encodeToString(rowVer),
                "Ngừng kinh doanh để chuyển sang gói khuyến mãi mới"
        );

        assertTrue(result.isSuccess());
        assertFalse(fakePackageDAO.packages.get(3).isActive());
        assertEquals("Đã ngừng bán gói dịch vụ.", result.getMessage());
    }

    @Test
    @DisplayName("Mở bán lại gói bị từ chối nếu tên gói trùng với một gói khác đang bán")
    void testReactivateDuplicateNameFails() {
        // Gói đang active
        fakePackageDAO.activeNames.add("Gói Tập Gym 1 Tháng");

        // Gói đang inactive trùng tên muốn mở lại
        Package inactivePkg = new Package(4);
        inactivePkg.setPackageName("Gói Tập Gym 1 Tháng");
        inactivePkg.setPackageType(Package.Type.MEMBERSHIP);
        inactivePkg.setDurationUnit("Month");
        inactivePkg.setDurationValue(1);
        inactivePkg.setPrice(600000);
        inactivePkg.setActive(false);
        byte[] rowVer = new byte[]{0, 0, 0, 0, 0, 0, 0, 4};
        inactivePkg.setRowVersion(rowVer);
        fakePackageDAO.packages.put(4, inactivePkg);

        ServiceResult<Void> result = service.togglePackageStatus(
                MANAGER_ACCOUNT_ID,
                4,
                true,
                Base64.getEncoder().encodeToString(rowVer),
                "Mở lại gói"
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("đang có một gói khác cùng tên đang bán"));
    }

    // ===== Lớp giả lập (Fakes & Test Doubles) =====

    static class TestablePackageService extends PackageService {
        TestablePackageService(PackageDAO packageDAO, AuditLogService auditLogService) {
            super(packageDAO, auditLogService);
        }

        @Override
        protected Connection getConnection() {
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

    static class FakePackageDAO extends PackageDAO {
        final Map<Integer, Package> packages = new HashMap<>();
        final Set<String> activeNames = new HashSet<>();
        boolean simulateConcurrencyConflict = false;
        int idSequence = 100;

        @Override
        public Package findById(Connection conn, int packageId) {
            return packages.get(packageId);
        }

        @Override
        public Package findById(int packageId) {
            return packages.get(packageId);
        }

        @Override
        public boolean existsActiveByName(Connection conn, String packageName, Integer excludePackageId) {
            if (excludePackageId != null && packages.containsKey(excludePackageId)) {
                Package existing = packages.get(excludePackageId);
                if (existing.getPackageName().equalsIgnoreCase(packageName)) {
                    return false;
                }
            }
            return activeNames.contains(packageName);
        }

        @Override
        public int insert(Connection conn, Package pkg) {
            int newId = ++idSequence;
            packages.put(newId, pkg);
            if (pkg.isActive()) {
                activeNames.add(pkg.getPackageName());
            }
            return newId;
        }

        @Override
        public boolean update(Connection conn, Package pkg) {
            if (simulateConcurrencyConflict) {
                return false;
            }
            packages.put(pkg.getPackageId(), pkg);
            return true;
        }

        @Override
        public boolean updateActiveStatus(Connection conn, int packageId, boolean isActive, byte[] expectedRowVer) {
            if (simulateConcurrencyConflict) {
                return false;
            }
            Package pkg = packages.get(packageId);
            if (pkg != null) {
                pkg.setActive(isActive);
                if (isActive) {
                    activeNames.add(pkg.getPackageName());
                } else {
                    activeNames.remove(pkg.getPackageName());
                }
                return true;
            }
            return false;
        }
    }

    static class RecordingAuditLogService extends AuditLogService {
        static class LogRecord {
            String action;
            String entityName;
            Integer entityId;
            String fieldChanged;
            String oldValue;
            String newValue;
            String reason;
        }

        final List<LogRecord> records = new ArrayList<>();

        @Override
        void logInTransaction(Connection conn, Integer accountId, Role actingRole, String action,
                              String entityName, Integer entityId, String fieldChanged,
                              String oldValue, String newValue, String reason) {
            LogRecord r = new LogRecord();
            r.action = action;
            r.entityName = entityName;
            r.entityId = entityId;
            r.fieldChanged = fieldChanged;
            r.oldValue = oldValue;
            r.newValue = newValue;
            r.reason = reason;
            records.add(r);
        }
    }
}
