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
 * Nghiệp vụ "Quên mật khẩu" (màn #3, UC-03). Đây cũng là cách tài khoản do Lễ tân tạo tại quầy
 * (chưa có mật khẩu) đặt mật khẩu lần đầu (CBR-24, CBR-31) — không có luồng link riêng.
 *
 * Class này chỉ quyết định ĐƯỢC hay KHÔNG ĐƯỢC. Nó không biết gì về trang web:
 * Servlet gọi vào, nhận ServiceResult, rồi tự chọn trang để hiển thị.
 *
 * Quên mật khẩu có 3 bước, mỗi bước là 1 hàm:
 *   Bước 1  requestCode(email)                              khách nhập email → gửi mã 6 số về email
 *   Bước 2  verifyCode(email, code)                         khách nhập mã → kiểm tra đúng/sai
 *   Bước 3  resetPassword(accountId, matKhau, nhapLai)      khách đặt mật khẩu mới
 *
 * Luật gửi mã / nhập mã (chờ 60 giây, sai 5 lần thì khóa 5 phút...) KHÔNG nằm ở đây
 * mà nằm ở VerificationCodeService — class này chỉ gọi sang.
 *
 * @author Le Cong Minh
 */
public class PasswordRecoveryService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final VerificationCodeDAO verificationCodeDAO = new VerificationCodeDAO();
    private final VerificationCodeService verificationCodeService = new VerificationCodeService();
    private final AuditLogService auditLogService = new AuditLogService();

    // ===== Quên mật khẩu — Bước 1: nhập email, gửi mã =====

    /**
     * Gửi mã khôi phục 6 số tới email của khách.
     *
     * Dùng khi: khách ở bước 1 bấm "Gửi mã xác thực", hoặc ở bước 2 bấm "Gửi lại mã".
     *
     * @param email email khách gõ vào ô, đã bỏ khoảng trắng 2 đầu.
     *              VD: "thao.luong@gmail.com". Nếu khách gõ số điện thoại thì vẫn truyền vào như chữ bình thường.
     * @return kết quả gửi mã:
     *         - Thành công: không có dữ liệu kèm theo, chỉ có câu MSG42
     *           "Mã đã được gửi tới thao.luong@gmail.com. Mã có hiệu lực trong 15 phút."
     *         - Thất bại vì ô email sai: lỗi gắn với ô "email" (bỏ trống, sai định dạng, không có tài khoản MSG41)
     *           → trang hiện chữ đỏ ngay dưới ô email.
     *         - Thất bại chung: tài khoản bị khóa/nghỉ việc (MSG37), đang bị khóa xác thực (MSG61),
     *           chưa đủ 60 giây, lỗi hệ thống (MSG04) → trang hiện khung lỗi hoặc toast.
     */
    public ServiceResult<Void> requestCode(String email) {
        // B1: ô email bỏ trống → báo "Email là bắt buộc." dưới ô
        if (ValidationUtil.isNullOrEmpty(email)) {
            return ServiceResult.failField("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        }

        // B2: sai định dạng email → báo dưới ô.
        //     Khách gõ số điện thoại (VD "0901234501") cũng rơi vào đây, vì chỉ khôi phục qua email (BR-03)
        if (!ValidationUtil.isValidEmail(email)) {
            return ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL);
        }

        try {
            // B3: tìm tài khoản đang dùng email này. Không có → MSG41 (luật mục 2.4 yêu cầu báo rõ)
            Account account = accountDAO.findByEmail(email);
            if (account == null) {
                return ServiceResult.failField("email", Messages.MSG41_EMAIL_NOT_FOUND);
            }

            // B4: tài khoản bị Admin khóa (Locked) hoặc nhân viên đã nghỉ (Inactive) → không cho khôi phục,
            //     báo MSG37 giống hệt màn đăng nhập.
            //     Lưu ý: khóa TẠM 5 phút do sai mật khẩu KHÔNG đổi cột Status, nên vẫn qua được bước này.
            if (account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B5: nhờ VerificationCodeService gửi mã loại "khôi phục" (RECOVERY).
            //     Bên đó tự kiểm tra: đang khóa không, đủ 60 giây chưa, đã xin quá 4 lần/giờ chưa → rồi mới gửi.
            return verificationCodeService.sendCode(email, VerificationCode.Purpose.RECOVERY, account.getAccountId());

        } catch (SQLException e) {
            // Lỗi kết nối / câu SQL → in lỗi thật ra console cho dev, báo khách "Hệ thống đang bận"
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Quên mật khẩu — Bước 2: nhập mã =====

    /**
     * Kiểm tra mã 6 số khách vừa gõ ở bước 2.
     *
     * @param email email đã nhận mã. Servlet LẤY TỪ SESSION (đã lưu ở bước 1), không lấy từ form,
     *              để khách không thể đổi sang email người khác giữa chừng.
     * @param code  mã khách gõ vào ô, VD "246810".
     * @return kết quả kiểm tra mã:
     *         - Thành công: kèm dòng mã vừa dùng. Servlet lấy 2 thứ từ dòng này:
     *           getAccountId() = tài khoản nào được đặt lại mật khẩu,
     *           getExpiresAt() = hạn chót để làm xong bước 3.
     *         - Thất bại: sai định dạng (MSG68_CODE_FORMAT), sai mã (MSG43), mã hết hạn (MSG44),
     *           sai lần thứ 5 → bị khóa (MSG61).
     */
    public ServiceResult<VerificationCode> verifyCode(String email, String code) {
        // Toàn bộ luật nhập mã nằm ở VerificationCodeService; ở đây chỉ nói rõ đây là mã loại "khôi phục"
        return verificationCodeService.verifyCode(email, VerificationCode.Purpose.RECOVERY, code);
    }

    /**
     * Lấy mã khôi phục GỬI GẦN NHẤT của 1 email — để Servlet tính 2 đồng hồ ở bước 2:
     * "Mã hết hạn sau mm:ss" và "Gửi lại mã (mm:ss)".
     *
     * @param email email đang khôi phục, VD "thao.luong@gmail.com".
     * @return dòng mã gần nhất (có giờ gửi SentAt và giờ hết hạn ExpiresAt);
     *         trả null nếu email này chưa từng được gửi mã khôi phục.
     * @throws SQLException khi truy vấn database lỗi — người gọi tự xử lý.
     */
    public VerificationCode findLatestRecoveryCode(String email) throws SQLException {
        return verificationCodeDAO.findLatestByEmailAndPurpose(email, VerificationCode.Purpose.RECOVERY);
    }

    /**
     * Cho biết khách còn mấy lần nhập sai mã và còn mấy lần bấm "Gửi lại mã" trước khi bị khóa.
     * Bước 2 dùng để hiện gợi ý MSG62–MSG65. Hàm chỉ ĐỌC, không làm thay đổi gì.
     *
     * @param email email đang khôi phục, VD "thao.luong@gmail.com".
     * @return kết quả:
     *         - Thành công: kèm VerificationAttemptInfo (đang khóa chưa, còn mấy lần nhập, còn mấy lần gửi lại,
     *           khóa bao nhiêu phút).
     *         - Thất bại: chỉ khi lỗi hệ thống (MSG04) — lúc đó Servlet không hiện gợi ý.
     */
    public ServiceResult<VerificationAttemptInfo> getRecoveryAttemptInfo(String email) {
        return verificationCodeService.getAttemptInfo(email, VerificationCode.Purpose.RECOVERY);
    }

    // ===== Quên mật khẩu — Bước 3: đặt mật khẩu mới =====

    /**
     * Đặt mật khẩu mới cho tài khoản đã nhập đúng mã ở bước 2.
     *
     * @param accountId       Id tài khoản đã xác minh mã. Servlet LẤY TỪ SESSION (lưu ở bước 2), không lấy từ form.
     * @param newPassword     mật khẩu mới khách gõ, giữ nguyên từng ký tự (không bỏ khoảng trắng), VD "Moi2026abc".
     * @param confirmPassword ô "Nhập lại mật khẩu", phải giống hệt newPassword.
     * @return kết quả:
     *         - Thành công: câu MSG49 "Đã đổi mật khẩu. Tất cả phiên đăng nhập trước đó đã bị đăng xuất."
     *         - Thất bại vì ô mật khẩu: lỗi gắn với ô "password" (bỏ trống / sai luật 8–64 chữ + số)
     *           hoặc ô "confirmPassword" (không khớp MSG47) → trang giữ nguyên form, hiện chữ đỏ dưới ô.
     *         - Thất bại chung: tài khoản vừa bị khóa (MSG37), lỗi hệ thống (MSG04).
     */
    public ServiceResult<Void> resetPassword(int accountId, String newPassword, String confirmPassword) {
        // B1: kiểm tra 2 ô mật khẩu (bỏ trống? đúng luật? khớp nhau?). Sai thì dừng luôn, chưa đụng DB
        ServiceResult<Void> passwordCheck = validateNewPassword(newPassword, confirmPassword);
        if (passwordCheck != null) {
            return passwordCheck;
        }

        try {
            // B2: đọc lại tài khoản — có thể Admin vừa khóa trong lúc khách đang khôi phục → MSG37
            Account account = accountDAO.findById(accountId);
            if (account == null || account.getStatus() != Account.Status.ACTIVE) {
                return ServiceResult.fail(Messages.MSG37_ACCOUNT_INACTIVE);
            }

            // B3: lưu mật khẩu mới (dạng BCrypt) + mở khóa tạm (bộ đếm sai mật khẩu về 0).
            //     VD: thao đang bị khóa 5 phút vì sai 5 lần → đặt lại xong là đăng nhập được ngay
            savePasswordAndUnlock(accountId, newPassword);

            // B4: đăng xuất tài khoản này trên MỌI trình duyệt đang đăng nhập (CBR-93).
            //     Truyền null = không chừa phiên nào (khác với đổi mật khẩu trong /account là chừa phiên hiện tại)
            SessionRegistryListener.invalidateSessionsOfAccount(accountId, null);

            return ServiceResult.ok(null, Messages.MSG49_PASSWORD_CHANGED);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== Hàm phụ =====

    /**
     * Kiểm tra 2 ô mật khẩu mới trước khi lưu (luật DBR-02: 8–64 ký tự, có cả chữ và số).
     *
     * @param newPassword     ô "Mật khẩu mới".
     * @param confirmPassword ô "Nhập lại mật khẩu".
     * @return null nếu cả 2 ô đều ổn (người gọi đi tiếp);
     *         ngược lại là kết quả thất bại có lỗi gắn đúng ô sai (người gọi trả thẳng kết quả này ra ngoài).
     */
    private ServiceResult<Void> validateNewPassword(String newPassword, String confirmPassword) {
        // Ô mật khẩu mới bỏ trống → "Mật khẩu mới là bắt buộc."
        if (ValidationUtil.isNullOrEmpty(newPassword)) {
            return ServiceResult.failField("password", String.format(Messages.MSG09_REQUIRED, "Mật khẩu mới"));
        }
        // Sai luật (VD "abcdefgh" thiếu số, "1234567" quá ngắn) → "Mật khẩu phải dài 8–64 ký tự, gồm cả chữ và số."
        if (!ValidationUtil.isValidPassword(newPassword)) {
            return ServiceResult.failField("password", String.format(Messages.MSG66_PASSWORD_RULE,
                    AppConstants.PASSWORD_MIN_LENGTH, AppConstants.PASSWORD_MAX_LENGTH));
        }
        // Ô nhập lại không giống hệt → "Mật khẩu xác nhận không khớp." dưới ô nhập lại
        if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
            return ServiceResult.failField("confirmPassword", Messages.MSG47_PASSWORD_MISMATCH);
        }
        return null;
    }

    /**
     * Lưu mật khẩu mới, mở khóa tạm và ghi AuditLog cho 1 tài khoản — làm chung 1 transaction:
     * cùng thành công, hoặc lỗi giữa chừng thì cùng hủy (không bao giờ có chuyện đổi được mật khẩu mà vẫn bị khóa).
     *
     * @param accountId   Id tài khoản cần đổi mật khẩu.
     * @param newPassword mật khẩu mới dạng chữ thường — hàm tự băm BCrypt, database chỉ nhận chuỗi đã băm.
     * @throws SQLException khi lưu lỗi — dữ liệu đã được trả về như cũ (rollback) trước khi ném lỗi ra.
     */
    private void savePasswordAndUnlock(int accountId, String newPassword) throws SQLException {
        // B1: băm mật khẩu bằng BCrypt (chuỗi dạng "$2a$10$..."), không bao giờ lưu mật khẩu gốc
        String newHash = PasswordUtil.hashPassword(newPassword);

        try (Connection conn = DBContext.getConnection()) {
            // B2: tắt tự lưu → các câu SQL dưới đây chỉ có hiệu lực khi gọi commit()
            conn.setAutoCommit(false);
            try {
                // B3: ghi mật khẩu mới, rồi đưa bộ đếm sai về 0 + xóa giờ khóa tạm
                accountDAO.updatePasswordHash(conn, accountId, newHash);
                accountDAO.resetFailedLogin(conn, accountId);

                // B3a: ghi nhật ký đặt lại mật khẩu, không lưu giá trị (GBR-07, DBR-02)
                auditLogService.logInTransaction(conn, accountId, "Đặt lại mật khẩu qua email",
                        "Account", accountId, "PasswordHash", null, null, null);

                // B4: cả 3 câu đều chạy được → lưu thật
                conn.commit();
            } catch (SQLException e) {
                // Lỗi ở giữa → hủy hết, database trở về như trước khi gọi hàm
                conn.rollback();
                throw e;
            }
        }
    }
}


