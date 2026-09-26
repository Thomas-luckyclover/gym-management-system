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
import com.gms.constant.AppConstants;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
/**
 * Nghiệp vụ Account Settings - UC-04.
 */
public class AccountSettingsService {

    private final AccountDAO accountDAO;
    private final PersonDAO personDAO;
    private final EmployeeDAO employeeDAO;
    private final AuditLogService auditLogService;

    public AccountSettingsService() {
        accountDAO = new AccountDAO();
        personDAO = new PersonDAO();
        employeeDAO = new EmployeeDAO();
        auditLogService = new AuditLogService();
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

// B8: Cập nhật mật khẩu và ghi AuditLog
// trong cùng một transaction
            try (Connection conn =
                         DBContext.getConnection()) {

                conn.setAutoCommit(false);

                try {
                    boolean isUpdated =
                            accountDAO.updatePasswordHash(
                                    conn,
                                    accountId,
                                    newPasswordHash
                            );

                    if (!isUpdated) {
                        conn.rollback();

                        return ServiceResult.fail(
                                "Không thể cập nhật mật khẩu."
                        );
                    }

                    auditLogService.logInTransaction(
                            conn,
                            accountId,
                            "Người dùng đã thay đổi mật khẩu của tài khoản cá nhân.",
                            "Account",
                            accountId,
                            "PasswordHash",
                            null,
                            null,
                            null
                    );

                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    e.printStackTrace();

                    return ServiceResult.fail(
                            Messages.MSG04_SYSTEM_BUSY
                    );
                }
            }

// B9: Đăng xuất tất cả phiên khác,
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
    /**
     * Cập nhật avatar cho chính tài khoản đang đăng nhập.
     *
     * Ảnh hợp lệ phải là JPG hoặc PNG và không vượt quá dung lượng
     * được quy định bởi ValidationUtil/AppConstants.
     *
     * @param accountId id tài khoản hiện tại
     * @param contentType MIME type của file
     * @param sizeInBytes kích thước file
     * @param inputStream dữ liệu ảnh
     * @param uploadDirectory thư mục lưu avatar vật lý
     * @return kết quả cập nhật
     */
    public ServiceResult<Void> updateAvatar(
            int accountId,
            String contentType,
            long sizeInBytes,
            InputStream inputStream,
            Path uploadDirectory
    ) {

        boolean isValidImage =
                ValidationUtil.isValidImageFile(
                        contentType,
                        sizeInBytes
                );

        if (!isValidImage) {

            long maxSizeMb =
                    AppConstants.IMAGE_MAX_BYTES
                            / (1024L * 1024L);

            String message =
                    String.format(
                            Messages.MSG20_INVALID_IMAGE,
                            maxSizeMb + " MB"
                    );

            return ServiceResult.failField(
                    "avatarFile",
                    message
            );
        }

        if (inputStream == null
                || uploadDirectory == null) {

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }

        Path newFilePath = null;

        try {

            Account account =
                    accountDAO.findById(accountId);

            if (account == null) {

                return ServiceResult.fail(
                        String.format(
                                Messages.MSG08_NOT_FOUND,
                                "tài khoản"
                        )
                );
            }

            Person person =
                    personDAO.findById(
                            account.getPersonId()
                    );

            if (person == null) {

                return ServiceResult.fail(
                        String.format(
                                Messages.MSG08_NOT_FOUND,
                                "hồ sơ"
                        )
                );
            }

            Files.createDirectories(
                    uploadDirectory
            );

            String extension =
                    "image/png".equals(contentType)
                            ? ".png"
                            : ".jpg";

            String fileName =
                    "avatar-"
                            + person.getPersonId()
                            + "-"
                            + UUID.randomUUID()
                            + extension;

            newFilePath =
                    uploadDirectory
                            .resolve(fileName)
                            .normalize();

            Path normalizedDirectory =
                    uploadDirectory
                            .toAbsolutePath()
                            .normalize();

            Path normalizedFile =
                    newFilePath
                            .toAbsolutePath()
                            .normalize();

            if (!normalizedFile.startsWith(
                    normalizedDirectory
            )) {

                return ServiceResult.fail(
                        Messages.MSG04_SYSTEM_BUSY
                );
            }

            Files.copy(
                    inputStream,
                    newFilePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            String avatarPath =
                    "/uploads/avatar/"
                            + fileName;

            boolean isUpdated =
                    personDAO.updateAvatar(
                            person.getPersonId(),
                            avatarPath
                    );

            if (!isUpdated) {

                Files.deleteIfExists(
                        newFilePath
                );

                return ServiceResult.fail(
                        Messages.MSG04_SYSTEM_BUSY
                );
            }

            deleteOldAvatarFile(
                    person.getAvatar(),
                    uploadDirectory
            );

            return ServiceResult.ok(null);

        } catch (SQLException | IOException e) {

            if (newFilePath != null) {

                try {

                    Files.deleteIfExists(
                            newFilePath
                    );

                } catch (IOException ignored) {
                    // Không làm thay đổi kết quả lỗi chính.
                }
            }

            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }
    /**
     * Xóa avatar cũ do chức năng Account Settings tạo ra.
     * Không đụng tới ảnh check-in hay loại ảnh khác.
     *
     * @param oldAvatarPath đường dẫn avatar cũ trong DB
     * @param uploadDirectory thư mục avatar vật lý
     */
    private void deleteOldAvatarFile(
            String oldAvatarPath,
            Path uploadDirectory
    ) {

        if (ValidationUtil.isNullOrEmpty(
                oldAvatarPath
        )) {
            return;
        }

        String avatarPrefix =
                "/uploads/avatar/";

        if (!oldAvatarPath.startsWith(
                avatarPrefix
        )) {
            return;
        }

        String oldFileName =
                oldAvatarPath.substring(
                        avatarPrefix.length()
                );

        Path normalizedDirectory =
                uploadDirectory
                        .toAbsolutePath()
                        .normalize();

        Path oldFile =
                uploadDirectory
                        .resolve(oldFileName)
                        .toAbsolutePath()
                        .normalize();

        if (!oldFile.startsWith(
                normalizedDirectory
        )) {
            return;
        }

        try {

            Files.deleteIfExists(
                    oldFile
            );

        } catch (IOException ignored) {
            // Avatar mới đã cập nhật thành công;
            // lỗi cleanup file cũ không làm rollback nghiệp vụ.
        }
    }
}