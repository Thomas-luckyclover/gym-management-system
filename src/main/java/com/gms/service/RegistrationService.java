package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.Customer;
import com.gms.entity.Person;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.PasswordUtil;
import com.gms.util.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nghiệp vụ đăng ký tài khoản khách hàng trực tuyến (UC-05, UC-IDENT, UC-Claim).
 */
public class RegistrationService {

    private final PersonDAO personDAO;
    private final CustomerDAO customerDAO;
    private final AccountDAO accountDAO;

    public RegistrationService() {
        this(new PersonDAO(), new CustomerDAO(), new AccountDAO());
    }

    public RegistrationService(PersonDAO personDAO, CustomerDAO customerDAO) {
        this(personDAO, customerDAO, new AccountDAO());
    }

    public RegistrationService(PersonDAO personDAO, CustomerDAO customerDAO, AccountDAO accountDAO) {
        this.personDAO = personDAO;
        this.customerDAO = customerDAO;
        this.accountDAO = accountDAO;
    }

    // ===== Kết quả tra số điện thoại =====

    public enum PhoneOutcome {
        NEW_PHONE,
        HAS_ACCOUNT,
        PROFILE_NO_EMAIL,
        PROFILE_EMAIL_MISMATCH,
        PROFILE_CAN_CLAIM
    }

    public static class PhoneCheckResult {
        private final PhoneOutcome outcome;
        private final String maskedEmail;

        public PhoneCheckResult(PhoneOutcome outcome, String maskedEmail) {
            this.outcome = outcome;
            this.maskedEmail = maskedEmail;
        }

        public PhoneOutcome getOutcome() {
            return outcome;
        }

        public String getMaskedEmail() {
            return maskedEmail;
        }
    }

    // ===== 1. Tra số điện thoại (Bước 1) =====

    public PhoneCheckResult checkPhone(String phone) {
        try {
            Person person = personDAO.findByPhone(phone);
            if (person == null) {
                return new PhoneCheckResult(PhoneOutcome.NEW_PHONE, null);
            }

            // CBR-26: Đã có tài khoản -> chặn tạo tài khoản mới
            if (accountDAO.findByPersonId(person.getPersonId()) != null) {
                return new PhoneCheckResult(PhoneOutcome.HAS_ACCOUNT, null);
            }

            // CBR-114: Hồ sơ quầy phải là Self còn hiệu lực
            Customer customer = customerDAO.findByPersonId(person.getPersonId());
            boolean activeSelf = customer != null
                    && "Self".equalsIgnoreCase(customer.getCustomerType())
                    && !"Inactive".equalsIgnoreCase(customer.getStatus());
            if (!activeSelf || ValidationUtil.isNullOrEmpty(person.getEmail())) {
                return new PhoneCheckResult(PhoneOutcome.PROFILE_NO_EMAIL, null);
            }

            // Hồ sơ tại quầy có email -> cho phép kích hoạt (UC-Claim)
            return new PhoneCheckResult(PhoneOutcome.PROFILE_CAN_CLAIM, maskEmail(person.getEmail()));
        } catch (SQLException e) {
            e.printStackTrace();
            return new PhoneCheckResult(PhoneOutcome.NEW_PHONE, null);
        }
    }

    // ===== 2. Validate form Đăng ký mới =====

    public ServiceResult<Void> validateRegistrationForm(String fullName, LocalDate dateOfBirth,
                                                         String phone, String email,
                                                         String password, String confirmPassword,
                                                         String registerMode) {
        String trimmedName = fullName != null ? fullName.trim() : "";
        String trimmedEmail = email != null ? email.trim() : "";
        String trimmedPhone = phone != null ? phone.trim() : "";

        ServiceResult<Void> inputCheck = validateInput(trimmedName, dateOfBirth, trimmedPhone, trimmedEmail, password, confirmPassword);
        if (!inputCheck.isSuccess()) {
            return inputCheck;
        }

        try {
            if (personDAO.existsByEmail(trimmedEmail)) {
                return ServiceResult.failField("email", Messages.MSG12_EMAIL_USED);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        return ServiceResult.ok(null);
    }

    // ===== 3. Kích hoạt hồ sơ có sẵn tại quầy (UC-Claim) =====

    public ServiceResult<Void> validateClaimEmail(String phone, String email) {
        String trimmedEmail = email != null ? email.trim() : "";
        String trimmedPhone = phone != null ? phone.trim() : "";

        if (ValidationUtil.isNullOrEmpty(trimmedEmail)) {
            return ServiceResult.failField("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        }
        if (!ValidationUtil.isValidEmail(trimmedEmail)) {
            return ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL);
        }

        try {
            Person person = personDAO.findByPhone(trimmedPhone);
            if (person == null || person.getEmail() == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }
            if (!person.getEmail().equalsIgnoreCase(trimmedEmail)) {
                return ServiceResult.fail("EMAIL_MISMATCH:" + maskEmail(person.getEmail()));
            }
            return ServiceResult.ok(null);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    public ServiceResult<Void> validateClaimPassword(String password, String confirmPassword) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ValidationUtil.isNullOrEmpty(password)) {
            errors.put("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu"));
        } else if (!ValidationUtil.isValidPassword(password)) {
            errors.put("password", String.format(Messages.MSG66_PASSWORD_RULE,
                    AppConstants.PASSWORD_MIN_LENGTH, AppConstants.PASSWORD_MAX_LENGTH));
        }

        if (ValidationUtil.isNullOrEmpty(confirmPassword)) {
            errors.put("confirmPassword", String.format(Messages.MSG09_REQUIRED, "Nhập lại mật khẩu"));
        } else if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", Messages.MSG47_PASSWORD_MISMATCH);
        }

        return errors.isEmpty() ? ServiceResult.ok(null) : ServiceResult.failFields(errors);
    }

    public ServiceResult<Void> claimAccount(String phone, String password) {
        try {
            Person person = personDAO.findByPhone(phone != null ? phone.trim() : "");
            if (person == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }
            return claimExistingAccount(person.getPersonId(), password);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 4. Thực hiện Đăng ký tài khoản =====

    public ServiceResult<Void> register(String fullName, LocalDate dateOfBirth, String phone,
                                        String email, String password, String confirmPassword) {
        String trimmedName = fullName != null ? fullName.trim() : "";
        String trimmedPhone = phone != null ? phone.trim() : "";
        String trimmedEmail = email != null ? email.trim() : "";

        ServiceResult<Void> validation = validateInput(trimmedName, dateOfBirth, trimmedPhone, trimmedEmail, password, confirmPassword);
        if (!validation.isSuccess()) {
            return validation;
        }

        try {
            Person existingPerson = personDAO.findByPhone(trimmedPhone);
            if (existingPerson != null) {
                return handleExistingPhone(existingPerson, trimmedEmail, password);
            }

            if (personDAO.existsByEmail(trimmedEmail)) {
                return ServiceResult.fail(Messages.MSG12_EMAIL_USED);
            }

            return createNewCustomerTransaction(trimmedName, dateOfBirth, trimmedPhone, trimmedEmail, password);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Hàm hỗ trợ nội bộ =====

    private ServiceResult<Void> handleExistingPhone(Person person, String inputEmail, String password) throws SQLException {
        if (accountDAO.findByPersonId(person.getPersonId()) != null) {
            return ServiceResult.fail(Messages.MSG51_PHONE_HAS_ACCOUNT);
        }

        Customer customer = customerDAO.findByPersonId(person.getPersonId());
        boolean activeSelf = customer != null
                && "Self".equalsIgnoreCase(customer.getCustomerType())
                && !"Inactive".equalsIgnoreCase(customer.getStatus());
        if (!activeSelf || ValidationUtil.isNullOrEmpty(person.getEmail())) {
            return ServiceResult.fail(Messages.MSG53_PROFILE_NO_EMAIL);
        }

        if (!person.getEmail().equalsIgnoreCase(inputEmail)) {
            return ServiceResult.fail(String.format(Messages.MSG52_PHONE_DIFFERENT_EMAIL, maskEmail(person.getEmail())));
        }

        return claimExistingAccount(person.getPersonId(), password);
    }

    private ServiceResult<Void> claimExistingAccount(int personId, String rawPassword) {
        String hash = PasswordUtil.hashPassword(rawPassword);
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // CBR-01: Chỉ tạo Account, không đổi trạng thái Customer
                accountDAO.insert(conn, personId, hash);
                conn.commit();
                return ServiceResult.ok(null, Messages.MSG54_ACCOUNT_CREATED);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    private ServiceResult<Void> createNewCustomerTransaction(String fullName, LocalDate dob, String phone,
                                                             String email, String rawPassword) {
        String hash = PasswordUtil.hashPassword(rawPassword);
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Person person = new Person(fullName, dob, phone, email);
                person.setReleased(false);
                int personId = personDAO.insert(conn, person);

                String customerCode = customerDAO.generateNextCustomerCode(conn);
                Customer customer = new Customer(personId, customerCode, "Self", "New");
                customerDAO.insert(conn, customer);

                accountDAO.insert(conn, personId, hash);
                conn.commit();
                return ServiceResult.ok(null, Messages.MSG54_ACCOUNT_CREATED);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    private ServiceResult<Void> validateInput(String fullName, LocalDate dob, String phone,
                                              String email, String password, String confirmPassword) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ValidationUtil.isNullOrEmpty(fullName)) {
            errors.put("fullName", String.format(Messages.MSG09_REQUIRED, "Họ và tên"));
        } else if (!ValidationUtil.isLengthValid(fullName, AppConstants.FULL_NAME_MIN_LENGTH, AppConstants.FULL_NAME_MAX_LENGTH)) {
            errors.put("fullName", String.format(Messages.MSG72_FULL_NAME_LENGTH,
                    AppConstants.FULL_NAME_MIN_LENGTH, AppConstants.FULL_NAME_MAX_LENGTH));
        } else if (!ValidationUtil.isValidFullName(fullName)) {
            errors.put("fullName", Messages.MSG67_FULL_NAME_RULE);
        }

        if (dob == null) {
            errors.put("dateOfBirth", String.format(Messages.MSG09_REQUIRED, "Ngày sinh"));
        } else if (dob.isAfter(DateTimeUtil.today())) {
            errors.put("dateOfBirth", Messages.MSG15_DOB_IN_FUTURE);
        } else if (DateTimeUtil.calculateAge(dob, DateTimeUtil.today()) > 100) {
            errors.put("dateOfBirth", String.format(Messages.MSG69_DOB_TOO_OLD, 100));
        }

        if (ValidationUtil.isNullOrEmpty(phone)) {
            errors.put("phone", String.format(Messages.MSG09_REQUIRED, "Số điện thoại"));
        } else if (!ValidationUtil.isValidPhone(phone)) {
            errors.put("phone", Messages.MSG13_INVALID_PHONE);
        }

        if (ValidationUtil.isNullOrEmpty(email)) {
            errors.put("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        } else if (!ValidationUtil.isValidEmail(email)) {
            errors.put("email", Messages.MSG11_INVALID_EMAIL);
        } else if (email.length() > AppConstants.EMAIL_MAX_LENGTH) {
            errors.put("email", String.format(Messages.MSG10_MAX_LENGTH, AppConstants.EMAIL_MAX_LENGTH));
        }

        if (ValidationUtil.isNullOrEmpty(password)) {
            errors.put("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu"));
        } else if (!ValidationUtil.isValidPassword(password)) {
            errors.put("password", String.format(Messages.MSG66_PASSWORD_RULE,
                    AppConstants.PASSWORD_MIN_LENGTH, AppConstants.PASSWORD_MAX_LENGTH));
        }

        if (ValidationUtil.isNullOrEmpty(confirmPassword)) {
            errors.put("confirmPassword", String.format(Messages.MSG09_REQUIRED, "Nhập lại mật khẩu"));
        } else if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", Messages.MSG47_PASSWORD_MISMATCH);
        }

        return errors.isEmpty() ? ServiceResult.ok(null) : ServiceResult.failFields(errors);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        return (name.length() <= 2 ? name.charAt(0) : name.substring(0, 2)) + "***" + domain;
    }
}
