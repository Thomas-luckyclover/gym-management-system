package com.gms.util;

import java.security.SecureRandom;

/**
 * Sinh mã ngẫu nhiên an toàn: mã xác thực 6 số (DBR-03).
 * Dùng SecureRandom — KHÔNG dùng Math.random()/Random vì đoán trước được.
 */
public final class SecureCodeUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
}
