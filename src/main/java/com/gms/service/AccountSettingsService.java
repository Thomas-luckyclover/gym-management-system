package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.AccountSettingsView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Person;
import com.gms.listener.SessionRegistryListener;
import com.gms.util.PasswordUtil;
import com.gms.util.ValidationUtil;
import java.sql.SQLException;

/**
 * Nghiệp vụ Account Settings - UC-04.
 */
public class AccountSettingsService {

    private final AccountDAO accountDAO;
    private final PersonDAO personDAO;
    private final EmployeeDAO employeeDAO;

    public AccountSettingsService() {
        accountDAO = new AccountDAO();
        personDAO = new PersonDAO();
        employeeDAO = new EmployeeDAO();
    }

    /**
     * Lấy thông tin tài khoản và hồ sơ của người dùng hiện tại.
     *
     * @param accountId id tài khoản đang đăng nhập
     * @param sessionRole vai trò lưu trong session
     * @return dữ liệu Account Settings
     */
    public ServiceResult<AccountSettingsView> getAccountSettings(
            int accountId,
            Role sessionRole
    ) {

        try {
            Account account = accountDAO.findById(accountId);

            if (account == null) {
                String message = String.format(
                        Messages.MSG08_NOT_FOUND,
                        "tài khoản"
                );

                return ServiceResult.fail(message);
            }

            Person person = personDAO.findById(
                    account.getPersonId()
            );

            if (person == null) {
                String message = String.format(
                        Messages.MSG08_NOT_FOUND,
                        "hồ sơ"
                );

                return ServiceResult.fail(message);
            }

            Employee employee = null;

            boolean isCustomer =
                    sessionRole == Role.CUSTOMER;

            if (!isCustomer) {
                employee = employeeDAO.findByPersonId(
                        person.getPersonId()
                );
            }

            String employeeCode =
                    employee == null
                            ? null
                            : employee.getEmployeeCode();

            AccountSettingsView view =
                    new AccountSettingsView(
                            account.getAccountId(),
                            person.getPersonId(),
                            person.getFullName(),
                            person.getDateOfBirth(),
                            person.getPhone(),
                            person.getEmail(),
                            person.getAvatar(),
                            sessionRole,
                            employeeCode
                    );

            return ServiceResult.ok(view);

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }

    /**
     * Đổi mật khẩu cho chính tài khoản đang đăng nhập.
     *
     * Thứ tự kiểm tra:
     * 1. Mật khẩu hiện tại có được nhập.
     * 2. Mật khẩu hiện tại phải đúng.
     * 3. Mật khẩu mới phải đúng định dạng.
     * 4. Mật khẩu mới phải khác mật khẩu hiện tại.
     * 5. Xác nhận mật khẩu phải khớp.
     * 6. Cập nhật mật khẩu và đăng xuất các phiên khác.
     *
     * @param accountId id tài khoản hiện tại
     * @param currentPassword mật khẩu hiện tại
     * @param newPassword mật khẩu mới
     * @param confirmPassword xác nhận mật khẩu mới
     * @param currentSessionId id phiên hiện tại cần giữ lại
     * @return kết quả đổi mật khẩu
     */
    public ServiceResult<Void> changePassword(
            int accountId,
            String currentPassword,
            String newPassword,
            String confirmPassword,
            String currentSessionId
    ) {

        // B1: Bắt buộc nhập mật khẩu hiện tại
        if (ValidationUtil.isNullOrEmpty(
                currentPassword
        )) {
            return ServiceResult.failField(
                    "currentPassword",
                    "Vui lòng nhập mật khẩu hiện tại."
            );
        }

        try {
            Account account =
                    accountDAO.findById(accountId);

            if (account == null) {
                return ServiceResult.fail(
                        "Không tìm thấy tài khoản."
                );
            }

            // B2: Kiểm tra mật khẩu hiện tại trước tiên
            boolean isCurrentPasswordCorrect =
                    PasswordUtil.checkPassword(
                            currentPassword,
                            account.getPasswordHash()
                    );

            if (!isCurrentPasswordCorrect) {
                return ServiceResult.failField(
                        "currentPassword",
                        "Mật khẩu hiện tại không đúng."
                );
            }

            // B3: Chỉ kiểm tra mật khẩu mới
            // sau khi mật khẩu hiện tại đã đúng
            if (ValidationUtil.isNullOrEmpty(
                    newPassword
            )) {
                return ServiceResult.failField(
                        "newPassword",
                        "Vui lòng nhập mật khẩu mới."
                );
            }

            boolean isNewPasswordValid =
                    ValidationUtil.isValidPassword(
                            newPassword
                    );

            if (!isNewPasswordValid) {
                return ServiceResult.failField(
                        "newPassword",
                        "Mật khẩu phải từ 8–64 ký tự và có ít nhất 1 chữ cái, 1 chữ số."
                );
            }

            // B4: Mật khẩu mới không được giống mật khẩu cũ
            boolean isSameAsCurrentPassword =
                    PasswordUtil.checkPassword(
                            newPassword,
                            account.getPasswordHash()
                    );

            if (isSameAsCurrentPassword) {
                return ServiceResult.failField(
                        "newPassword",
                        "Mật khẩu mới phải khác mật khẩu hiện tại."
                );
            }

            // B5: Bắt buộc nhập xác nhận mật khẩu
            if (ValidationUtil.isNullOrEmpty(
                    confirmPassword
            )) {
                return ServiceResult.failField(
                        "confirmPassword",
                        "Vui lòng xác nhận mật khẩu mới."
                );
            }

            // B6: Xác nhận phải khớp mật khẩu mới
            boolean isPasswordMatched =
                    newPassword.equals(
                            confirmPassword
                    );

            if (!isPasswordMatched) {
                return ServiceResult.failField(
                        "confirmPassword",
                        "Mật khẩu xác nhận không khớp."
                );
            }

            // B7: Hash mật khẩu mới
            String newPasswordHash =
                    PasswordUtil.hashPassword(
                            newPassword
                    );

            boolean isUpdated =
                    accountDAO.updatePasswordHash(
                            accountId,
                            newPasswordHash
                    );

            if (!isUpdated) {
                return ServiceResult.fail(
                        "Không thể cập nhật mật khẩu."
                );
            }

            // B8: Đăng xuất tất cả phiên khác,
            // giữ lại phiên hiện tại
            SessionRegistryListener
                    .invalidateSessionsOfAccount(
                            accountId,
                            currentSessionId
                    );

            return ServiceResult.ok(null);

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }
}