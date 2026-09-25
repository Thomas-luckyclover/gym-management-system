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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

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
     */
    private ServiceResult<Void> claimExistingAccount(int personId, String rawPassword) {
        String passwordHash = PasswordUtil.hashPassword(rawPassword);

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // B1: Thêm Account
                insertAccount(conn, personId, passwordHash);

                // B2: Cập nhật trạng thái Customer thành Active nếu đang là New
                updateCustomerStatusToActive(conn, personId);

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
        // Họ tên (2-50 ký tự)
        boolean isValidNameLength = ValidationUtil.isLengthValid(fullName, 2, 50);
        if (!isValidNameLength) {
            return ServiceResult.fail(String.format(Messages.MSG09_REQUIRED, "Họ và tên"));
        }
        boolean isValidFullName = ValidationUtil.isValidFullName(fullName);
        if (!isValidFullName) {
            return ServiceResult.fail(Messages.EXTRA_FULL_NAME_RULE);
        }

        // Ngày sinh
        if (dob == null) {
            return ServiceResult.fail(String.format(Messages.MSG09_REQUIRED, "Ngày sinh"));
        }
        boolean isFutureDob = dob.isAfter(DateTimeUtil.today());
        if (isFutureDob) {
            return ServiceResult.fail(Messages.MSG15_DOB_IN_FUTURE);
        }
        int age = DateTimeUtil.calculateAge(dob, DateTimeUtil.today());
        boolean isTooOld = age > 100;
        if (isTooOld) {
            return ServiceResult.fail(String.format(Messages.EXTRA_DOB_TOO_OLD, 100));
        }

        // Số điện thoại (10 số, bắt đầu bằng 0)
        boolean isValidPhone = ValidationUtil.isValidPhone(phone);
        if (!isValidPhone) {
            return ServiceResult.fail(Messages.MSG13_INVALID_PHONE);
        }

        // Email
        boolean isValidEmail = ValidationUtil.isValidEmail(email);
        if (!isValidEmail) {
            return ServiceResult.fail(Messages.MSG11_INVALID_EMAIL);
        }
        boolean isEmailTooLong = email.length() > 100;
        if (isEmailTooLong) {
            return ServiceResult.fail(String.format(Messages.MSG10_MAX_LENGTH, 100));
        }

        // Mật khẩu (8-64 ký tự, có ít nhất 1 chữ cái và 1 chữ số)
        boolean isValidPassword = ValidationUtil.isValidPassword(password);
        if (!isValidPassword) {
            return ServiceResult.fail(String.format(Messages.EXTRA_PASSWORD_RULE, AppConstants.PASSWORD_MIN_LENGTH, AppConstants.PASSWORD_MAX_LENGTH));
        }

        // Nhập lại mật khẩu
        boolean isPasswordMatched = password.equals(confirmPassword);
        if (!isPasswordMatched) {
            return ServiceResult.fail(Messages.MSG47_PASSWORD_MISMATCH);
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

    private void updateCustomerStatusToActive(Connection conn, int personId) throws SQLException {
        String sql = "UPDATE Customers SET Status = 'Active' WHERE PersonId = ? AND Status = 'New'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);
            stmt.executeUpdate();
        }
    }

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