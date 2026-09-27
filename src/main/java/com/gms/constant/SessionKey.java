package com.gms.constant;

/**
 * Tên các attribute lưu trong HttpSession — dùng chung cho cả nhóm, cấm tự gõ chuỗi "userId"... rải rác.
 *
 * Nguyên tắc (codeImplementationBackend.md Mục 11.1): chỉ lưu dữ liệu tối thiểu,
 * KHÔNG lưu cả object Account/Customer/Employee (có passwordHash) vào session.
 *
 * Trong JSP đọc bằng: ${sessionScope.role}, ${sessionScope.displayName}
 */
public final class SessionKey {

    /** Id của bảng Account (int) — dấu hiệu "đã đăng nhập". */
    public static final String ACCOUNT_ID = "accountId";

    /** Vai trò, lưu Role.getDbValue() (VD "Manager") — Filter phân quyền đọc key này. */
    public static final String ROLE = "role";

    /**
     * Tên hiển thị trên header/sidebar. Không nhạy cảm, lưu để khỏi query DB ở mỗi trang.
     * Khi user đổi tên (Lễ tân sửa hồ sơ) thì lần đăng nhập sau mới cập nhật — chấp nhận được.
     */
    public static final String DISPLAY_NAME = "displayName";

    /**
     * Thông báo toast mang qua lần redirect (mẫu Post/Redirect/Get).
     * Servlet gọi RequestUtil.setFlashToast(...), trang JSP kế tiếp hiện toast rồi xóa (common/flash-toast.jspf).
     */
    public static final String FLASH_TOAST = "flashToast";

    /** Quên mật khẩu bước 1 xong: email đã nhận mã khôi phục (String). */
    public static final String RECOVERY_EMAIL = "recoveryEmail";

    /** Quên mật khẩu bước 2 xong: AccountId đã xác minh mã (Integer) — chưa có thì không vào được bước 3. */
    public static final String RECOVERY_ACCOUNT_ID = "recoveryAccountId";

    /** Hạn làm bước 3 = hạn của mã vừa xác minh (LocalDateTime). */
    public static final String RECOVERY_DEADLINE = "recoveryDeadline";

    /**
     * Email điền sẵn ô bước 1 Quên mật khẩu khi khách mở link đặt mật khẩu đã hết hạn (String).
     * Dùng 1 lần: ForgotPasswordServlet đọc xong xóa ngay. Truyền qua session để email không nằm trên URL.
     */
    public static final String RECOVERY_PREFILL_EMAIL = "recoveryPrefillEmail";

    /** Thời gian sống của session: 2 tiếng không hoạt động (codeImplementationBackend.md Mục 11.1). */
    public static final int SESSION_TIMEOUT_SECONDS = 7200;

    private SessionKey() {
    }
}
