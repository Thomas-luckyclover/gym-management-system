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
 * SERVICE MÀN QUÊN MẬT KHẨU — /forgot-password (UC-03).
 *
 * AI GỌI SERVICE NÀY?  Chỉ ForgotPasswordServlet. Servlet đưa chữ thô (email, mã, mật khẩu); Service tự kiểm tra hết.
 * SERVICE GỌI AI?      AccountDAO (tìm tài khoản, lưu mật khẩu) · VerificationCodeDAO (mã mới nhất)
 *                      · VerificationCodeService (gửi / kiểm mã 6 số, khóa khi sai nhiều — dùng chung với Đăng ký, Đổi email)
 *                      · AuditLogService (ghi nhật ký) · SessionRegistryListener (đăng xuất mọi thiết bị).
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  requestCode     "Gửi mã xác thực" / "Gửi lại mã"           → (VerificationCodeService.sendCode)
 *   HÀM CHÍNH 2  verifyCode      "Xác nhận" mã 6 số                         → (VerificationCodeService.verifyCode)
 *   HÀM CHÍNH 3  resetPassword   "Đặt mật khẩu mới"                         → validateNewPassword, savePasswordAndUnlock
 *   ĐỌC CHO SERVLET              findLatestRecoveryCode, getRecoveryAttemptInfo (đồng hồ + gợi ý số lần ở bước code)
 *
 * LUẬT QUAN TRỌNG
 *   - Mã khôi phục dùng Purpose = Recovery; luật gửi lại 60 giây, số lần sai, khóa tạm nằm ở VerificationCodeService.
 *   - Đặt mật khẩu xong: mở khóa đăng nhập sai + đăng xuất MỌI thiết bị của tài khoản (kể cả session đang đặt lại).
 */
public class PasswordRecoveryService {
    // Đọc / ghi bảng Accounts.
    private final AccountDAO accountDAO = new AccountDAO();
    // Đọc mã mới nhất trong bảng VerificationCodes.
    private final VerificationCodeDAO verificationCodeDAO = new VerificationCodeDAO();
    // Gửi / kiểm mã 6 số (dùng chung với Đăng ký, Đổi email…).
    private final VerificationCodeService verificationCodeService = new VerificationCodeService();
    private final AuditLogService auditLogService = new AuditLogService();



    // ============================================================================================================
    // HÀM CHÍNH 1. requestCode — "Gửi mã xác thực" (bước email) và "Gửi lại mã" (bước code)
    //   Ai gọi: ForgotPasswordServlet.handleSend, handleResend  ·  Gọi xuống: AccountDAO.findByEmail, VerificationCodeService.sendCode
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — gửi mã khôi phục 6 số tới email.
     *   Ai gọi: ForgotPasswordServlet.handleSend, handleResend
     *   Trả về: ok(MSG42) | lỗi ô email / câu lỗi chung
     */
    public ServiceResult<Void> requestCode(String email) {
        // B1: kiểm email trống / sai định dạng
        if (ValidationUtil.isNullOrEmpty(email)) {
            return ServiceResult.failField("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        }

        if (!ValidationUtil.isValidEmail(email)) {
            return ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL);
        }

        try {
            // B2: tìm tài khoản theo email
            Account account = accountDAO.findByEmail(email);
            if (account == null) {
                return ServiceResult.failField("email", Messages.MSG41_EMAIL_NOT_FOUND);
            }

            // B3: tài khoản bị khóa → không gửi
            if (account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B4: gửi mã (bị khóa / gửi quá sớm / quá nhiều lần → báo lỗi)
            return verificationCodeService.sendCode(email, VerificationCode.Purpose.RECOVERY, account.getAccountId());

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }



    // ============================================================================================================
    // HÀM CHÍNH 2. verifyCode — "Xác nhận" mã 6 số (bước code)
    //   Ai gọi: ForgotPasswordServlet.handleVerify  ·  Gọi xuống: VerificationCodeService.verifyCode
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — kiểm mã 6 số người dùng nhập.
     *   Ai gọi: ForgotPasswordServlet.handleVerify
     *   Trả về: ok(mã đã dùng: AccountId + hạn) | lỗi ô mã
     */
    public ServiceResult<VerificationCode> verifyCode(String email, String code) {
        return verificationCodeService.verifyCode(email, VerificationCode.Purpose.RECOVERY, code);
    }



    // ============================================================================================================
    // HÀM CHÍNH 3. resetPassword — "Đặt mật khẩu mới" (bước reset)
    //   Ai gọi: ForgotPasswordServlet.handleReset  ·  Gọi xuống: AccountDAO, AuditLogService, SessionRegistryListener
    //   Hàm phụ ngay dưới: validateNewPassword · savePasswordAndUnlock
    // ============================================================================================================

    /**
     * HÀM CHÍNH 3 — đặt mật khẩu mới cho tài khoản đã xác thực mã.
     *   Ai gọi: ForgotPasswordServlet.handleReset
     *   Trả về: ok(MSG95) | lỗi ô mật khẩu / câu lỗi chung
     */
    public ServiceResult<Void> resetPassword(int accountId, String newPassword, String confirmPassword) {
        // B1: kiểm mật khẩu mới
        ServiceResult<Void> passwordCheck = validateNewPassword(newPassword, confirmPassword);
        if (passwordCheck != null) {
            return passwordCheck;
        }

        try {
            // B2: tài khoản phải còn hoạt động
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B3: lưu mật khẩu + mở khóa đăng nhập sai (1 transaction)
            savePasswordAndUnlock(accountId, newPassword);

            // B4: đăng xuất mọi thiết bị
            SessionRegistryListener.invalidateSessionsOfAccount(accountId, null);

            // B5: báo thành công
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

    /** HÀM PHỤ (resetPassword B3) — ghi DB trong 1 transaction, lỗi thì rollback cả 3 lệnh. */
    private void savePasswordAndUnlock(int accountId, String newPassword) throws SQLException {
        String newHash = PasswordUtil.hashPassword(newPassword);

        try (Connection conn = DBContext.getConnection()) {
            // B1: mở transaction
            conn.setAutoCommit(false);
            try {
                // B2: lưu mật khẩu mới (đã hash)
                accountDAO.updatePasswordHash(conn, accountId, newHash);

                // B3: xóa số lần đăng nhập sai, mở khóa
                accountDAO.resetFailedLogin(conn, accountId);

                // B4: ghi nhật ký
                auditLogService.logInTransaction(
                        conn,
                        accountId,
                        "Đặt lại mật khẩu qua email",
                        "Account",
                        accountId,
                        "PasswordHash",
                        null,
                        null,
                        null);

                conn.commit();
            } catch (SQLException e) {
                // Lỗi → hủy cả 3 lệnh
                conn.rollback();
                throw e;
            }
        }
    }



    // ============================================================================================================
    // ĐỌC CHO SERVLET — số liệu đồng hồ và gợi ý dưới ô mã (ForgotPasswordServlet.setCodeTimers)
    // ============================================================================================================

    /** HÀM PHỤ (ForgotPasswordServlet.setCodeTimers) — mã khôi phục mới nhất của email → đồng hồ hết hạn / gửi lại. */
    public VerificationCode findLatestRecoveryCode(String email) throws SQLException {
        return verificationCodeDAO.findLatestByEmailAndPurpose(email, VerificationCode.Purpose.RECOVERY);
    }

    /** HÀM PHỤ (ForgotPasswordServlet.setCodeTimers) — còn mấy lần nhập sai / gửi lại → câu gợi ý dưới ô mã. */
    public ServiceResult<VerificationAttemptInfo> getRecoveryAttemptInfo(String email) {
        return verificationCodeService.getAttemptInfo(email, VerificationCode.Purpose.RECOVERY);
    }
}
