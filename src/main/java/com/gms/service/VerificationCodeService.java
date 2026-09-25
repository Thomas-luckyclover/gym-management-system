package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.dao.VerificationCodeDAO;
import com.gms.dto.ServiceResult;
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
 * Service cho mã xác thực (OTP 6 số) và link đặt mật khẩu — nơi RA QUYẾT ĐỊNH.
 * DAO chỉ đọc/ghi "cuốn sổ mã OTP"; Service đọc sổ lên rồi quyết định: cho gửi không, mã đúng không, có khóa không.
 * 
 * Luật áp dụng (01_Phase1_Rules.md mục 2.3, CBR-94):
 * - Mã có hiệu lực P-15 phút (mặc định 15)
 * - Gõ sai P-17 lần (mặc định 5) → hủy mã + khóa email P-18 phút (mặc định 5)
 * - Xin mã lần thứ 5 trong 60 phút → khóa email P-18 phút như trên
 * - 2 lần xin mã phải cách nhau ít nhất 60 giây
 * - Chỉ mã mới nhất có hiệu lực, mỗi mã dùng 1 lần
 *
 * @author Le Cong Minh
 */
public class VerificationCodeService {

    private final VerificationCodeDAO verificationCodeDAO = new VerificationCodeDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();
    private final EmailGateway emailGateway = new EmailGateway();

    // ===== 1. Gửi mã =====

    /**
     * Sinh mã 6 số mới, gửi qua email, rồi ghi vào sổ.
     *
     * Dùng khi: khách bấm "Gửi mã" hoặc "Gửi lại mã" (đăng ký, kích hoạt hồ sơ, quên mật khẩu, đổi email).
     * Trước khi gửi, kiểm tra lần lượt 3 điều — vướng điều nào thì dừng, KHÔNG gửi:
     *   1. Email có đang bị khóa không?              → khóa rồi: báo MSG61 kèm thời gian còn lại
     *   2. Đã đủ 60 giây kể từ lần gửi trước chưa?   → chưa đủ: báo chờ thêm bao nhiêu giây
     *   3. Trong 60 phút qua đã xin đủ 4 lần chưa?   → lần này là lần thứ 5: khóa email 5 phút
     *
     * Ví dụ: thao@gmail.com bấm "Gửi mã" lúc 10:00, không vướng gì
     *        → sinh mã 246810, gửi email, sổ thêm dòng: Recovery | 246810 | gửi 10:00 | hết hạn 10:15
     *        → trả về thành công kèm câu "Mã đã được gửi tới thao@gmail.com. Mã có hiệu lực trong 15 phút."
     *
     * Người gọi phải tự kiểm tra email đúng định dạng / có tồn tại trước (việc của màn đăng ký, quên mật khẩu...).
     *
     * @param email email nhận mã (đã kiểm tra hợp lệ)
     * @param purpose mục đích: SIGN_UP, CLAIM, RECOVERY, EMAIL_CHANGE
     * @param accountId Id tài khoản; null nếu khách đang đăng ký (chưa có tài khoản)
     * @return thành công kèm câu thông báo MSG42; thất bại kèm lý do (bị khóa, chưa đủ 60 giây, lỗi hệ thống)
     */
    public ServiceResult<Void> sendCode(String email, VerificationCode.Purpose purpose, Integer accountId) {
        try {
            // B1: lấy giờ hiện tại + đọc 3 tham số từ bảng SystemConfigs
            LocalDateTime now = DateTimeUtil.now();
            int validMinutes = systemConfigService.getInt(SystemConfigService.VERIFICATION_VALID_MINUTES);
            int maxAttempts = systemConfigService.getInt(SystemConfigService.MAX_FAILED_ATTEMPTS);
            int lockMinutes = systemConfigService.getInt(SystemConfigService.LOCK_MINUTES);

            // B2: kiểm tra 1 — email đang bị khóa thì không cho xin mã
            LocalDateTime lockedUntil = verificationCodeDAO.findLockedUntil(email, now);
            if (lockedUntil != null) {
                String remainingTime = formatRemainingTime(now, lockedUntil);
                return ServiceResult.fail(String.format(Messages.MSG61_VERIFICATION_LOCKED, lockMinutes, remainingTime));
            }

            // B3: kiểm tra 2 — lần gửi trước (cùng mục đích) phải cách bây giờ ít nhất 60 giây
            VerificationCode latestCode = verificationCodeDAO.findLatestByEmailAndPurpose(email, purpose);
            if (latestCode != null) {
                LocalDateTime allowedAt = latestCode.getSentAt().plusSeconds(AppConstants.CODE_RESEND_GAP_SECONDS);
                boolean isTooSoon = now.isBefore(allowedAt);
                if (isTooSoon) {
                    long secondsLeft = Duration.between(now, allowedAt).getSeconds() + 1;
                    return ServiceResult.fail(String.format(Messages.EXTRA_CODE_RESEND_TOO_SOON, secondsLeft));
                }
            }

            // B4: kiểm tra 3 — đếm số lần đã xin trong 60 phút qua; lần này là lần thứ 5 thì khóa
            // VD: đã xin 4 lần (sentCount = 4), lần này là lần thứ 5 → 4 + 1 >= 5 → khóa
            LocalDateTime windowStart = now.minusMinutes(AppConstants.CODE_REQUEST_WINDOW_MINUTES);
            int sentCount = verificationCodeDAO.countCodesSentSince(email, windowStart);
            boolean isTooManyRequests = sentCount + 1 >= maxAttempts;
            if (isTooManyRequests) {
                // Ghi giờ khóa vào dòng mã gần nhất để findLockedUntil ở B2 thấy được lần sau
                LocalDateTime newLockedUntil = now.plusMinutes(lockMinutes);
                if (latestCode != null) {
                    verificationCodeDAO.lockCode(latestCode.getVerificationCodeId(), newLockedUntil);
                }
                String remainingTime = formatRemainingTime(now, newLockedUntil);
                return ServiceResult.fail(String.format(Messages.MSG61_VERIFICATION_LOCKED, lockMinutes, remainingTime));
            }

            // B5: qua hết 3 kiểm tra → sinh mã 6 số mới
            String code = SecureCodeUtil.generateVerificationCode();

            // B6: gửi email TRƯỚC khi ghi sổ — gửi lỗi thì dừng, sổ không có dòng rác
            emailGateway.sendVerificationCode(email, code, validMinutes, getPurposeLabel(purpose));

            // B7: gửi được rồi mới ghi dòng mới vào sổ
            VerificationCode newCode = new VerificationCode(0);
            newCode.setAccountId(accountId);
            newCode.setEmail(email);
            newCode.setPurpose(purpose);
            newCode.setCode(code);
            newCode.setSentAt(now);
            newCode.setExpiresAt(now.plusMinutes(validMinutes));
            verificationCodeDAO.insert(newCode);

            // B8: báo thành công
            return ServiceResult.ok(null, String.format(Messages.MSG42_CODE_SENT, email, validMinutes));

        } catch (EmailSendException e) {
            // Gửi email lỗi (sai App Password, mất mạng...) — lỗi thật đã được EmailGateway in ra console
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 2. Kiểm tra mã khách gõ =====

    /**
     * Kiểm tra mã 6 số khách vừa gõ. Đúng thì đánh dấu mã "đã dùng" và trả về dòng mã đó.
     *
     * Dùng khi: khách gõ mã rồi bấm "Xác nhận" (đăng ký, kích hoạt hồ sơ, quên mật khẩu, đổi email).
     * Kiểm tra lần lượt:
     *   1. Mã gõ vào có đúng 6 chữ số không?                  → sai dạng: báo EXTRA_CODE_FORMAT dưới ô nhập
     *   2. Email có đang bị khóa không?                        → khóa rồi: báo MSG61
     *   3. Có mã nào còn dùng được không? (chưa hết hạn, chưa dùng, chưa bị hủy) → không: báo MSG44 (bấm Gửi lại mã)
     *   4. Mã gõ vào có khớp không?
     *        sai → cộng 1 lần sai; đủ 5 lần thì khóa email 5 phút (MSG61), chưa đủ thì báo MSG43
     *        đúng → đánh dấu đã dùng, trả về thành công
     *
     * Ví dụ: sổ có mã 246810 (hết hạn 10:15), khách gõ lúc 10:03
     *        - gõ 111111 → sai lần 1 → "Mã không đúng..."
     *        - gõ 246810 → đúng → UsedAt = 10:03 → trả về dòng mã (có AccountId để làm tiếp bước sau)
     *
     * @param email email đã nhận mã
     * @param purpose mục đích của mã
     * @param inputCode mã khách gõ
     * @return thành công kèm dòng mã đã dùng; thất bại kèm lý do
     */
    public ServiceResult<VerificationCode> verifyCode(String email, VerificationCode.Purpose purpose, String inputCode) {
        // B1: kiểm tra dạng mã trước (không cần hỏi DB) — sai thì báo lỗi ngay dưới ô "code"
        if (!ValidationUtil.isValidVerificationCode(inputCode)) {
            return ServiceResult.failField("code", Messages.EXTRA_CODE_FORMAT);
        }
        String trimmedCode = inputCode.trim();

        try {
            // B2: lấy giờ hiện tại + đọc tham số
            LocalDateTime now = DateTimeUtil.now();
            int maxAttempts = systemConfigService.getInt(SystemConfigService.MAX_FAILED_ATTEMPTS);
            int lockMinutes = systemConfigService.getInt(SystemConfigService.LOCK_MINUTES);

            // B3: email đang bị khóa thì không cho gõ mã
            LocalDateTime lockedUntil = verificationCodeDAO.findLockedUntil(email, now);
            if (lockedUntil != null) {
                String remainingTime = formatRemainingTime(now, lockedUntil);
                return ServiceResult.fail(String.format(Messages.MSG61_VERIFICATION_LOCKED, lockMinutes, remainingTime));
            }

            // B4: lấy mã mới nhất; không dùng được nữa (chưa gửi / đã dùng / đã bị hủy / hết hạn) → bảo gửi lại mã
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

            // B5: so mã — SAI thì cộng lần sai, đủ số lần thì khóa
            boolean isMatched = trimmedCode.equals(latestCode.getCode());
            if (!isMatched) {
                int verificationCodeId = latestCode.getVerificationCodeId();
                verificationCodeDAO.increaseFailedAttempts(verificationCodeId);

                int newFailedAttempts = latestCode.getFailedAttempts() + 1;
                boolean isLockNow = newFailedAttempts >= maxAttempts;
                if (isLockNow) {
                    LocalDateTime newLockedUntil = now.plusMinutes(lockMinutes);
                    verificationCodeDAO.lockCode(verificationCodeId, newLockedUntil);
                    String remainingTime = formatRemainingTime(now, newLockedUntil);
                    return ServiceResult.fail(String.format(Messages.MSG61_VERIFICATION_LOCKED, lockMinutes, remainingTime));
                }
                return ServiceResult.failField("code", Messages.MSG43_WRONG_CODE);
            }

            // B6: ĐÚNG → đánh dấu đã dùng để không dùng lại được, trả dòng mã cho người gọi làm tiếp
            verificationCodeDAO.markUsed(latestCode.getVerificationCodeId(), now);
            latestCode.setUsedAt(now);
            return ServiceResult.ok(latestCode);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 3. Kiểm tra link đặt mật khẩu =====

    /**
     * Kiểm tra link đặt mật khẩu khách vừa bấm còn dùng được không. Hàm này CHỈ KIỂM TRA, chưa đánh dấu đã dùng —
     * việc đánh dấu làm lúc khách đặt mật khẩu thành công (màn /set-password).
     *
     * Dùng khi: khách mở /set-password?token=... (tài khoản do Lễ tân tạo tại quầy — CBR-24, CBR-31).
     * Kiểm tra lần lượt: link có token không → tìm thấy trong sổ không → đã dùng chưa → hết hạn chưa.
     * Vướng bất kỳ điều nào → cùng 1 câu "link không còn hiệu lực, dùng Quên mật khẩu".
     *
     * Ví dụ: token "setup-nhung-phase1-2026" (seed, còn hạn, chưa dùng) → thành công, trả dòng có AccountId 113.
     *        token "setup-khanh-expired-2026" (seed, đã hết hạn)        → thất bại.
     *
     * @param token chuỗi token gốc lấy từ URL (?token=...) 
     * @return thành công kèm dòng link (có AccountId để biết đặt mật khẩu cho ai); thất bại kèm lý do
     */
    public ServiceResult<VerificationCode> findValidSetupLink(String token) {
        // B1: URL không có token → chắc chắn không hợp lệ
        if (ValidationUtil.isNullOrEmpty(token)) {
            return ServiceResult.fail(Messages.EXTRA_SETUP_LINK_INVALID);
        }

        try {
            // B2: DB không lưu token gốc, chỉ lưu mã băm → băm token trên URL rồi mới tìm
            String tokenHash = SecureCodeUtil.sha256Hex(token.trim());
            VerificationCode setupLink = verificationCodeDAO.findByTokenHash(tokenHash);

            // B3: không tìm thấy / đã dùng / hết hạn → không hợp lệ
            LocalDateTime now = DateTimeUtil.now();
            boolean isNotFound = setupLink == null;
            if (isNotFound) {
                return ServiceResult.fail(Messages.EXTRA_SETUP_LINK_INVALID);
            }
            boolean isUsed = setupLink.getUsedAt() != null;
            boolean isExpired = !now.isBefore(setupLink.getExpiresAt());
            if (isUsed || isExpired) {
                return ServiceResult.fail(Messages.EXTRA_SETUP_LINK_INVALID);
            }

            // B4: link còn dùng được
            return ServiceResult.ok(setupLink);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 4. Hàm phụ =====

    /**
     * Tính thời gian còn lại từ "bây giờ" tới 1 mốc, viết dạng mm:ss để ghép vào câu MSG61.
     * VD: bây giờ 10:07:30, khóa tới 10:10:00 → "02:30".
     */
    private String formatRemainingTime(LocalDateTime now, LocalDateTime until) {
        long totalSeconds = Duration.between(now, until).getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Đổi mục đích mã sang chữ tiếng Việt để ghi vào tiêu đề/nội dung email.
     * VD: RECOVERY → "khôi phục mật khẩu" → tiêu đề "[Super Gym] Mã xác thực khôi phục mật khẩu".
     */
    private String getPurposeLabel(VerificationCode.Purpose purpose) {
        switch (purpose) {
            case SIGN_UP:
                return "đăng ký tài khoản";
            case CLAIM:
                return "kích hoạt hồ sơ";
            case RECOVERY:
                return "khôi phục mật khẩu";
            case EMAIL_CHANGE:
                return "đổi email";
            default:
                return "xác thực tài khoản";
        }
    }
}