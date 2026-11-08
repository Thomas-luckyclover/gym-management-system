package com.gms.dto;

/**
 * DTO — Service TẠO: VerificationCodeService.getAttemptInfo (đọc VerificationCodeDAO).
 * Không in thẳng ra JSP: ForgotPasswordServlet.setCodeTimers đọc rồi đổi thành câu gợi ý dưới ô mã (bước "code").
 */
public class VerificationAttemptInfo {
    // ===== DÙNG ĐỂ ĐIỀU KHIỂN HIỂN THỊ (bước nhập mã) =====
    // locked ← VerificationCodeDAO.findLockedUntil có giá trị → đang khóa: Servlet KHÔNG hiện 2 câu gợi ý
    private final boolean locked;
    // attemptsLeft ← số lần nhập sai còn lại của mã mới nhất → attemptHint "Còn 3 lần nhập" (=1: chữ vàng MSG63)
    private final int attemptsLeft;
    // resendsLeft ← số lần gửi mã còn lại trong khung giờ (countCodesSentSince) → resendHint (=0: chữ vàng MSG65)
    private final int resendsLeft;
    // lockMinutes ← số phút bị khóa nếu dùng hết lượt → chèn vào câu MSG63 / MSG65
    private final int lockMinutes;

    public VerificationAttemptInfo(boolean locked, int attemptsLeft, int resendsLeft, int lockMinutes) {
        this.locked = locked;
        this.attemptsLeft = attemptsLeft;
        this.resendsLeft = resendsLeft;
        this.lockMinutes = lockMinutes;
    }

    public boolean isLocked() {
        return locked;
    }

    public int getAttemptsLeft() {
        return attemptsLeft;
    }

    public int getResendsLeft() {
        return resendsLeft;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }
}
