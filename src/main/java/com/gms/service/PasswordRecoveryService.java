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
 * SERVICE "QUÊN MẬT KHẨU" (màn #3, UC-03). Chỉ ForgotPasswordServlet gọi.
 * Đây cũng là cách tài khoản do Lễ tân tạo tại quầy (chưa có mật khẩu) đặt mật khẩu LẦN ĐẦU (CBR-24, CBR-31).
 *
 * Service chỉ quyết định ĐƯỢC / KHÔNG ĐƯỢC rồi trả ServiceResult; không biết gì về trang web.
 *
 *   Bước 1  requestCode(email)                          kiểm tra email → gửi mã 6 số
 *   Bước 2  verifyCode(email, code)                     kiểm tra mã
 *           findLatestRecoveryCode, getRecoveryAttemptInfo   dữ liệu cho 2 đồng hồ + gợi ý số lượt
 *   Bước 3  resetPassword(accountId, mk, nhapLai)       lưu mật khẩu mới → validateNewPassword, savePasswordAndUnlock
 *
 * Luật gửi / nhập mã (chờ 60 giây, sai 5 lần khóa 5 phút…) KHÔNG nằm ở đây mà ở VerificationCodeService.
 *
 * THAM SỐ CẤU HÌNH (giá trị hiện tại)
 *   PASSWORD_MIN_LENGTH / MAX = 8 / 64   mật khẩu mới 8–64 ký tự, có cả chữ và số  (AppConstants)
 *   P-15 = 15   mã khôi phục sống 15 phút → đồng hồ "Mã hết hạn sau 14:59"           (SystemConfigs)
 *   P-17 = 5, P-18 = 5   nhập sai 5 lần → khóa 5 phút                               (SystemConfigs)
 *   Bước 3 phải xong trước giờ hết hạn của mã vừa dùng (lúc gửi mã + 15 phút), Servlet giữ mốc này trong session.
 *
 * @author Le Cong Minh
 */
public class PasswordRecoveryService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final VerificationCodeDAO verificationCodeDAO = new VerificationCodeDAO();
    private final VerificationCodeService verificationCodeService = new VerificationCodeService();
    private final AuditLogService auditLogService = new AuditLogService();


    // =================================================================================================
    // BƯỚC 1 — NHẬP EMAIL, GỬI MÃ
    // =================================================================================================

    /**
     * HÀM CHÍNH — ForgotPasswordServlet gọi ở B1 của handleSend (nút "Gửi mã") và B2 của handleResend ("Gửi lại mã").
     *
     * @param email chữ khách gõ, đã bỏ khoảng trắng 2 đầu. Gõ số điện thoại cũng truyền vào như chữ thường.
     * @return thành công: câu MSG42 "Mã đã được gửi tới … Mã có hiệu lực trong 15 phút."
     *         lỗi gắn ô "email": bỏ trống / sai định dạng / không có tài khoản (MSG41) → chữ đỏ dưới ô
     *         lỗi chung: tài khoản bị khóa (MSG37), đang khóa xác thực (MSG61), chưa đủ 60 giây, lỗi hệ thống (MSG04)
     */
    public ServiceResult<Void> requestCode(String email) {
        // B1: bỏ trống.
        if (ValidationUtil.isNullOrEmpty(email)) {
            return ServiceResult.failField("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        }

        // B2: sai định dạng. Gõ số điện thoại (vd "0901234501") cũng rơi vào đây vì chỉ khôi phục qua email (BR-03).
        if (!ValidationUtil.isValidEmail(email)) {
            return ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL);
        }

        try {
            // B3: không có tài khoản dùng email này → MSG41.
            Account account = accountDAO.findByEmail(email);
            if (account == null) {
                return ServiceResult.failField("email", Messages.MSG41_EMAIL_NOT_FOUND);
            }

            // B4: tài khoản bị Admin khóa / nhân viên đã nghỉ → MSG37 (giống màn Đăng nhập).
            //     Khóa TẠM 5 phút do sai mật khẩu KHÔNG đổi cột Status → vẫn qua được bước này (để tự mở khóa).
            if (account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B5: VerificationCodeService tự kiểm tra (đang khóa? đủ 60 giây? quá số lần/giờ?) rồi mới gửi.
            return verificationCodeService.sendCode(email, VerificationCode.Purpose.RECOVERY, account.getAccountId());

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }


    // =================================================================================================
    // BƯỚC 2 — NHẬP MÃ
    // =================================================================================================

    /**
     * HÀM CHÍNH — ForgotPasswordServlet gọi ở B2 của handleVerify.
     *
     * @param email lấy từ SESSION (lưu ở bước 1), không từ form → không đổi được sang email người khác giữa chừng
     * @param code  mã khách gõ, vd "246810"
     * @return thành công: kèm dòng mã vừa dùng (Servlet lấy getAccountId() + getExpiresAt() = hạn làm bước 3)
     *         thất bại: sai định dạng (MSG68), sai mã (MSG43), hết hạn (MSG44), sai lần thứ 5 → khóa (MSG61)
     */
    public ServiceResult<VerificationCode> verifyCode(String email, String code) {
        // Toàn bộ luật nhập mã ở VerificationCodeService; ở đây chỉ nói "mã loại khôi phục".
        return verificationCodeService.verifyCode(email, VerificationCode.Purpose.RECOVERY, code);
    }

    /**
     * HÀM CHÍNH — ForgotPasswordServlet gọi ở B1 của setCodeTimers.
     * Mã khôi phục gửi GẦN NHẤT của email → Servlet tính "Mã hết hạn sau mm:ss" và "Gửi lại mã (mm:ss)".
     * Trả null nếu email này chưa từng được gửi mã khôi phục.
     */
    public VerificationCode findLatestRecoveryCode(String email) throws SQLException {
        return verificationCodeDAO.findLatestByEmailAndPurpose(email, VerificationCode.Purpose.RECOVERY);
    }

    /**
     * HÀM CHÍNH — ForgotPasswordServlet gọi ở B2 của setCodeTimers. CHỈ ĐỌC.
     * Còn mấy lần nhập mã / mấy lần gửi lại trước khi bị khóa → gợi ý MSG62–MSG65.
     */
    public ServiceResult<VerificationAttemptInfo> getRecoveryAttemptInfo(String email) {
        return verificationCodeService.getAttemptInfo(email, VerificationCode.Purpose.RECOVERY);
    }


    // =================================================================================================
    // BƯỚC 3 — ĐẶT MẬT KHẨU MỚI
    // =================================================================================================

    /**
     * HÀM CHÍNH — ForgotPasswordServlet gọi ở B3 của handleReset.
     *
     * @param accountId       lấy từ SESSION (lưu ở bước 2), không từ form
     * @param newPassword     giữ nguyên từng ký tự (không trim)
     * @param confirmPassword ô "Nhập lại mật khẩu", phải giống hệt
     * @return thành công: MSG49 "Đã đổi mật khẩu. Tất cả phiên đăng nhập trước đó đã bị đăng xuất."
     *         lỗi gắn ô "password" / "confirmPassword" → trang giữ form, chữ đỏ dưới ô
     *         lỗi chung: tài khoản vừa bị khóa (MSG37), lỗi hệ thống (MSG04)
     */
    public ServiceResult<Void> resetPassword(int accountId, String newPassword, String confirmPassword) {
        // B1: kiểm tra 2 ô (trống? đúng luật? khớp?). Sai thì dừng, chưa đụng DB.
        ServiceResult<Void> passwordCheck = validateNewPassword(newPassword, confirmPassword);
        if (passwordCheck != null) {
            return passwordCheck;
        }

        try {
            // B2: đọc lại tài khoản — có thể Admin vừa khóa trong lúc khách đang khôi phục.
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B3: lưu mật khẩu mới (BCrypt) + mở khóa tạm + ghi nhật ký, chung 1 transaction.
            //     Ví dụ: đang bị khóa 5 phút vì sai mật khẩu 5 lần → đặt lại xong đăng nhập được ngay.
            savePasswordAndUnlock(accountId, newPassword);

            // B4: đăng xuất tài khoản này trên MỌI trình duyệt (CBR-93).
            //     null = không chừa phiên nào (khác đổi mật khẩu trong /account: chừa phiên đang dùng).
            SessionRegistryListener.invalidateSessionsOfAccount(accountId, null);

            return ServiceResult.ok(null, Messages.MSG49_PASSWORD_CHANGED);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * HÀM PHỤ — gọi ở B1 của resetPassword. Luật DBR-02: 8–64 ký tự, có cả chữ và số.
     * Trả null = cả 2 ô ổn; ngược lại = kết quả thất bại có lỗi gắn đúng ô sai.
     */
    private ServiceResult<Void> validateNewPassword(String newPassword, String confirmPassword) {
        // Bỏ trống.
        if (ValidationUtil.isNullOrEmpty(newPassword)) {
            return ServiceResult.failField("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu mới"));
        }
        // Sai luật: "abcdefgh" thiếu số, "1234567" quá ngắn…
        if (!ValidationUtil.isValidPassword(newPassword)) {
            return ServiceResult.failField("password", String.format(Messages.MSG66_PASSWORD_RULE,
                    AppConstants.PASSWORD_MIN_LENGTH, AppConstants.PASSWORD_MAX_LENGTH));
        }
        // Ô nhập lại không giống hệt.
        if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
            return ServiceResult.failField("confirmPassword", Messages.MSG47_PASSWORD_MISMATCH);
        }
        return null;
    }

    /**
     * HÀM PHỤ — gọi ở B3 của resetPassword.
     * 3 việc trong 1 TRANSACTION: đổi mật khẩu + mở khóa tạm + ghi AuditLog.
     * Cùng thành công, hoặc lỗi giữa chừng thì cùng hủy (không bao giờ "đổi được mật khẩu mà vẫn bị khóa").
     */
    private void savePasswordAndUnlock(int accountId, String newPassword) throws SQLException {
        // B1: băm BCrypt ("$2a$10$…"); database không bao giờ giữ mật khẩu gốc.
        String newHash = PasswordUtil.hashPassword(newPassword);

        try (Connection conn = DBContext.getConnection()) {
            // B2: tắt tự lưu → các câu SQL dưới chỉ có hiệu lực khi commit().
            conn.setAutoCommit(false);
            try {
                // B3: mật khẩu mới + bộ đếm sai về 0 / xóa giờ khóa tạm + nhật ký (không lưu giá trị mật khẩu — GBR-07).
                accountDAO.updatePasswordHash(conn, accountId, newHash);
                accountDAO.resetFailedLogin(conn, accountId);
                auditLogService.logInTransaction(conn, accountId, "Đặt lại mật khẩu qua email",
                        "Account", accountId, "PasswordHash", null, null, null);

                // B4: cả 3 câu chạy được → lưu thật.
                conn.commit();
            } catch (SQLException e) {
                // Lỗi giữa chừng → hủy hết, database về như cũ.
                conn.rollback();
                throw e;
            }
        }
    }
}
