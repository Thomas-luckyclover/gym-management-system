package com.gms.dto;

/**
 * Số lượt còn lại trước khi email bị khóa xác thực (CBR-94) — để màn nhập mã báo trước cho khách (MSG62–MSG65).
 *
 * Ai tạo: VerificationCodeService.getAttemptInfo (chỉ đọc database, không ghi gì).
 * Ai dùng: ForgotPasswordServlet đổi các con số thành câu gợi ý, JSP chỉ in ra.
 *
 * VD (P-17 = 5, P-18 = 5): mới nhận mã, chưa gõ sai, trong 60 phút đã gửi 2 mã
 *     → locked = false, attemptsLeft = 5, resendsLeft = 2, lockMinutes = 5
 *
 * @author Le Cong Minh
 */
public class VerificationAttemptInfo {

    private final boolean locked;
    private final int attemptsLeft;
    private final int resendsLeft;
    private final int lockMinutes;

    /**
     * Tạo thông tin số lượt còn lại.
     *
     * @param locked       true nếu email ĐANG bị khóa xác thực.
     * @param attemptsLeft còn mấy lần được gõ sai mã hiện tại (0 nếu mã đã dùng / hết hạn / chưa gửi).
     * @param resendsLeft  còn mấy lần được bấm "Gửi lại mã" trong 60 phút mà chưa bị khóa.
     * @param lockMinutes  nếu bị khóa thì khóa bao nhiêu phút (P-18), để ghép vào câu MSG63 / MSG65.
     */
    public VerificationAttemptInfo(boolean locked, int attemptsLeft, int resendsLeft, int lockMinutes) {
        this.locked = locked;
        this.attemptsLeft = attemptsLeft;
        this.resendsLeft = resendsLeft;
        this.lockMinutes = lockMinutes;
    }

    /**
     * @return true nếu email đang bị khóa xác thực — lúc đó trang chỉ hiện MSG61, không hiện gợi ý.
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * @return số lần còn được gõ sai mã, VD 3. Bằng 0 khi mã đã dùng, đã hết hạn hoặc chưa gửi mã nào.
     */
    public int getAttemptsLeft() {
        return attemptsLeft;
    }

    /**
     * @return số lần còn được bấm "Gửi lại mã" trong 60 phút, VD 2. Bằng 0 nghĩa là bấm thêm 1 lần nữa sẽ bị khóa.
     */
    public int getResendsLeft() {
        return resendsLeft;
    }

    /**
     * @return số phút sẽ bị khóa (P-18), VD 5.
     */
    public int getLockMinutes() {
        return lockMinutes;
    }
}
