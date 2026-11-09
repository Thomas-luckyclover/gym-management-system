package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationServiceTest {

    private FakePersonDAO fakePersonDAO;
    private FakeCustomerDAO fakeCustomerDAO;
    private FakeAccountDAO fakeAccountDAO;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        fakePersonDAO = new FakePersonDAO();
        fakeCustomerDAO = new FakeCustomerDAO();
        fakeAccountDAO = new FakeAccountDAO();
        registrationService = new RegistrationService(fakePersonDAO, fakeCustomerDAO, fakeAccountDAO);
    }

    // ===== 1. Kiểm thử checkPhone (Bước 1) =====

    @Test
    @DisplayName("checkPhone: SĐT chưa có trong DB -> NEW_PHONE")
    void checkPhone_NewPhone_ReturnsNewPhoneOutcome() {
        RegistrationService.PhoneCheckResult result = registrationService.checkPhone("0912345678");

        assertEquals(RegistrationService.PhoneOutcome.NEW_PHONE, result.getOutcome());
        assertNull(result.getMaskedEmail());
    }

    @Test
    @DisplayName("checkPhone: SĐT đã có tài khoản Account -> HAS_ACCOUNT (CBR-26)")
    void checkPhone_HasAccount_ReturnsHasAccountOutcome() {
        Person person = new Person("Nguyễn Văn A", LocalDate.of(1995, 5, 20), "0912345678", "a@example.com");
        person.setPersonId(1);
        fakePersonDAO.addPerson(person);

        Account account = new Account(10, 1, "hash", Account.Status.ACTIVE, 0, null, null, LocalDateTime.now());
        fakeAccountDAO.addAccount(account);

        RegistrationService.PhoneCheckResult result = registrationService.checkPhone("0912345678");

        assertEquals(RegistrationService.PhoneOutcome.HAS_ACCOUNT, result.getOutcome());
    }

    @Test
    @DisplayName("checkPhone: SĐT có hồ sơ quầy nhưng chưa có email -> PROFILE_NO_EMAIL (CBR-27)")
    void checkPhone_ProfileWithoutEmail_ReturnsProfileNoEmail() {
        Person person = new Person("Trần Văn B", LocalDate.of(1998, 1, 10), "0987654321", null);
        person.setPersonId(2);
        fakePersonDAO.addPerson(person);

        Customer customer = new Customer(20, 2, "KH-0002", null, "Self", null, null, null, null, "New", LocalDateTime.now(), null);
        fakeCustomerDAO.addCustomer(customer);

        RegistrationService.PhoneCheckResult result = registrationService.checkPhone("0987654321");

        assertEquals(RegistrationService.PhoneOutcome.PROFILE_NO_EMAIL, result.getOutcome());
    }

    @Test
    @DisplayName("checkPhone: SĐT có hồ sơ quầy có email và chưa có tài khoản -> PROFILE_CAN_CLAIM với email được che (UC-Claim)")
    void checkPhone_CanClaim_ReturnsCanClaimWithMaskedEmail() {
        Person person = new Person("Lê Thị C", LocalDate.of(2000, 3, 15), "0901234567", "lethic@gmail.com");
        person.setPersonId(3);
        fakePersonDAO.addPerson(person);

        Customer customer = new Customer(30, 3, "KH-0003", null, "Self", null, null, null, null, "New", LocalDateTime.now(), null);
        fakeCustomerDAO.addCustomer(customer);

        RegistrationService.PhoneCheckResult result = registrationService.checkPhone("0901234567");

        assertEquals(RegistrationService.PhoneOutcome.PROFILE_CAN_CLAIM, result.getOutcome());
        assertEquals("le***@gmail.com", result.getMaskedEmail());
    }

    // ===== 2. Kiểm thử validateRegistrationForm (Đăng ký mới) =====

    @Test
    @DisplayName("validateRegistrationForm: Thông tin hợp lệ -> Thành công")
    void validateRegistrationForm_ValidData_Success() {
        ServiceResult<Void> result = registrationService.validateRegistrationForm(
                "Trần Văn D", LocalDate.of(1996, 7, 20), "0911223344", "tranvand@example.com",
                "Password123", "Password123", "new");

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("validateRegistrationForm: Email đã được sử dụng -> Báo lỗi MSG12")
    void validateRegistrationForm_EmailExisted_Fails() {
        fakePersonDAO.setExistingEmail("used@example.com");

        ServiceResult<Void> result = registrationService.validateRegistrationForm(
                "Trần Văn D", LocalDate.of(1996, 7, 20), "0911223344", "used@example.com",
                "Password123", "Password123", "new");

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG12_EMAIL_USED, result.getFieldErrors().get("email"));
    }

    @Test
    @DisplayName("validateRegistrationForm: Mật khẩu xác nhận không khớp -> Báo lỗi MSG47")
    void validateRegistrationForm_PasswordMismatch_Fails() {
        ServiceResult<Void> result = registrationService.validateRegistrationForm(
                "Trần Văn D", LocalDate.of(1996, 7, 20), "0911223344", "test@example.com",
                "Password123", "Mismatch456", "new");

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG47_PASSWORD_MISMATCH, result.getFieldErrors().get("confirmPassword"));
    }

    @Test
    @DisplayName("validateRegistrationForm: Ngày sinh ở tương lai -> Báo lỗi MSG15")
    void validateRegistrationForm_DobInFuture_Fails() {
        LocalDate futureDob = LocalDate.now().plusDays(1);
        ServiceResult<Void> result = registrationService.validateRegistrationForm(
                "Trần Văn D", futureDob, "0911223344", "test@example.com",
                "Password123", "Password123", "new");

        assertFalse(result.isSuccess());
        assertEquals(Messages.MSG15_DOB_IN_FUTURE, result.getFieldErrors().get("dateOfBirth"));
    }

    // ===== 3. Kiểm thử nhánh Claim (validateClaimEmail, validateClaimPassword) =====

    @Test
    @DisplayName("validateClaimEmail: Email trùng khớp với hồ sơ quầy -> Thành công")
    void validateClaimEmail_Matches_Success() {
        Person person = new Person("Phạm Văn E", LocalDate.of(1994, 2, 10), "0933445566", "phamvane@gmail.com");
        person.setPersonId(4);
        fakePersonDAO.addPerson(person);

        ServiceResult<Void> result = registrationService.validateClaimEmail("0933445566", "phamvane@gmail.com");

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("validateClaimEmail: Email không khớp hồ sơ -> Báo lỗi EMAIL_MISMATCH kèm email đã che (CBR-25)")
    void validateClaimEmail_Mismatch_ReturnsEmailMismatchWithMasked() {
        Person person = new Person("Phạm Văn E", LocalDate.of(1994, 2, 10), "0933445566", "phamvane@gmail.com");
        person.setPersonId(4);
        fakePersonDAO.addPerson(person);

        ServiceResult<Void> result = registrationService.validateClaimEmail("0933445566", "wrong@gmail.com");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().startsWith("EMAIL_MISMATCH:"));
        assertTrue(result.getMessage().contains("ph***@gmail.com"));
    }

    @Test
    @DisplayName("validateClaimPassword: Mật khẩu hợp lệ -> Thành công")
    void validateClaimPassword_Valid_Success() {
        ServiceResult<Void> result = registrationService.validateClaimPassword("SecurePass2026", "SecurePass2026");

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("validateClaimPassword: Mật khẩu quá ngắn (< 8 ký tự) -> Báo lỗi MSG66")
    void validateClaimPassword_TooShort_Fails() {
        ServiceResult<Void> result = registrationService.validateClaimPassword("Short1", "Short1");

        assertFalse(result.isSuccess());
        assertTrue(result.getFieldErrors().containsKey("password"));
    }

    // ===== Fake DAOs hỗ trợ Unit Test =====

    private static class FakePersonDAO extends PersonDAO {
        private final Map<String, Person> personsByPhone = new HashMap<>();
        private String existingEmail;

        void addPerson(Person p) {
            personsByPhone.put(p.getPhone(), p);
        }

        void setExistingEmail(String email) {
            this.existingEmail = email;
        }

        @Override
        public Person findByPhone(String phone) {
            return personsByPhone.get(phone);
        }

        @Override
        public boolean existsByEmail(String email) {
            return email != null && email.equalsIgnoreCase(existingEmail);
        }

        @Override
        public int insert(Connection conn, Person person) {
            return 100;
        }
    }

    private static class FakeCustomerDAO extends CustomerDAO {
        private final Map<Integer, Customer> customersByPersonId = new HashMap<>();

        void addCustomer(Customer c) {
            customersByPersonId.put(c.getPersonId(), c);
        }

        @Override
        public Customer findByPersonId(int personId) {
            return customersByPersonId.get(personId);
        }

        @Override
        public String generateNextCustomerCode(Connection conn) {
            return "KH-0100";
        }

        @Override
        public int insert(Connection conn, Customer customer) {
            return 200;
        }
    }

    private static class FakeAccountDAO extends AccountDAO {
        private final Map<Integer, Account> accountsByPersonId = new HashMap<>();

        void addAccount(Account a) {
            accountsByPersonId.put(a.getPersonId(), a);
        }

        @Override
        public Account findByPersonId(int personId) {
            return accountsByPersonId.get(personId);
        }

        @Override
        public int insert(Connection conn, int personId, String passwordHash) {
            return 300;
        }
    }
}
