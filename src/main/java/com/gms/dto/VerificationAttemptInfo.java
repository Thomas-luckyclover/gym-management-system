package com.gms.dto;

/**
 * Số lần còn lại khi nhập / xin mã xác thực của 1 email (CBR-94), để màn hình báo trước khi bị khóa (MSG62–MSG65).
 */
public class VerificationAttemptInfo {

    private final boolean locked;
    private final int attemptsLeft;
    private final int resendsLeft;
    private final int lockMinutes;

    public VerificationAttemptInfo(boolean locked, int attemptsLeft, int resendsLeft, int lockMinutes) {
        this.locked = locked;
        this.attemptsLeft = attemptsLeft;
        this.resendsLeft = resendsLeft;
        this.lockMinutes = lockMinutes;
    }

    /** Email đang bị khóa xác thực (MSG61) — màn hình không hiện số lần còn lại. */
    public boolean isLocked() {
        return locked;
    }

    /** Số lần còn được nhập mã hiện tại (0 nếu chưa có mã / mã đã dùng / đã hết hạn). */
    public int getAttemptsLeft() {
        return attemptsLeft;
    }

    /** Số lần còn được xin mã mới trong 60 phút trước khi lần xin kế tiếp bị khóa. */
    public int getResendsLeft() {
        return resendsLeft;
    }

    /** Thời gian khóa (P-18, phút) để ghép vào MSG63 / MSG65. */
    public int getLockMinutes() {
        return lockMinutes;
    }
}
