package com.gms.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Hash và kiểm tra mật khẩu bằng BCrypt (agents.md Phần 1 — bắt buộc, cấm plaintext/MD5/SHA không salt).
 * Chỉ gọi từ Service.
 */
public final class PasswordUtil {

    /**
     * Độ khó BCrypt (2^10 vòng). Đủ an toàn cho đồ án, đăng nhập vẫn nhanh (~0,1 giây).
     * Đổi số này không làm hỏng hash cũ — BCrypt tự đọc độ khó lưu trong chính chuỗi hash.
     */
    private static final int BCRYPT_LOG_ROUNDS = 10;

    private PasswordUtil() {
    }

    /**
     * Hash mật khẩu trước khi lưu DB.
     * @param rawPassword mật khẩu người dùng nhập (chưa hash)
     * @return chuỗi hash 60 ký tự dạng "$2a$10$..." — cột DB cần NVARCHAR(60) trở lên
     */
    public static String hashPassword(String rawPassword) {
        String salt = BCrypt.gensalt(BCRYPT_LOG_ROUNDS);
        return BCrypt.hashpw(rawPassword, salt);
    }

    /**
     * So khớp mật khẩu nhập vào với hash trong DB.
     * @param rawPassword mật khẩu người dùng nhập
     * @param passwordHash hash lấy từ DB (có thể null nếu tài khoản chưa đặt mật khẩu — MSG38)
     * @return true nếu khớp; false nếu không khớp, hash null hoặc hash sai định dạng
     */
    public static boolean checkPassword(String rawPassword, String passwordHash) {
        boolean isMissingInput = rawPassword == null || passwordHash == null;
        if (isMissingInput) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, passwordHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
