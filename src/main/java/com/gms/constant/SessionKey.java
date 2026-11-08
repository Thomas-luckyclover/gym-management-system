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

    /** Các vai trò của tài khoản, dạng "PT,Customer" (CBR-113). */
    public static final String AVAILABLE_ROLES = "availableRoles";

    /** Thay đổi email / SĐT đang chờ mã, lưu phía server (CBR-110). */
    public static final String PENDING_CONTACT_FIELD = "pendingContactField";
    public static final String PENDING_CONTACT_VALUE = "pendingContactValue";
    public static final String PENDING_CONTACT_VERIFY_EMAIL = "pendingContactVerifyEmail";

    /**
     * Tên hiển thị trên header/sidebar. Không nhạy cảm, lưu để khỏi query DB ở mỗi trang.
     * Khi user đổi tên (Lễ tân sửa hồ sơ) thì lần đăng nhập sau mới cập nhật — chấp nhận được.
     */
    public static final String DISPLAY_NAME = "displayName";

    /**
     * Đường dẫn ảnh đại diện hiển thị trên header/sidebar (/uploads/avatar/...).
     * Đồng bộ với cột Persons.Avatar trong DB khi đăng nhập hoặc khi đổi avatar.
     */
    public static final String AVATAR = "avatar";

    /**
     * Thẻ tài khoản góc phải mọi màn (common/_user-badge.jspf), AuthFilter → UserBadgeService.ensure điền 1 lần / vai trò:
     *   USER_CODE        mã hiển thị theo vai trò: KH-1006, PT-0005, LT-0003, QL-0002, QT-0001 (số lấy từ mã gốc trong DB)
     *   USER_ROLE_LABEL  chữ vai trò: Hội viên, Huấn luyện viên, Lễ tân, Quản lý, Quản trị viên
     *   USER_INITIALS    chữ viết tắt tên cho avatar: "Lê Thị Thanh Tâm" → "LT"
     *   USER_BADGE_ROLE  vai trò đã tính 3 giá trị trên — đổi vai trò (select-role) thì tính lại
     */
    public static final String USER_CODE = "userCode";
    public static final String USER_ROLE_LABEL = "userRoleLabel";
    public static final String USER_INITIALS = "userInitials";
    public static final String USER_BADGE_ROLE = "userBadgeRole";
    /**
     * Thông báo toast mang qua lần redirect (mẫu Post/Redirect/Get).
     * Servlet gọi RequestUtil.setFlashToast(...), trang JSP kế tiếp hiện toast rồi xóa (common/flash-toast.jspf).
     */
    public static final String FLASH_TOAST = "flashToast";

    /** Kết quả duyệt nhiều đơn, danh sách đọc và xóa sau redirect để không gửi lại POST khi tải lại. */
    public static final String LEAVE_BULK_RESULT = "leaveBulkResult";

    /** CSRF token gắn với session để chống tấn công Cross-Site Request Forgery (TC-17A). */
    public static final String CSRF_TOKEN = "csrfToken";

    /** Quên mật khẩu bước 1 xong: email đã nhận mã khôi phục (String). */
    public static final String RECOVERY_EMAIL = "recoveryEmail";

    /** Quên mật khẩu bước 2 xong: AccountId đã xác minh mã (Integer) — chưa có thì không vào được bước 3. */
    public static final String RECOVERY_ACCOUNT_ID = "recoveryAccountId";

    /** Hạn làm bước 3 = hạn của mã vừa xác minh (LocalDateTime). */
    public static final String RECOVERY_DEADLINE = "recoveryDeadline";


    /** Thời gian sống của session: 2 tiếng không hoạt động (codeImplementationBackend.md Mục 11.1). */
    public static final int SESSION_TIMEOUT_SECONDS = 7200;

    private SessionKey() {
    }
}
