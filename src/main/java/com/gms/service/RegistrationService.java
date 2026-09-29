package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Xử lý nghiệp vụ đăng ký tài khoản khách hàng trực tuyến (UC-05, UC-IDENT, UC-Claim).
 */
public class RegistrationService {

    private final PersonDAO personDAO;
    private final CustomerDAO customerDAO;

    public RegistrationService() {
        this.personDAO = new PersonDAO();
        this.customerDAO = new CustomerDAO();
    }

    public RegistrationService(PersonDAO personDAO, CustomerDAO customerDAO) {
        this.personDAO = personDAO;
        this.customerDAO = customerDAO;
    }

    // ===== 0. Kết quả tra số điện thoại (dùng bởi RegisterServlet step "phone") =====

    /**
     * Các kết quả có thể khi Servlet tra SĐT bước 1.
     */
    public enum PhoneOutcome {
        /** Số hoàn toàn mới, cho đăng ký bình thường. */
        NEW_PHONE,
        /** Hồ sơ Self đang hoạt động đã có tài khoản (CBR-26). */
        HAS_ACCOUNT,
        /** Hồ sơ Self chưa có email (CBR-27). */
        PROFILE_NO_EMAIL,
        /** Hồ sơ Self có email, khách cần nhập đúng email để kích hoạt (CBR-25 / UC-Claim). */
        PROFILE_EMAIL_MISMATCH,
        /** Hồ sơ Self có email, sẵn sàng kích hoạt (UC-Claim). */
        PROFILE_CAN_CLAIM
    }

    /** Kết quả trả về từ checkPhone(). */
    public static class PhoneCheckResult {

        private final PhoneOutcome outcome;

        /** Email đã che — chỉ có giá trị khi outcome = PROFILE_EMAIL_MISMATCH. */
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

    // ===== 1. Tra số điện thoại (bước 1 đăng ký) =====

    /**
     * Tra SĐT trong DB và trả về kết quả để Servlet chọn màn hiển thị tiếp theo.
     *
     * @param phone SĐT đã qua validate định dạng (10 số, bắt đầu 0)
     * @return PhoneCheckResult chứa outcome và maskedEmail nếu cần
     */
    public PhoneCheckResult checkPhone(String phone) {
        try {
            Person existingPerson = personDAO.findByPhone(phone);

            // Số chưa có hồ sơ hoặc hồ sơ Inactive (đã nhả) → đăng ký bình thường (CBR-23)
            if (existingPerson == null) {
                return new PhoneCheckResult(PhoneOutcome.NEW_PHONE, null);
            }

            // Hồ sơ đang hoạt động — kiểm tra đã có tài khoản chưa
            boolean hasAccount = hasAccountByPersonId(existingPerson.getPersonId());
            if (hasAccount) {
                return new PhoneCheckResult(PhoneOutcome.HAS_ACCOUNT, null);
            }

            // Hồ sơ chưa có tài khoản — kiểm tra có email không
            String existingEmail = existingPerson.getEmail();
            boolean hasNoEmail = ValidationUtil.isNullOrEmpty(existingEmail);
            if (hasNoEmail) {
                return new PhoneCheckResult(PhoneOutcome.PROFILE_NO_EMAIL, null);
            }

            // Hồ sơ có email → Servlet sẽ hỏi khách nhập email; nếu khớp sẽ kích hoạt (UC-Claim)
            // Ở đây chỉ báo "có thể claim"; Servlet sẽ cần email khách nhập ở bước form mới biết
            // khớp hay không — trả PROFILE_CAN_CLAIM (cũng che email sẵn cho màn thông báo sau)
            return new PhoneCheckResult(PhoneOutcome.PROFILE_CAN_CLAIM, maskEmail(existingEmail));

        } catch (SQLException e) {
            e.printStackTrace();
            // Lỗi DB → coi như số mới, để bước form validate lại sau
            return new PhoneCheckResult(PhoneOutcome.NEW_PHONE, null);
        }
    }

    // ===== 2. Validate form đăng ký MỚI trước khi gửi OTP =====

    /**
     * Kiểm tra dữ liệu form đăng ký MỚI trước khi gửi OTP.
     * Chỉ dùng cho nhánh đăng ký mới (registerMode = "new").
     *
     * @return ServiceResult.ok nếu hợp lệ; fail kèm fieldErrors nếu không
     */
    public ServiceResult<Void> validateRegistrationForm(String fullName, LocalDate dateOfBirth,
                                                         String phone, String email,
                                                         String password, String confirmPassword,
                                                         String registerMode) {
        // Chuẩn hóa
        String trimmedFullName = fullName != null ? fullName.trim() : "";
        String trimmedEmail = email != null ? email.trim() : "";
        String trimmedPhone = phone != null ? phone.trim() : "";

        // Validate cơ bản toàn bộ các trường theo GBR-17
        ServiceResult<Void> inputCheck = validateInput(
                trimmedFullName, dateOfBirth, trimmedPhone, trimmedEmail, password, confirmPassword);
        if (!inputCheck.isSuccess()) {
            return inputCheck;
        }

        // Kiểm tra email chưa được dùng (DBR-23)
        try {
            boolean isEmailExisted = personDAO.existsByEmail(trimmedEmail);
            if (isEmailExisted) {
                return ServiceResult.failField("email", Messages.MSG12_EMAIL_USED);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        return ServiceResult.ok(null);
    }

    // ===== 2b. Validate email cho nhánh Claim (UC-Claim) =====

    /**
     * So khớp email khách nhập với email trên hồ sơ quầy (CBR-25).
     * Chỉ validate 1 field email — không hỏi họ tên, ngày sinh, mật khẩu.
     *
     * @param phone SĐT đã tra ở bước 1
     * @param email email khách nhập
     * @return ok nếu khớp; fail kèm "EMAIL_MISMATCH:maskedEmail" nếu không khớp
     */
    public ServiceResult<Void> validateClaimEmail(String phone, String email) {
        String trimmedEmail = email != null ? email.trim() : "";
        String trimmedPhone = phone != null ? phone.trim() : "";

        // Validate định dạng email
        if (ValidationUtil.isNullOrEmpty(trimmedEmail)) {
            return ServiceResult.failField("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        }
        if (!ValidationUtil.isValidEmail(trimmedEmail)) {
            return ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL);
        }

        // So khớp với email trên hồ sơ (CBR-25)
        try {
            Person existingPerson = personDAO.findByPhone(trimmedPhone);
            if (existingPerson == null || existingPerson.getEmail() == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }
            if (!existingPerson.getEmail().equalsIgnoreCase(trimmedEmail)) {
                String masked = maskEmail(existingPerson.getEmail());
                return ServiceResult.fail("EMAIL_MISMATCH:" + masked);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        return ServiceResult.ok(null);
    }

    // ===== 2c. Validate mật khẩu cho nhánh Claim =====

    /**
     * Validate mật khẩu cho bước cuối của nhánh Claim (sau khi OTP đã đúng).
     *
     * @return ok nếu hợp lệ; fail kèm fieldErrors nếu không
     */
    public ServiceResult<Void> validateClaimPassword(String password, String confirmPassword) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ValidationUtil.isNullOrEmpty(password)) {
            errors.put("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu"));
        } else if (!ValidationUtil.isValidPassword(password)) {
            errors.put("password", String.format(Messages.MSG66_PASSWORD_RULE, AppConstants.PASSWORD_MIN_LENGTH, AppConstants.PASSWORD_MAX_LENGTH));
        }

        if (ValidationUtil.isNullOrEmpty(confirmPassword)) {
            errors.put("confirmPassword", String.format(Messages.MSG09_REQUIRED, "Nhập lại mật khẩu"));
        } else if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", Messages.MSG47_PASSWORD_MISMATCH);
        }

        if (!errors.isEmpty()) {
            return ServiceResult.failFields(errors);
        }
        return ServiceResult.ok(null);
    }

    // ===== 2d. Tạo tài khoản cho nhánh Claim (public, Servlet gọi sau OTP đúng) =====

    /**
     * Tạo tài khoản gắn vào hồ sơ có sẵn (UC-Claim).
     * KHÔNG đổi Customer.status (CBR-01).
     *
     * @param phone    SĐT đã tra ở bước 1
     * @param password mật khẩu thô
     * @return ok nếu thành công
     */
    public ServiceResult<Void> claimAccount(String phone, String password) {
        String trimmedPhone = phone != null ? phone.trim() : "";
        try {
            Person existingPerson = personDAO.findByPhone(trimmedPhone);
            if (existingPerson == null) {
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }
            return claimExistingAccount(existingPerson.getPersonId(), password);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Nghiệp vụ Đăng ký =====

    /**
     * Xử lý đăng ký tài khoản khách hàng mới hoặc kích hoạt hồ sơ có sẵn (UC-Claim).
     *
     * @param fullName họ và tên
     * @param dateOfBirth ngày sinh
     * @param phone số điện thoại 10 số
     * @param email địa chỉ email
     * @param password mật khẩu thô
     * @param confirmPassword xác nhận mật khẩu
     * @return ServiceResult chứa thông báo kết quả
     */
    public ServiceResult<Void> register(String fullName, LocalDate dateOfBirth, String phone,
                                        String email, String password, String confirmPassword) {
        // B1: Chuẩn hóa chuỗi dữ liệu đầu vào
        String trimmedFullName = fullName != null ? fullName.trim() : "";
        String trimmedPhone = phone != null ? phone.trim() : "";
        String trimmedEmail = email != null ? email.trim() : "";

        // B2: Kiểm tra tính hợp lệ cơ bản của dữ liệu (Validation)
        ServiceResult<Void> validationResult = validateInput(trimmedFullName, dateOfBirth, trimmedPhone, trimmedEmail, password, confirmPassword);
        boolean isInvalidInput = !validationResult.isSuccess();
        if (isInvalidInput) {
            return validationResult;
        }

        try {
            // B3: Kiểm tra định danh theo số điện thoại (UC-IDENT)
            Person existingPerson = personDAO.findByPhone(trimmedPhone);
            boolean isPhoneAlreadyRegistered = existingPerson != null;

            if (isPhoneAlreadyRegistered) {
                return handleExistingPhone(existingPerson, trimmedEmail, password);
            }

            // B4: Nhánh đăng ký mới hoàn toàn — kiểm tra trùng email (DBR-23)
            boolean isEmailExisted = personDAO.existsByEmail(trimmedEmail);
            if (isEmailExisted) {
                return ServiceResult.fail(Messages.MSG12_EMAIL_USED);
            }

            // B5: Thực hiện tạo mới trong Transaction (Person -> Customer -> Account)
            return createNewCustomerTransaction(trimmedFullName, dateOfBirth, trimmedPhone, trimmedEmail, password);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Các bước xử lý con & Transaction =====

    /**
     * Xử lý các nhánh khi số điện thoại đã tồn tại ở hồ sơ active.
     */
    private ServiceResult<Void> handleExistingPhone(Person existingPerson, String inputEmail, String password) throws SQLException {
        // Kiểm tra Person này đã có tài khoản chưa
        boolean hasAccount = hasAccountByPersonId(existingPerson.getPersonId());
        if (hasAccount) {
            return ServiceResult.fail(Messages.MSG51_PHONE_HAS_ACCOUNT);
        }

        // Kiểm tra hồ sơ có email không
        String existingEmail = existingPerson.getEmail();
        boolean hasNoEmail = ValidationUtil.isNullOrEmpty(existingEmail);
        if (hasNoEmail) {
            return ServiceResult.fail(Messages.MSG53_PROFILE_NO_EMAIL);
        }

        // So khớp email người dùng nhập với email trong hồ sơ
        boolean isEmailMatched = existingEmail.equalsIgnoreCase(inputEmail);
        if (!isEmailMatched) {
            String maskedEmail = maskEmail(existingEmail);
            return ServiceResult.fail(String.format(Messages.MSG52_PHONE_DIFFERENT_EMAIL, maskedEmail));
        }

        // Nhánh UC-Claim: Kích hoạt hồ sơ có sẵn bằng cách tạo Account gắn vào Person cũ
        return claimExistingAccount(existingPerson.getPersonId(), password);
    }

    /**
     * Tạo tài khoản mới gắn vào Person đã có sẵn tại quầy (UC-Claim).
     * KHÔNG đổi Customer.status — CBR-01: status chỉ đổi khi khách thanh toán gói.
     */
    private ServiceResult<Void> claimExistingAccount(int personId, String rawPassword) {
        String passwordHash = PasswordUtil.hashPassword(rawPassword);

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Chỉ thêm Account, KHÔNG đổi Customer.status (CBR-01)
                insertAccount(conn, personId, passwordHash);

                conn.commit();
                return ServiceResult.ok(null, Messages.MSG54_ACCOUNT_CREATED);

            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Tạo trọn gói Person, Customer và Account cho khách hàng mới trong Transaction.
     */
    private ServiceResult<Void> createNewCustomerTransaction(String fullName, LocalDate dob, String phone,
                                                             String email, String rawPassword) {
        String passwordHash = PasswordUtil.hashPassword(rawPassword);

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // B1: Thêm Person
                Person person = new Person(fullName, dob, phone, email);
                person.setReleased(false);
                int personId = personDAO.insert(conn, person);

                // B2: Sinh mã khách hàng và thêm Customer (Self, New)
                String customerCode = customerDAO.generateNextCustomerCode(conn);
                Customer customer = new Customer(personId, customerCode, "Self", "New");
                customerDAO.insert(conn, customer);

                // B3: Thêm Account
                insertAccount(conn, personId, passwordHash);

                conn.commit();
                return ServiceResult.ok(null, Messages.MSG54_ACCOUNT_CREATED);

            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Hàm hỗ trợ kiểm tra dữ liệu =====

    private ServiceResult<Void> validateInput(String fullName, LocalDate dob, String phone,
                                              String email, String password, String confirmPassword) {
        Map<String, String> errors = new LinkedHashMap<>();

        // Họ tên (2-50 ký tự)
        if (ValidationUtil.isNullOrEmpty(fullName)) {
            errors.put("fullName", String.format(Messages.MSG09_REQUIRED, "Họ và tên"));
        } else if (!ValidationUtil.isLengthValid(fullName, 2, 50)) {
            errors.put("fullName", String.format(Messages.MSG72_FULL_NAME_LENGTH,
                    AppConstants.FULL_NAME_MIN_LENGTH, AppConstants.FULL_NAME_MAX_LENGTH));
        } else if (!ValidationUtil.isValidFullName(fullName)) {
            errors.put("fullName", Messages.MSG67_FULL_NAME_RULE);
        }

        // Ngày sinh
        if (dob == null) {
            errors.put("dateOfBirth", String.format(Messages.MSG09_REQUIRED, "Ngày sinh"));
        } else if (dob.isAfter(DateTimeUtil.today())) {
            errors.put("dateOfBirth", Messages.MSG15_DOB_IN_FUTURE);
        } else {
            int age = DateTimeUtil.calculateAge(dob, DateTimeUtil.today());
            if (age > 100) {
                errors.put("dateOfBirth", String.format(Messages.MSG69_DOB_TOO_OLD, 100));
            }
        }

        // Số điện thoại (10 số, bắt đầu bằng 0)
        if (ValidationUtil.isNullOrEmpty(phone)) {
            errors.put("phone", String.format(Messages.MSG09_REQUIRED, "Số điện thoại"));
        } else if (!ValidationUtil.isValidPhone(phone)) {
            errors.put("phone", Messages.MSG13_INVALID_PHONE);
        }

        // Email
        if (ValidationUtil.isNullOrEmpty(email)) {
            errors.put("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        } else if (!ValidationUtil.isValidEmail(email)) {
            errors.put("email", Messages.MSG11_INVALID_EMAIL);
        } else if (email.length() > 100) {
            errors.put("email", String.format(Messages.MSG10_MAX_LENGTH, 100));
        }

        // Mật khẩu (8-64 ký tự, có ít nhất 1 chữ cái và 1 chữ số)
        if (ValidationUtil.isNullOrEmpty(password)) {
            errors.put("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu"));
        } else if (!ValidationUtil.isValidPassword(password)) {
            errors.put("password", String.format(Messages.MSG66_PASSWORD_RULE, AppConstants.PASSWORD_MIN_LENGTH, AppConstants.PASSWORD_MAX_LENGTH));
        }

        // Nhập lại mật khẩu
        if (ValidationUtil.isNullOrEmpty(confirmPassword)) {
            errors.put("confirmPassword", String.format(Messages.MSG09_REQUIRED, "Nhập lại mật khẩu"));
        } else if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", Messages.MSG47_PASSWORD_MISMATCH);
        }

        if (!errors.isEmpty()) {
            return ServiceResult.failFields(errors);
        }

        return ServiceResult.ok(null);
    }

    private boolean hasAccountByPersonId(int personId) throws SQLException {
        String sql = "SELECT 1 FROM Accounts WHERE PersonId = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertAccount(Connection conn, int personId, String passwordHash) throws SQLException {
        String sql = "INSERT INTO Accounts (PersonId, PasswordHash, Status, FailedLoginCount, CreatedAt) "
                + "VALUES (?, ?, 'Active', 0, SYSDATETIME())";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);
            stmt.setString(2, passwordHash);
            stmt.executeUpdate();
        }
    }

    // updateCustomerStatusToActive đã bị XOÁ — CBR-01: status Customer không đổi khi claim account

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String namePart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        if (namePart.length() <= 2) {
            return namePart.charAt(0) + "***" + domainPart;
        }
        return namePart.substring(0, 2) + "***" + domainPart;
    }
}