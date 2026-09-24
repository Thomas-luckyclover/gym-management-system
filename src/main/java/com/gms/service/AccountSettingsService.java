package com.gms.service;

import com.gms.constant.AppConstants;
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
import java.util.LinkedHashMap;
import java.util.Map;

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

            Person person
                    = personDAO.findById(account.getPersonId());

            if (person == null) {
                String message = String.format(
                        Messages.MSG08_NOT_FOUND,
                        "hồ sơ"
                );

                return ServiceResult.fail(message);
            }

            Employee employee = null;

            boolean isCustomer
                    = sessionRole == Role.CUSTOMER;

            if (!isCustomer) {
                employee = employeeDAO.findByPersonId(
                        person.getPersonId()
                );
            }

            String employeeCode
                    = employee == null
                    ? null
                    : employee.getEmployeeCode();

            AccountSettingsView view
                    = new AccountSettingsView(
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

    public ServiceResult<Void> changePassword(
            int accountId,
            String currentSessionId,
            String currentPassword,
            String newPassword,
            String confirmPassword
    ) {

        Map<String, String> fieldErrors
                = validatePasswordForm(
                        currentPassword,
                        newPassword,
                        confirmPassword
                );

        if (!fieldErrors.isEmpty()) {
            return ServiceResult.failFields(fieldErrors);
        }

        try {
            Account account = accountDAO.findById(accountId);

            if (account == null) {
                String message = String.format(
                        Messages.MSG08_NOT_FOUND,
                        "tài khoản"
                );

                return ServiceResult.fail(message);
            }

            String oldPasswordHash
                    = account.getPasswordHash();

            if (oldPasswordHash == null) {
                return ServiceResult.fail(
                        Messages.MSG38_NO_PASSWORD_YET
                );
            }

            boolean currentPasswordCorrect
                    = PasswordUtil.checkPassword(
                            currentPassword,
                            oldPasswordHash
                    );

            if (!currentPasswordCorrect) {
                return ServiceResult.failField(
                        "currentPassword",
                        Messages.MSG34_WRONG_CREDENTIALS_STAFF
                );
            }

            boolean sameAsOldPassword
                    = PasswordUtil.checkPassword(
                            newPassword,
                            oldPasswordHash
                    );

            if (sameAsOldPassword) {
                return ServiceResult.failField(
                        "newPassword",
                        Messages.MSG48_PASSWORD_SAME_AS_OLD
                );
            }

            String newPasswordHash
                    = PasswordUtil.hashPassword(newPassword);

            boolean updated
                    = accountDAO.updatePasswordHash(
                            accountId,
                            newPasswordHash
                    );

            if (!updated) {
                return ServiceResult.fail(
                        Messages.MSG04_SYSTEM_BUSY
                );
            }

            SessionRegistryListener
                    .invalidateSessionsOfAccount(
                            accountId,
                            currentSessionId
                    );

            return ServiceResult.ok(
                    null,
                    Messages.MSG49_PASSWORD_CHANGED
            );

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }

    private Map<String, String> validatePasswordForm(
            String currentPassword,
            String newPassword,
            String confirmPassword
    ) {

        Map<String, String> errors
                = new LinkedHashMap<>();

        if (ValidationUtil.isNullOrEmpty(
                currentPassword
        )) {

            errors.put(
                    "currentPassword",
                    String.format(
                            Messages.MSG09_REQUIRED,
                            "Mật khẩu hiện tại"
                    )
            );
        }

        if (ValidationUtil.isNullOrEmpty(newPassword)) {

            errors.put(
                    "newPassword",
                    String.format(
                            Messages.MSG09_REQUIRED,
                            "Mật khẩu mới"
                    )
            );

        } else if (!ValidationUtil.isValidPassword(
                newPassword
        )) {

            errors.put(
                    "newPassword",
                    String.format(
                            Messages.EXTRA_PASSWORD_RULE,
                            AppConstants.PASSWORD_MIN_LENGTH,
                            AppConstants.PASSWORD_MAX_LENGTH
                    )
            );
        }

        if (ValidationUtil.isNullOrEmpty(
                confirmPassword
        )) {

            errors.put(
                    "confirmPassword",
                    String.format(
                            Messages.MSG09_REQUIRED,
                            "Xác nhận mật khẩu"
                    )
            );

        } else if (newPassword != null
                && !newPassword.equals(
                        confirmPassword
                )) {

            errors.put(
                    "confirmPassword",
                    Messages.MSG47_PASSWORD_MISMATCH
            );
        }

        return errors;
    }
}