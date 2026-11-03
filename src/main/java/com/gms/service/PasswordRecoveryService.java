package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.VerificationCodeDAO;
import com.gms.dto.ServiceResult;
import com.gms.dto.VerificationAttemptInfo;
import com.gms.entity.Account;
import com.gms.entity.VerificationCode;
import com.gms.listener.SessionRegistryListener;
import com.gms.util.DBContext;
import com.gms.util.PasswordUtil;
import com.gms.util.ValidationUtil;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * QUÊN MẬT KHẨU — xử lý cho ForgotPasswordServlet.
 * Phần mã 6 số (gửi, kiểm, khóa khi sai nhiều) dùng chung VerificationCodeService (Purpose = Recovery).
 */
public class PasswordRecoveryService {
    // Đọc / ghi bảng Accounts.
    private final AccountDAO accountDAO = new AccountDAO();
    // Đọc mã mới nhất trong bảng VerificationCodes.
    private final VerificationCodeDAO verificationCodeDAO = new VerificationCodeDAO();
    // Gửi / kiểm mã 6 số (dùng chung với Đăng ký, Đổi email…).
    private final VerificationCodeService verificationCodeService = new VerificationCodeService();
    private final AuditLogService auditLogService = new AuditLogService();

    // Gửi mã khôi phục tới email (nút "Gửi mã xác thực" và "Gửi lại mã").
    public ServiceResult<Void> requestCode(String email) {
        // B1: email trống → MSG09 · sai định dạng → MSG11 (lỗi gắn vào ô "email")
        if (ValidationUtil.isNullOrEmpty(email)) {
            return ServiceResult.failField("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        }

        if (!ValidationUtil.isValidEmail(email)) {
            return ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL);
        }

        try {
            // B2: AccountDAO.findByEmail → không có tài khoản → MSG41 (ô email)
            Account account = accountDAO.findByEmail(email);
            if (account == null) {
                return ServiceResult.failField("email", Messages.MSG41_EMAIL_NOT_FOUND);
            }

            // B3: tài khoản bị khóa / ngừng → MSG37
            if (account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B4: VerificationCodeService.sendCode: đang bị khóa → MSG61 · gửi lại sớm < 60 giây → MSG70
            //     · gửi quá nhiều trong khung giờ → khóa → MSG61 · được → gửi mail + VerificationCodes thêm 1 dòng → MSG42
            return verificationCodeService.sendCode(email, VerificationCode.Purpose.RECOVERY, account.getAccountId());

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // Kiểm mã 6 số → VerificationCodeService.verifyCode. Trả về: ok(mã đã dùng: AccountId, hạn) | fail(câu lỗi ô "code").
    public ServiceResult<VerificationCode> verifyCode(String email, String code) {
        return verificationCodeService.verifyCode(email, VerificationCode.Purpose.RECOVERY, code);
    }

    // Mã khôi phục mới nhất của email (VerificationCodeDAO) — Servlet dùng để tính đồng hồ hết hạn / gửi lại.
    public VerificationCode findLatestRecoveryCode(String email) throws SQLException {
        return verificationCodeDAO.findLatestByEmailAndPurpose(email, VerificationCode.Purpose.RECOVERY);
    }

    // Còn mấy lần nhập sai / gửi lại (VerificationCodeService.getAttemptInfo) → Servlet làm câu gợi ý dưới ô mã.
    public ServiceResult<VerificationAttemptInfo> getRecoveryAttemptInfo(String email) {
        return verificationCodeService.getAttemptInfo(email, VerificationCode.Purpose.RECOVERY);
    }

    // Đặt mật khẩu mới cho tài khoản đã xác thực mã.
    public ServiceResult<Void> resetPassword(int accountId, String newPassword, String confirmPassword) {
        // B1: kiểm mật khẩu mới (validateNewPassword) → lỗi gắn vào ô password / confirmPassword
        ServiceResult<Void> passwordCheck = validateNewPassword(newPassword, confirmPassword);
        if (passwordCheck != null) {
            return passwordCheck;
        }

        try {
            // B2: AccountDAO.findById → tài khoản không còn Active → MSG37
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B3: lưu mật khẩu (1 transaction): Accounts: PasswordHash mới + mở khóa đăng nhập sai · AuditLogs thêm 1 dòng
            savePasswordAndUnlock(accountId, newPassword);

            // B4: đăng xuất mọi thiết bị đang đăng nhập tài khoản này (hủy các session)
            SessionRegistryListener.invalidateSessionsOfAccount(accountId, null);

            // B5: trả MSG95 → Servlet toast + về /login
            return ServiceResult.ok(null, Messages.MSG95_PASSWORD_RESET_COMPLETED);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ (resetPassword B1) — trống → MSG09 · không đủ 8–64 ký tự chữ + số → MSG66 · nhập lại khác → MSG47.
     * Trả về: null = hợp lệ, không thì ServiceResult lỗi gắn đúng ô.
     */
    private ServiceResult<Void> validateNewPassword(String newPassword, String confirmPassword) {
        if (ValidationUtil.isNullOrEmpty(newPassword)) {
            return ServiceResult.failField("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu mới"));
        }

        if (!ValidationUtil.isValidPassword(newPassword)) {
            return ServiceResult.failField("password", String.format(Messages.MSG66_PASSWORD_RULE,
                    AppConstants.PASSWORD_MIN_LENGTH, AppConstants.PASSWORD_MAX_LENGTH));
        }

        if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
            return ServiceResult.failField("confirmPassword", Messages.MSG47_PASSWORD_MISMATCH);
        }
        return null;
    }

    /**
     * HÀM PHỤ (resetPassword B3) — ghi DB trong 1 transaction, lỗi thì rollback cả 3 lệnh.
     */
    private void savePasswordAndUnlock(int accountId, String newPassword) throws SQLException {
        String newHash = PasswordUtil.hashPassword(newPassword);

        try (Connection conn = DBContext.getConnection()) {
            // Mở transaction
            conn.setAutoCommit(false);
            try {
                // Accounts: PasswordHash = hash BCrypt mới
                accountDAO.updatePasswordHash(conn, accountId, newHash);
                // Accounts: FailedLoginCount = 0, LockedUntil = NULL (mở khóa nếu đang bị khóa vì sai mật khẩu)
                accountDAO.resetFailedLogin(conn, accountId);
                // AuditLogs: thêm 1 dòng "Đặt lại mật khẩu qua email"
                auditLogService.logInTransaction(conn, accountId, "Đặt lại mật khẩu qua email",
                        "Account", accountId, "PasswordHash", null, null, null);

                conn.commit();
            } catch (SQLException e) {
                // Lỗi → rollback, không lưu gì
                conn.rollback();
                throw e;
            }
        }
    }
}
