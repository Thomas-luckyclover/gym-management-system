package com.gms.entity;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng VerificationCodes — mã 6 số gửi qua email, HOẶC link đặt mật khẩu (Purpose PasswordSetup).
 * Dùng chung cho Đăng ký, Kích hoạt hồ sơ, Quên mật khẩu, Đổi email, Đặt mật khẩu qua link
 *
 * @author Le Cong Minh
 */

public class VerificationCode {

    private final int verificationCodeId;
    private Integer accountId;
    private String email;
    private Purpose purpose;
    private String code;
    private String tokenHash;
    private LocalDateTime sentAt;
    private LocalDateTime expiresAt;
    private int failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime usedAt;

    /**
     * @param verificationCodeId id định danh (khóa chính, bất biến)
     */
    public VerificationCode(int verificationCodeId) {
        this.verificationCodeId = verificationCodeId;
    }

    // ===== Getter/Setter =====

    public int getVerificationCodeId() {
        return verificationCodeId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public void setPurpose(Purpose purpose) {
        this.purpose = purpose;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    /**
     * Mục đích của mã xác thực / token link đặt mật khẩu — phân biệt dòng dữ liệu này
     * phục vụ luồng nào (đăng ký, kích hoạt, quên mật khẩu, đổi email, hay đặt mật khẩu qua link).
     * dbValue phải khớp đúng CHECK constraint CK_VerificationCodes_Purpose.
     */
    public enum Purpose {

        SIGN_UP("SignUp"),
        CLAIM("Claim"),
        RECOVERY("Recovery"),
        EMAIL_CHANGE("EmailChange"),
        PASSWORD_SETUP("PasswordSetup");

        private final String dbValue;

        Purpose(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        /**
         * @param dbValue giá trị chuỗi đọc từ ResultSet, VD "SignUp"
         * @return Purpose tương ứng, hoặc null nếu không khớp giá trị nào
         */
        public static Purpose fromDbValue(String dbValue) {
            for (Purpose purpose : values()) {
                if (purpose.dbValue.equalsIgnoreCase(dbValue)) {
                    return purpose;
                }
            }
            return null;
        }
    }
}