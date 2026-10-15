package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.VerificationCodeDAO;
import com.gms.dto.ServiceResult;
import com.gms.dto.VerificationAttemptInfo;
import com.gms.entity.VerificationCode;
import com.gms.integration.EmailGateway;
import com.gms.integration.EmailSendException;
import com.gms.util.DateTimeUtil;
import com.gms.util.SecureCodeUtil;
import com.gms.util.ValidationUtil;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * "Bộ máy" mã xác thực 6 số (OTP) — dùng chung cho Đăng ký, Kích hoạt hồ sơ,
 * Quên mật khẩu, Đổi email.
 *
 * Hãy hình dung bảng VerificationCodes là 1 cuốn sổ: mỗi lần gửi mã là 1 dòng
 * mới.
 * DAO chỉ đọc/ghi cuốn sổ đó. Class này đọc sổ lên rồi QUYẾT ĐỊNH: có cho gửi
 * mã không, mã đúng không, có khóa không.
 *
 * Luật (CBR-94, số trong ngoặc là giá trị mặc định trong bảng SystemConfigs):
 * - Mã sống P-15 phút (15), mỗi mã chỉ dùng được 1 lần, chỉ mã GỬI GẦN NHẤT còn
 * hiệu lực
 * - Nhập sai P-17 lần (5) → mã bị hủy + email bị khóa xác thực P-18 phút (5)
 * - 2 lần xin mã phải cách nhau ít nhất 60 giây
 * - Xin mã lần thứ 5 trong vòng 60 phút → email bị khóa P-18 phút (5)
 *
 * @author Le Cong Minh
 */
public class VerificationCodeService {

    private final VerificationCodeDAO verificationCodeDAO = new VerificationCodeDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();
    private final EmailGateway emailGateway = new EmailGateway();

    // ===== 1. Gửi mã =====

    /**
     * Sinh mã 6 số mới, gửi về email, rồi ghi vào sổ.
     *
     * Dùng khi: khách bấm "Gửi mã" / "Gửi lại mã" ở Đăng ký, Kích hoạt hồ sơ, Quên
     * mật khẩu, Đổi email.
     * Hàm KHÔNG kiểm tra email có đúng định dạng hay có tài khoản không — người gọi
     * phải kiểm tra trước.
     *
     * VD: thao.luong@gmail.com bấm "Gửi mã" lúc 10:00, không bị chặn gì
     * → sinh mã 246810 → gửi mail → sổ thêm dòng: Recovery | 246810 | gửi 10:00 |
     * hết hạn 10:15
     *
     * @param email     email sẽ nhận mã, đã được người gọi kiểm tra hợp lệ. VD
     *                  "thao.luong@gmail.com".
     * @param purpose   mã dùng để làm gì: SIGN_UP (đăng ký), CLAIM (kích hoạt hồ
     *                  sơ), RECOVERY (quên mật khẩu),
     *                  EMAIL_CHANGE (đổi email). Mỗi loại tính riêng "mã gần nhất".
     * @param accountId Id tài khoản của email đó. Truyền null khi khách đang đăng
     *                  ký (chưa có tài khoản).
     * @return kết quả:
     *         - Thành công: câu MSG42 "Mã đã được gửi tới … Mã có hiệu lực trong 15
     *         phút."
     *         - Thất bại: email đang bị khóa (MSG61 kèm "thử lại sau mm:ss"), chưa
     *         đủ 60 giây
     *         ("Vui lòng chờ n giây…"), lần xin thứ 5 trong giờ (MSG61), gửi mail
     *         lỗi / lỗi DB (MSG04).
     */
    public ServiceResult<Void> sendCode(String email, VerificationCode.Purpose purpose, Integer accountId) {
        try {
            // B1: lấy giờ hiện tại + đọc 3 tham số từ bảng SystemConfigs
            // validMinutes = mã sống bao lâu (P-15), maxAttempts = mốc 5 lần (P-17),
            // lockMinutes = khóa bao lâu (P-18)
            LocalDateTime now = DateTimeUtil.now();
            int validMinutes = systemConfigService.getInt(SystemConfigService.VERIFICATION_VALID_MINUTES);
            int maxAttempts = systemConfigService.getInt(SystemConfigService.MAX_FAILED_ATTEMPTS);
            int lockMinutes = systemConfigService.getInt(SystemConfigService.LOCK_MINUTES);

            // B2: CHẶN 1 — email đang bị khóa xác thực → không cho xin mã, báo còn bao lâu
            // mới mở
            LocalDateTime lockedUntil = verificationCodeDAO.findLockedUntil(email, now);
            if (lockedUntil != null) {
                String remainingTime = formatRemainingTime(now, lockedUntil);
                return ServiceResult
                        .fail(String.format(Messages.MSG61_VERIFICATION_LOCKED, lockMinutes, remainingTime));
            }

            // B3: CHẶN 2 — mã gần nhất (cùng loại) mới gửi chưa đủ 60 giây → bảo chờ thêm n
            // giây
            // VD: gửi lúc 10:00:00, bấm lại lúc 10:00:45 → "Vui lòng chờ 16 giây nữa…"
            VerificationCode latestCode = verificationCodeDAO.findLatestByEmailAndPurpose(email, purpose);
            if (latestCode != null) {
                LocalDateTime allowedAt = latestCode.getSentAt().plusSeconds(AppConstants.CODE_RESEND_GAP_SECONDS);
                boolean isTooSoon = now.isBefore(allowedAt);
                if (isTooSoon) {
                    long secondsLeft = Duration.between(now, allowedAt).getSeconds() + 1;
                    return ServiceResult.fail(String.format(Messages.MSG70_CODE_RESEND_TOO_SOON, secondsLeft));
                }
            }

            // B4: CHẶN 3 — đếm số mã đã gửi tới email này trong 60 phút qua (mọi loại).
            // Nếu lần xin này là lần thứ 5 → khóa email luôn, không gửi.
            // VD: đã gửi 4 mã (sentCount = 4) → 4 + 1 = 5 ≥ 5 → khóa
            LocalDateTime windowStart = now.minusMinutes(AppConstants.CODE_REQUEST_WINDOW_MINUTES);
            int sentCount = verificationCodeDAO.countCodesSentSince(email, windowStart);
            boolean isTooManyRequests = sentCount + 1 >= maxAttempts;
            if (isTooManyRequests) {
                // Ghi giờ khóa vào dòng mã gần nhất → lần sau B2 (findLockedUntil) sẽ thấy và
                // chặn
                LocalDateTime newLockedUntil = now.plusMinutes(lockMinutes);
                if (latestCode != null) {
                    verificationCodeDAO.lockCode(latestCode.getVerificationCodeId(), newLockedUntil);
                }
                String remainingTime = formatRemainingTime(now, newLockedUntil);
                return ServiceResult
                        .fail(String.format(Messages.MSG61_VERIFICATION_LOCKED, lockMinutes, remainingTime));
            }

            // B5: qua hết 3 chặn → sinh mã 6 số ngẫu nhiên an toàn (SecureRandom), VD
            // "048213"
            String code = SecureCodeUtil.generateVerificationCode();

            // B6: GỬI MAIL TRƯỚC, ghi sổ sau. Gửi lỗi thì nhảy xuống catch
            // EmailSendException,
            // sổ không có dòng mã "ma" mà khách không bao giờ nhận được
            emailGateway.sendVerificationCode(email, code, validMinutes, getPurposeLabel(purpose));

            // B7: gửi được rồi mới ghi dòng mới vào sổ. Dòng mới này tự làm mã cũ mất hiệu
            // lực
            // (vì các hàm khác chỉ xét mã GỬI GẦN NHẤT)
            VerificationCode newCode = new VerificationCode(0);
            newCode.setAccountId(accountId);
            newCode.setEmail(email);
            newCode.setPurpose(purpose);
            newCode.setCode(code);
            newCode.setSentAt(now);
            newCode.setExpiresAt(now.plusMinutes(validMinutes));
            verificationCodeDAO.insert(newCode);

            // B8: báo thành công, VD "Mã đã được gửi tới thao.luong@gmail.com. Mã có hiệu
            // lực trong 15 phút."
            return ServiceResult.ok(null, String.format(Messages.MSG42_CODE_SENT, email, validMinutes));

        } catch (EmailSendException e) {
            // Gửi mail lỗi (sai App Password, mất mạng...) — lỗi thật EmailGateway đã in ra
            // console
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 2. Kiểm tra mã khách gõ =====

    /**
     * Kiểm tra mã 6 số khách vừa gõ. Đúng thì đánh dấu mã "đã dùng" (không dùng lại
     * được) và trả dòng mã về.
     *
     * Dùng khi: khách gõ mã rồi bấm "Xác nhận" ở Đăng ký, Kích hoạt hồ sơ, Quên mật
     * khẩu, Đổi email.
     *
     * VD: sổ có mã 246810 (hết hạn 10:15), khách gõ lúc 10:03
     * - gõ 111111 → sai lần 1 → MSG43 "Mã không đúng…"
     * - gõ 246810 → đúng → ghi UsedAt = 10:03 → trả dòng mã về
     *
     * @param email     email đã nhận mã (người gọi lấy từ session). VD
     *                  "thao.luong@gmail.com".
     * @param purpose   mã loại gì — phải cùng loại lúc gửi, VD RECOVERY.
     * @param inputCode chữ khách gõ vào ô mã, VD "246810" (có thể lẫn khoảng trắng
     *                  2 đầu).
     * @return kết quả:
     *         - Thành công: kèm dòng mã vừa dùng (có AccountId, ExpiresAt để người
     *         gọi làm bước tiếp).
     *         - Thất bại gắn ô "code": sai định dạng (MSG68_CODE_FORMAT), sai mã
     *         (MSG43),
     *         mã không còn dùng được (MSG44).
     *         - Thất bại chung: email đang khóa / vừa bị khóa do sai lần thứ 5
     *         (MSG61), lỗi DB (MSG04).
     */
    public ServiceResult<VerificationCode> verifyCode(String email, VerificationCode.Purpose purpose,
            String inputCode) {
        // B1: kiểm tra dạng mã trước (không cần hỏi DB). Gõ "12ab56" hay "12345" → báo
        // dưới ô,
        // KHÔNG tính là 1 lần nhập sai
        if (!ValidationUtil.isValidVerificationCode(inputCode)) {
            return ServiceResult.failField("code", Messages.MSG68_CODE_FORMAT);
        }
        String trimmedCode = inputCode.trim();

        try {
            // B2: lấy giờ hiện tại + tham số P-17 (mốc 5 lần sai), P-18 (khóa bao nhiêu
            // phút)
            LocalDateTime now = DateTimeUtil.now();
            int maxAttempts = systemConfigService.getInt(SystemConfigService.MAX_FAILED_ATTEMPTS);
            int lockMinutes = systemConfigService.getInt(SystemConfigService.LOCK_MINUTES);

            // B3: email đang bị khóa → không cho gõ mã, kể cả gõ đúng
            LocalDateTime lockedUntil = verificationCodeDAO.findLockedUntil(email, now);
            if (lockedUntil != null) {
                String remainingTime = formatRemainingTime(now, lockedUntil);
                return ServiceResult
                        .fail(String.format(Messages.MSG61_VERIFICATION_LOCKED, lockMinutes, remainingTime));
            }

            // B4: lấy mã GỬI GẦN NHẤT. 4 trường hợp dưới đây đều báo chung MSG44 "Mã đã hết
            // hạn. Bấm Gửi lại mã":
            // chưa từng gửi / đã dùng rồi / đã bị hủy vì sai đủ 5 lần / quá 15 phút
            VerificationCode latestCode = verificationCodeDAO.findLatestByEmailAndPurpose(email, purpose);
            boolean isNeverSent = latestCode == null;
            if (isNeverSent) {
                return ServiceResult.failField("code", Messages.MSG44_CODE_EXPIRED);
            }
            boolean isUsed = latestCode.getUsedAt() != null;
            boolean isCancelled = latestCode.getFailedAttempts() >= maxAttempts;
            boolean isExpired = !now.isBefore(latestCode.getExpiresAt());
            if (isUsed || isCancelled || isExpired) {
                return ServiceResult.failField("code", Messages.MSG44_CODE_EXPIRED);
            }

            // B5: so mã. SAI → cộng 1 lần sai vào dòng mã
            boolean isMatched = trimmedCode.equals(latestCode.getCode());
            if (!isMatched) {
                int verificationCodeId = latestCode.getVerificationCodeId();
                verificationCodeDAO.increaseFailedAttempts(verificationCodeId);

                // Đây là lần sai thứ 5 → ghi giờ khóa, báo MSG61. Chưa tới 5 → chỉ báo MSG43
                // dưới ô
                int newFailedAttempts = latestCode.getFailedAttempts() + 1;
                boolean isLockNow = newFailedAttempts >= maxAttempts;
                if (isLockNow) {
                    LocalDateTime newLockedUntil = now.plusMinutes(lockMinutes);
                    verificationCodeDAO.lockCode(verificationCodeId, newLockedUntil);
                    String remainingTime = formatRemainingTime(now, newLockedUntil);
                    return ServiceResult
                            .fail(String.format(Messages.MSG61_VERIFICATION_LOCKED, lockMinutes, remainingTime));
                }
                return ServiceResult.failField("code", Messages.MSG43_WRONG_CODE);
            }

            // B6: ĐÚNG → ghi UsedAt để mã không dùng lại được, trả dòng mã cho người gọi
            // làm bước tiếp
            verificationCodeDAO.markUsed(latestCode.getVerificationCodeId(), now);
            latestCode.setUsedAt(now);
            return ServiceResult.ok(latestCode);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 2b. Còn bao nhiêu lượt (chỉ đọc, để báo trước — MSG62–MSG65) =====

    /**
     * Tính khách còn mấy lần nhập sai mã và còn mấy lần bấm "Gửi lại mã" trước khi
     * bị khóa.
     * Hàm chỉ ĐỌC sổ, không ghi gì — việc khóa thật vẫn do sendCode / verifyCode
     * làm.
     *
     * VD (P-17 = 5): thao.luong vừa nhận mã, chưa gõ sai lần nào, trong 60 phút đã
     * có 2 mã được gửi
     * → còn 5 - 0 = 5 lần nhập; còn (5 - 1) - 2 = 2 lần gửi lại
     *
     * @param email   email đang xác thực, VD "thao.luong@gmail.com".
     * @param purpose mã loại gì, VD RECOVERY.
     * @return kết quả:
     *         - Thành công: kèm VerificationAttemptInfo gồm isLocked() (đang khóa
     *         chưa), getAttemptsLeft()
     *         (còn mấy lần nhập; 0 nếu mã đã dùng / hết hạn / chưa gửi),
     *         getResendsLeft() (còn mấy lần gửi lại),
     *         getLockMinutes() (khóa bao nhiêu phút, để ghép vào câu).
     *         - Thất bại: chỉ khi lỗi DB (MSG04).
     */
    public ServiceResult<VerificationAttemptInfo> getAttemptInfo(String email, VerificationCode.Purpose purpose) {
        try {
            // B1: giờ hiện tại + tham số P-17 (mốc 5 lần), P-18 (khóa bao nhiêu phút)
            LocalDateTime now = DateTimeUtil.now();
            int maxAttempts = systemConfigService.getInt(SystemConfigService.MAX_FAILED_ATTEMPTS);
            int lockMinutes = systemConfigService.getInt(SystemConfigService.LOCK_MINUTES);

            // B2: email có đang bị khóa xác thực không
            boolean isLocked = verificationCodeDAO.findLockedUntil(email, now) != null;

            // B3: còn mấy lần nhập mã = 5 − số lần đã sai của mã gần nhất.
            // Mã đã dùng / quá 15 phút / chưa gửi → 0 (không còn gì để nhập)
            VerificationCode latestCode = verificationCodeDAO.findLatestByEmailAndPurpose(email, purpose);
            boolean isCodeUsable = latestCode != null
                    && latestCode.getUsedAt() == null
                    && now.isBefore(latestCode.getExpiresAt());
            int attemptsLeft = isCodeUsable ? Math.max(0, maxAttempts - latestCode.getFailedAttempts()) : 0;

            // B4: còn mấy lần gửi lại = 4 − số mã đã gửi trong 60 phút.
            // Số 4 = 5 − 1, khớp đúng điều kiện khóa ở sendCode (lần xin thứ 5 mới khóa).
            // VD đã gửi 4 mã → còn 0 lần → bấm thêm 1 lần nữa là bị khóa
            LocalDateTime windowStart = now.minusMinutes(AppConstants.CODE_REQUEST_WINDOW_MINUTES);
            int sentCount = verificationCodeDAO.countCodesSentSince(email, windowStart);
            int resendsLeft = Math.max(0, (maxAttempts - 1) - sentCount);

            return ServiceResult.ok(new VerificationAttemptInfo(isLocked, attemptsLeft, resendsLeft, lockMinutes));

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 3. Hàm phụ =====

    /**
     * Đổi khoảng thời gian từ "bây giờ" tới 1 mốc thành chuỗi "mm:ss" để ghép vào
     * câu MSG61.
     *
     * @param now   giờ hiện tại, VD 10:07:30.
     * @param until mốc hết khóa, VD 10:10:00.
     * @return chuỗi phút:giây còn lại, VD "02:30".
     */
    private String formatRemainingTime(LocalDateTime now, LocalDateTime until) {
        long totalSeconds = Duration.between(now, until).getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Đổi loại mã sang cụm chữ tiếng Việt để ghép vào tiêu đề và nội dung email.
     *
     * @param purpose loại mã, VD RECOVERY.
     * @return cụm chữ tương ứng, VD RECOVERY → "khôi phục mật khẩu"
     *         (tiêu đề mail thành "[Super Gym] Mã xác thực khôi phục mật khẩu").
     */
    String getPurposeLabel(VerificationCode.Purpose purpose) {
        switch (purpose) {
            case SIGN_UP:
                return "đăng ký tài khoản";
            case CLAIM:
                return "kích hoạt hồ sơ";
            case RECOVERY:
                return "khôi phục mật khẩu";
            case EMAIL_CHANGE:
                return "đổi email";
            case PHONE_CHANGE:
                return "đổi số điện thoại";
            default:
                return "xác thực tài khoản";
        }
    }
}
