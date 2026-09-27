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
import com.gms.util.DateTimeUtil;
import com.gms.util.PasswordUtil;
import com.gms.util.ValidationUtil;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Quên mật khẩu (#3, UC-03) và đặt mật khẩu qua link email (CBR-24, CBR-31).
 *
 * Quên mật khẩu — 3 bước (01_Phase1_Rules.md mục 2.4):
 *   1. requestCode      email → kiểm tra có tài khoản → gửi mã Recovery
 *   2. verifyCode       mã 6 số đúng → trả AccountId đã xác minh
 *   3. resetPassword    đặt mật khẩu mới → mở khóa tạm → đăng xuất mọi phiên (CBR-93)
 * Luật gửi/nhập mã (60 giây, 5 lần, khóa 5 phút — CBR-94) do VerificationCodeService lo.
 *
 * Đặt mật khẩu qua link: checkSetupLink khi mở link, setPasswordByLink khi gửi form.
 *
 * @author Le Cong Minh
 */
public class PasswordRecoveryService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final VerificationCodeDAO verificationCodeDAO = new VerificationCodeDAO();
    private final VerificationCodeService verificationCodeService = new VerificationCodeService();

    // ===== Quên mật khẩu — bước 1: gửi mã =====

    /**
     * Gửi mã khôi phục tới email của tài khoản.
     *
     * Chỉ nhận email, không nhận SĐT (BR-03). Tài khoản bị Admin khóa / nhân viên đã nghỉ → MSG37,
     * khớp với màn đăng nhập. Khóa tạm do sai mật khẩu (BR-01) và tài khoản chưa có mật khẩu (CBR-31)
     * vẫn được khôi phục.
     *
     * @param email email khách nhập (đã trim)
     * @return thành công kèm MSG42; thất bại kèm lỗi dưới ô "email" hoặc lỗi chung
     */
    public ServiceResult<Void> requestCode(String email) {
        // B1: kiểm tra ô email — nhập SĐT cũng rơi vào "sai định dạng email"
        if (ValidationUtil.isNullOrEmpty(email)) {
            return ServiceResult.failField("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        }
        if (!ValidationUtil.isValidEmail(email)) {
            return ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL);
        }

        try {
            // B2: email phải thuộc 1 tài khoản đang có (MSG41 theo luật mục 2.4)
            Account account = accountDAO.findByEmail(email);
            if (account == null) {
                return ServiceResult.failField("email", Messages.MSG41_EMAIL_NOT_FOUND);
            }

            // B3: tài khoản bị Admin khóa hoặc đã ngừng hoạt động → không cho khôi phục
            if (account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B4: gửi mã — luật 60 giây / 5 lần / khóa email nằm trong VerificationCodeService
            return verificationCodeService.sendCode(email, VerificationCode.Purpose.RECOVERY, account.getAccountId());

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Quên mật khẩu — bước 2: kiểm tra mã =====

    /**
     * Kiểm tra mã khôi phục khách gõ.
     *
     * @param email email đã nhận mã (lấy từ session, không lấy từ form)
     * @param code mã 6 số khách gõ
     * @return thành công kèm dòng mã đã dùng (AccountId + ExpiresAt làm hạn cho bước 3);
     *         thất bại kèm lý do (MSG43, MSG44, MSG61...)
     */
    public ServiceResult<VerificationCode> verifyCode(String email, String code) {
        return verificationCodeService.verifyCode(email, VerificationCode.Purpose.RECOVERY, code);
    }

    /**
     * Mã khôi phục mới nhất của email — Servlet dùng để tính đồng hồ hạn mã và nút "Gửi lại mã".
     *
     * @param email email đang khôi phục
     * @return dòng mã mới nhất, null nếu chưa gửi lần nào
     * @throws SQLException khi truy vấn lỗi
     */
    public VerificationCode findLatestRecoveryCode(String email) throws SQLException {
        return verificationCodeDAO.findLatestByEmailAndPurpose(email, VerificationCode.Purpose.RECOVERY);
    }

    /**
     * Số lần còn nhập mã / còn xin mã khôi phục của email — Servlet dùng để hiện MSG62–MSG65 ở bước 2.
     *
     * @param email email đang khôi phục
     * @return thành công kèm số lần còn lại; thất bại MSG04
     */
    public ServiceResult<VerificationAttemptInfo> getRecoveryAttemptInfo(String email) {
        return verificationCodeService.getAttemptInfo(email, VerificationCode.Purpose.RECOVERY);
    }

    // ===== Quên mật khẩu — bước 3: đặt mật khẩu mới =====

    /**
     * Đặt mật khẩu mới sau khi đã xác minh mã.
     *
     * Thành công: lưu hash mới + mở khóa tạm (bộ đếm sai về 0) trong 1 transaction,
     * rồi đăng xuất MỌI phiên của tài khoản (CBR-93).
     *
     * @param accountId tài khoản đã xác minh ở bước 2 (lấy từ session)
     * @param newPassword mật khẩu mới
     * @param confirmPassword nhập lại mật khẩu mới
     * @return thành công kèm MSG49; thất bại kèm lỗi dưới ô
     */
    public ServiceResult<Void> resetPassword(int accountId, String newPassword, String confirmPassword) {
        // B1: kiểm tra 2 ô mật khẩu
        ServiceResult<Void> passwordCheck = validateNewPassword(newPassword, confirmPassword);
        if (passwordCheck != null) {
            return passwordCheck;
        }

        try {
            // B2: tài khoản vẫn phải còn hoạt động (có thể vừa bị Admin khóa trong lúc khôi phục)
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B3: lưu mật khẩu + mở khóa tạm
            savePasswordAndUnlock(accountId, newPassword);

            // B4: đăng xuất mọi phiên đang mở của tài khoản
            SessionRegistryListener.invalidateSessionsOfAccount(accountId, null);

            // TODO GBR-07: ghi AuditLog "đặt lại mật khẩu qua email" khi có AuditLogDAO
            return ServiceResult.ok(null, Messages.MSG49_PASSWORD_CHANGED);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Đặt mật khẩu qua link email =====

    /**
     * Kiểm tra link đặt mật khẩu còn dùng được không (chưa đánh dấu đã dùng).
     *
     * @param token token gốc trên URL
     * @return thành công kèm dòng link; thất bại kèm MSG71_SETUP_LINK_INVALID
     */
    public ServiceResult<VerificationCode> checkSetupLink(String token) {
        return verificationCodeService.findValidSetupLink(token);
    }

    /**
     * Đặt mật khẩu lần đầu qua link. Kiểm tra lại link ở server (không tin form), lưu mật khẩu,
     * rồi đánh dấu link đã dùng để không mở lại được lần 2 (CBR-31).
     *
     * @param token token gốc trên URL
     * @param newPassword mật khẩu mới
     * @param confirmPassword nhập lại mật khẩu mới
     * @return thành công kèm MSG72_PASSWORD_SET; thất bại kèm lý do
     */
    public ServiceResult<Void> setPasswordByLink(String token, String newPassword, String confirmPassword) {
        // B1: link phải còn hiệu lực
        ServiceResult<VerificationCode> linkResult = verificationCodeService.findValidSetupLink(token);
        if (!linkResult.isSuccess()) {
            return ServiceResult.fail(linkResult.getMessage());
        }
        VerificationCode setupLink = linkResult.getData();

        // B2: kiểm tra 2 ô mật khẩu
        ServiceResult<Void> passwordCheck = validateNewPassword(newPassword, confirmPassword);
        if (passwordCheck != null) {
            return passwordCheck;
        }

        try {
            // B3: tài khoản còn hoạt động
            Integer accountId = setupLink.getAccountId();
            Account account = accountId == null ? null : accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B4: lưu mật khẩu rồi đánh dấu link đã dùng
            savePasswordAndUnlock(accountId, newPassword);
            verificationCodeDAO.markUsed(setupLink.getVerificationCodeId(), DateTimeUtil.now());

            return ServiceResult.ok(null, Messages.MSG72_PASSWORD_SET);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Hàm phụ =====

    /**
     * Kiểm tra mật khẩu mới (DBR-02) và ô nhập lại (MSG47).
     *
     * @return null nếu hợp lệ; ngược lại là kết quả thất bại kèm lỗi dưới ô
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
     * Lưu hash mật khẩu mới và mở khóa tạm (FailedLoginCount = 0, LockedUntil = NULL) trong 1 transaction.
     */
    private void savePasswordAndUnlock(int accountId, String newPassword) throws SQLException {
        String newHash = PasswordUtil.hashPassword(newPassword);
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                accountDAO.updatePasswordHash(conn, accountId, newHash);
                accountDAO.resetFailedLogin(conn, accountId);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
