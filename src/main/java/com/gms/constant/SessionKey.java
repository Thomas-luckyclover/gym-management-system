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

    /** Thời gian sống của session: 2 tiếng không hoạt động (codeImplementationBackend.md Mục 11.1). */
    public static final int SESSION_TIMEOUT_SECONDS = 7200;

    private SessionKey() {
    }
}
