package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.AccountSettingsView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Person;
import com.gms.entity.VerificationCode;
import com.gms.integration.EmailGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Account Settings Profile operations:
 * - Avatar 1-letter fallback (initials) logic
 * - Avatar removal (safe file cleanup, DB update, audit logging)
 * - Avatar update validations (5 MB size, MIME/extension)
 * - Email and Phone change business rules (same email/phone rejection, format validation)
 * - VerificationCodeService purpose label
 */
class AccountSettingsProfileTest {

    // ==========================================
    // 1. Fallback 1-letter initials tests (từ cuối cùng trong họ và tên)
    // ==========================================

    @Test
    @DisplayName("Avatar fallback: Chỉ lấy 1 chữ cái đầu của tên (từ cuối cùng), giữ nguyên dấu tiếng Việt")
    void testInitials_VietnameseFullName() {
        AccountSettingsView view1 = createViewWithName("Nguyễn Hải Đăng");
        assertEquals("Đ", view1.getInitials());

        AccountSettingsView view2 = createViewWithName("Nguyễn Đăng Vũ");
        assertEquals("V", view2.getInitials());

        AccountSettingsView view3 = createViewWithName("Trần Minh Anh");
        assertEquals("A", view3.getInitials());

        AccountSettingsView view4 = createViewWithName("Lê Hoàng Nam");
        assertEquals("N", view4.getInitials());

        AccountSettingsView view5 = createViewWithName("Trần Hưng");
        assertEquals("H", view5.getInitials());
    }

    @Test
    @DisplayName("Avatar fallback: Tên 1 từ, tên có nhiều khoảng trắng, null hoặc rỗng")
    void testInitials_EdgeCases() {
        AccountSettingsView viewSingle = createViewWithName("Vũ");
        assertEquals("V", viewSingle.getInitials());

        AccountSettingsView viewAccentedSingle = createViewWithName("Đăng");
        assertEquals("Đ", viewAccentedSingle.getInitials());

        AccountSettingsView viewMultipleSpaces = createViewWithName("   Nguyễn   Hải   Đăng   ");
        assertEquals("Đ", viewMultipleSpaces.getInitials());

        AccountSettingsView viewNull = createViewWithName(null);
        assertEquals("—", viewNull.getInitials());

        AccountSettingsView viewEmpty = createViewWithName("");
        assertEquals("—", viewEmpty.getInitials());

        AccountSettingsView viewBlank = createViewWithName("   ");
        assertEquals("—", viewBlank.getInitials());
    }

    private AccountSettingsView createViewWithName(String name) {
        return new AccountSettingsView(
                1, 10, name, LocalDate.of(1995, 1, 1),
                "0912345678", "test@gms.vn", null,
                Role.ADMIN, "NV-0001", LocalDate.now(), "Working"
        );
    }

    // ==========================================
    // 2. VerificationCodeService purpose label
    // ==========================================

    @Test
    @DisplayName("VerificationCodeService: Purpose labels for email and phone change")
    void testVerificationCodePurposeLabels() {
        VerificationCodeService service = new VerificationCodeService();
        assertEquals("đổi số điện thoại", service.getPurposeLabel(VerificationCode.Purpose.PHONE_CHANGE));
        assertEquals("đổi email", service.getPurposeLabel(VerificationCode.Purpose.EMAIL_CHANGE));
        assertEquals("khôi phục mật khẩu", service.getPurposeLabel(VerificationCode.Purpose.RECOVERY));
    }

    // ==========================================
    // 3. ContactChangeService validations
    // ==========================================

    @Test
    @DisplayName("Email Change: Reject same email as current email")
    void testRequestEmailChange_SameEmailFails() {
        StubAccountDAO stubAccountDAO = new StubAccountDAO();
        StubPersonDAO stubPersonDAO = new StubPersonDAO();

        Person person = new Person();
        person.setPersonId(10);
        person.setEmail("current@gms.vn");
        person.setPhone("0912345678");
        stubPersonDAO.savePerson(person);

        Account account = new Account(1, 10, "hash", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        stubAccountDAO.saveAccount(account);

        ContactChangeService service = new ContactChangeService(
                stubAccountDAO, stubPersonDAO, new VerificationCodeService(), new EmailGateway()
        );

        ServiceResult<Void> result = service.requestEmailChange(1, "current@gms.vn");
        assertFalse(result.isSuccess());
        assertEquals("Email mới phải khác email hiện tại.", result.getFieldErrors().get("email"));

        // Case-insensitive check
        ServiceResult<Void> resultCase = service.requestEmailChange(1, "CURRENT@gms.vn");
        assertFalse(resultCase.isSuccess());
        assertEquals("Email mới phải khác email hiện tại.", resultCase.getFieldErrors().get("email"));
    }

    @Test
    @DisplayName("Email Change: Reject empty or invalid format email")
    void testRequestEmailChange_InvalidFormat() {
        StubAccountDAO stubAccountDAO = new StubAccountDAO();
        StubPersonDAO stubPersonDAO = new StubPersonDAO();
        ContactChangeService service = new ContactChangeService(
                stubAccountDAO, stubPersonDAO, new VerificationCodeService(), new EmailGateway()
        );

        ServiceResult<Void> emptyResult = service.requestEmailChange(1, "");
        assertFalse(emptyResult.isSuccess());
        assertTrue(emptyResult.getFieldErrors().containsKey("email"));

        ServiceResult<Void> invalidResult = service.requestEmailChange(1, "not-an-email");
        assertFalse(invalidResult.isSuccess());
        assertEquals(Messages.MSG11_INVALID_EMAIL, invalidResult.getFieldErrors().get("email"));
    }

    @Test
    @DisplayName("Phone Change: Reject same phone as current phone")
    void testRequestPhoneChange_SamePhoneFails() {
        StubAccountDAO stubAccountDAO = new StubAccountDAO();
        StubPersonDAO stubPersonDAO = new StubPersonDAO();

        Person person = new Person();
        person.setPersonId(10);
        person.setEmail("user@gms.vn");
        person.setPhone("0912345678");
        stubPersonDAO.savePerson(person);

        Account account = new Account(1, 10, "hash", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        stubAccountDAO.saveAccount(account);

        ContactChangeService service = new ContactChangeService(
                stubAccountDAO, stubPersonDAO, new VerificationCodeService(), new EmailGateway()
        );

        ServiceResult<Void> result = service.requestPhoneChange(1, "user@gms.vn", "0912345678");
        assertFalse(result.isSuccess());
        assertEquals("Số điện thoại mới phải khác số điện thoại hiện tại.", result.getFieldErrors().get("phone"));
    }

    @Test
    @DisplayName("Phone Change: Reject empty, invalid format, or invalid network prefix")
    void testRequestPhoneChange_InvalidPhone() {
        StubAccountDAO stubAccountDAO = new StubAccountDAO();
        StubPersonDAO stubPersonDAO = new StubPersonDAO();
        ContactChangeService service = new ContactChangeService(
                stubAccountDAO, stubPersonDAO, new VerificationCodeService(), new EmailGateway()
        );

        ServiceResult<Void> emptyResult = service.requestPhoneChange(1, "user@gms.vn", "");
        assertFalse(emptyResult.isSuccess());
        assertTrue(emptyResult.getFieldErrors().containsKey("phone"));

        ServiceResult<Void> invalidFormat = service.requestPhoneChange(1, "user@gms.vn", "12345");
        assertFalse(invalidFormat.isSuccess());
        assertEquals(Messages.MSG13_INVALID_PHONE, invalidFormat.getFieldErrors().get("phone"));

        ServiceResult<Void> invalidLength = service.requestPhoneChange(1, "user@gms.vn", "09123456789"); // 11 digits
        assertFalse(invalidLength.isSuccess());
        assertEquals(Messages.MSG13_INVALID_PHONE, invalidLength.getFieldErrors().get("phone"));

        ServiceResult<Void> invalidChars = service.requestPhoneChange(1, "user@gms.vn", "091234abcd");
        assertFalse(invalidChars.isSuccess());
        assertEquals(Messages.MSG13_INVALID_PHONE, invalidChars.getFieldErrors().get("phone"));
    }

    @Test
    @DisplayName("Phone Change: Account without email cannot receive OTP")
    void testRequestPhoneChange_AccountWithoutEmail() {
        StubAccountDAO stubAccountDAO = new StubAccountDAO();
        StubPersonDAO stubPersonDAO = new StubPersonDAO();
        ContactChangeService service = new ContactChangeService(
                stubAccountDAO, stubPersonDAO, new VerificationCodeService(), new EmailGateway()
        );

        ServiceResult<Void> result = service.requestPhoneChange(1, "", "0987654321");
        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG53_PROFILE_NO_EMAIL, result.getMessage());
    }

    // ==========================================
    // 4. AccountSettingsService: Remove Avatar
    // ==========================================

    @Test
    @DisplayName("Remove Avatar: Account not found returns error")
    void testRemoveAvatar_AccountNotFound(@TempDir Path tempDir) {
        StubAccountDAO stubAccountDAO = new StubAccountDAO();
        StubPersonDAO stubPersonDAO = new StubPersonDAO();
        AccountSettingsService service = new AccountSettingsService(
                stubAccountDAO, new CustomerDAO(), stubPersonDAO, new EmployeeDAO(),
                new StubAuditLogService(), new SystemConfigService()
        );

        ServiceResult<Void> result = service.removeAvatar(999, tempDir);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Không tìm thấy"));
    }

    @Test
    @DisplayName("Remove Avatar: Account with no avatar returns failure")
    void testRemoveAvatar_AlreadyNull(@TempDir Path tempDir) {
        StubAccountDAO stubAccountDAO = new StubAccountDAO();
        StubPersonDAO stubPersonDAO = new StubPersonDAO();

        Person person = new Person();
        person.setPersonId(20);
        person.setAvatar(null);
        stubPersonDAO.savePerson(person);

        Account account = new Account(2, 20, "hash", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        stubAccountDAO.saveAccount(account);

        AccountSettingsService service = new AccountSettingsService(
                stubAccountDAO, new CustomerDAO(), stubPersonDAO, new EmployeeDAO(),
                new StubAuditLogService(), new SystemConfigService()
        );

        ServiceResult<Void> result = service.removeAvatar(2, tempDir);
        assertFalse(result.isSuccess());
        assertEquals("Tài khoản chưa có ảnh đại diện.", result.getMessage());
    }

    @Test
    @DisplayName("Remove Avatar: Existing avatar removed from DB and physical file deleted")
    void testRemoveAvatar_SuccessWithPhysicalFile(@TempDir Path tempDir) throws IOException {
        StubAccountDAO stubAccountDAO = new StubAccountDAO();
        StubPersonDAO stubPersonDAO = new StubPersonDAO();
        StubAuditLogService stubAuditLog = new StubAuditLogService();

        // Create avatar folder structure inside tempDir
        Path avatarDir = tempDir.resolve("avatar");
        Files.createDirectories(avatarDir);
        Path testAvatarFile = avatarDir.resolve("avatar_20_12345.jpg");
        Files.writeString(testAvatarFile, "fake-image-content");
        assertTrue(Files.exists(testAvatarFile));

        Person person = new Person();
        person.setPersonId(20);
        person.setAvatar("/uploads/avatar/avatar_20_12345.jpg");
        stubPersonDAO.savePerson(person);

        Account account = new Account(2, 20, "hash", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        stubAccountDAO.saveAccount(account);

        AccountSettingsService service = new AccountSettingsService(
                stubAccountDAO, new CustomerDAO(), stubPersonDAO, new EmployeeDAO(),
                stubAuditLog, new SystemConfigService()
        );

        ServiceResult<Void> result = service.removeAvatar(2, avatarDir);
        assertTrue(result.isSuccess());
        assertEquals(Messages.MSG58_AVATAR_REMOVED, result.getMessage());

        // Verify Person.Avatar is updated to null in DB
        assertNull(stubPersonDAO.findById(20).getAvatar());

        // Verify physical file was safely deleted
        assertFalse(Files.exists(testAvatarFile), "Old avatar file must be deleted");

        // Verify Audit Log was recorded
        assertTrue(stubAuditLog.loggedActions.contains("Xóa ảnh đại diện cá nhân"));
    }

    // ==========================================
    // 5. AccountSettingsService: Update Avatar validations
    // ==========================================

    @Test
    @DisplayName("Update Avatar: Rejects oversized file (> 5 MB)")
    void testUpdateAvatar_OversizedRejected(@TempDir Path tempDir) {
        AccountSettingsService service = new AccountSettingsService();
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        long oversized = AppConstants.IMAGE_MAX_BYTES + 1; // 5 MB + 1 byte
        ServiceResult<Void> result = service.updateAvatar(
                1, "image/jpeg", oversized, stream, tempDir
        );

        assertFalse(result.isSuccess());
        long maxMb = AppConstants.IMAGE_MAX_BYTES / (1024L * 1024L);
        assertEquals(String.format(Messages.MSG20_INVALID_IMAGE, maxMb + " MB"), result.getFieldErrors().get("avatarFile"));
    }

    @Test
    @DisplayName("Update Avatar: Rejects invalid MIME type or extension")
    void testUpdateAvatar_InvalidTypeRejected(@TempDir Path tempDir) {
        AccountSettingsService service = new AccountSettingsService();
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        // Disallowed gif
        ServiceResult<Void> resultGif = service.updateAvatar(
                1, "image/gif", 100, stream, tempDir
        );
        assertFalse(resultGif.isSuccess());
        long maxMb = AppConstants.IMAGE_MAX_BYTES / (1024L * 1024L);
        assertEquals(String.format(Messages.MSG20_INVALID_IMAGE, maxMb + " MB"), resultGif.getFieldErrors().get("avatarFile"));

        // Disallowed pdf
        ServiceResult<Void> resultPdf = service.updateAvatar(
                1, "application/pdf", 100, stream, tempDir
        );
        assertFalse(resultPdf.isSuccess());
        assertEquals(String.format(Messages.MSG20_INVALID_IMAGE, maxMb + " MB"), resultPdf.getFieldErrors().get("avatarFile"));
    }

    // ==========================================
    // In-memory Test Stubs
    // ==========================================

    static class StubAccountDAO extends AccountDAO {
        private final Map<Integer, Account> store = new HashMap<>();

        void saveAccount(Account a) {
            store.put(a.getAccountId(), a);
        }

        @Override
        public Account findById(int accountId) {
            return store.get(accountId);
        }
    }

    static class StubPersonDAO extends PersonDAO {
        private final Map<Integer, Person> store = new HashMap<>();

        void savePerson(Person p) {
            store.put(p.getPersonId(), p);
        }

        @Override
        public Person findById(int personId) {
            return store.get(personId);
        }

        @Override
        public boolean updateAvatar(int personId, String avatarPath) {
            Person p = store.get(personId);
            if (p != null) {
                p.setAvatar(avatarPath);
                return true;
            }
            return false;
        }

        @Override
        public boolean existsByEmail(String email) {
            return store.values().stream().anyMatch(p -> email != null && email.equalsIgnoreCase(p.getEmail()));
        }

        @Override
        public boolean existsByPhone(String phone) {
            return store.values().stream().anyMatch(p -> phone != null && phone.equals(p.getPhone()));
        }
    }

    static class StubAuditLogService extends AuditLogService {
        final List<String> loggedActions = new ArrayList<>();

        @Override
        public ServiceResult<Void> log(Integer accountId, String action, String entityName, Integer entityId,
                                       String fieldChanged, String oldValue, String newValue, String reason) {
            loggedActions.add(action);
            return ServiceResult.ok(null);
        }
    }
}
