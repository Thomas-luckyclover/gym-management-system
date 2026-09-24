package com.gms.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Sinh mã ngẫu nhiên an toàn: mã xác thực 6 số (DBR-03) và token cho link đặt mật khẩu (P-15),
 * và băm token trước khi lưu DB (cột VerificationCodes.TokenHash).
 * Dùng SecureRandom — KHÔNG dùng Math.random()/Random vì đoán trước được.
 */
public final class SecureCodeUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Token 32 byte ngẫu nhiên → chuỗi 43 ký tự, đủ để không thể đoán. */
    private static final int TOKEN_BYTES = 32;

    private SecureCodeUtil() {
    }

    /**
     * Sinh mã xác thực đúng 6 chữ số, giữ cả số 0 ở đầu (VD "048213").
     * @return chuỗi 6 chữ số
     */
    public static String generateVerificationCode() {
        int number = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    /**
     * Sinh token dùng trong URL (link đặt mật khẩu cho tài khoản do Lễ tân tạo — MSG38).
     * @return chuỗi Base64 an toàn cho URL, không có ký tự '=' ở cuối
     */
    public static String generateUrlToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(randomBytes);
    }

    /**
     * Băm token của link đặt mật khẩu bằng SHA-256 trước khi lưu vào VerificationCodes.TokenHash.
     * DB chỉ giữ mã băm: lỡ lộ DB thì cũng không ai dựng lại được link.
     * Khi khách mở link: băm token trên URL bằng đúng hàm này rồi tìm dòng có TokenHash trùng khớp.
     * @param token token gốc (sinh bằng generateUrlToken, gửi trong email)
     * @return chuỗi hex chữ thường 64 ký tự (khớp kiểu CHAR(64) của cột TokenHash)
     */
    public static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte hashByte : hashBytes) {
                hex.append(String.format("%02x", hashByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Mọi JDK đều có SHA-256 — chỉ xảy ra khi JDK hỏng
            throw new IllegalStateException("JDK không hỗ trợ SHA-256", e);
        }
    }
}
