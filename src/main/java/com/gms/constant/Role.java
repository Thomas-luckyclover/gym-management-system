package com.gms.constant;

/**
 * VAI TRÒ NGƯỜI DÙNG (GBR-10, SRS 4.1 Actors) — 5 vai trò cố định, mỗi vai trò mang 3 thông tin.
 * Guest không có trong enum: Guest là người chưa đăng nhập, không có Account.
 *
 *   Hằng số       dbValue          displayName        homePath
 *   ADMIN         "Admin"          "Quản trị viên"    /admin/dashboard
 *   MANAGER       "Manager"        "Quản lý"          /manager/dashboard
 *   PT            "PT"             "Huấn luyện viên"  /pt/dashboard
 *   RECEPTIONIST  "Receptionist"   "Lễ tân"           /receptionist/check-in
 *   CUSTOMER      "Customer"       "Hội viên"         /customer/dashboard
 *
 * NHỚ NHANH:  dbValue = chữ MÁY đọc (DB, session, form) · displayName = chữ NGƯỜI đọc (giao diện)
 *             homePath = trang chủ của vai trò (chuyển tới sau khi đăng nhập / chọn vai trò)
 */
public enum Role {

    ADMIN("Admin", "Quản trị viên", "/admin/dashboard"),
    MANAGER("Manager", "Quản lý", "/manager/dashboard"),
    PT("PT", "Huấn luyện viên", "/pt/dashboard"),
    RECEPTIONIST("Receptionist", "Lễ tân", "/receptionist/check-in"),
    CUSTOMER("Customer", "Hội viên", "/customer/dashboard");

    /**
     * dbValue — CHỮ TIẾNG ANH dùng để LƯU và SO SÁNH (người dùng không nhìn thấy).
     *   VD: "PT", "Customer"
     *   Phải khớp đúng cột Employees.Position trong DB (DB lưu khác thì sửa ở đây, không sửa rải rác).
     *   Dùng ở:
     *     - DB                 Employees.Position = 'PT'  → AuthService.fetchPersonProfile đổi thành enum
     *     - session ROLE        "PT"                       → vai trò đang dùng (SelectRoleServlet doPost B6)
     *     - session AVAILABLE_ROLES  "PT,Customer"         → menu biết có hiện "Đổi vai trò" không
     *     - ô ẩn "role"         select-role.jsp            → gửi lên doPost khi bấm nút
     *     - thẻ tài khoản        UserBadgeService           → chọn bảng / tiền tố mã (PT-0006, KH-1017)
     */
    private final String dbValue;

    /**
     * displayName — CHỮ TIẾNG VIỆT để HIỂN THỊ cho người dùng (GBR-01).
     *   VD: "Huấn luyện viên", "Hội viên"
     *   Dùng ở:
     *     - chữ trên nút vai trò       select-role.jsp (r.displayName)
     *     - thẻ tài khoản góc phải      "PT-0006 · Huấn luyện viên" (UserBadgeService.roleLabel)
     *     - toast đổi vai trò           MSG75 "Bạn đang làm việc với vai trò Huấn luyện viên"
     */
    private final String displayName;

    /**
     * homePath — TRANG CHỦ của vai trò (tính từ context path, chưa có "/gym-management-system").
     *   VD: "/pt/dashboard"
     *   Dùng ở:
     *     - đăng nhập xong, chỉ 1 vai trò, không có returnUrl  → về trang này (AuthService.login B9)
     *     - chọn vai trò xong, returnUrl không hợp lệ          → về trang này (SelectRoleServlet doPost B8)
     *     - tính KHU VỰC vai trò: "/pt/dashboard" → "/pt/"     → isInRoleArea kiểm returnUrl có thuộc khu vực không
     */
    private final String homePath;

    /** Constructor của enum: chỉ chạy 5 lần lúc nạp class, gán 3 giá trị viết trong ngoặc ở trên. */
    Role(String dbValue, String displayName, String homePath) {
        this.dbValue = dbValue;
        this.displayName = displayName;
        this.homePath = homePath;
    }

    /** "PT" — JSP gọi bằng ${r.dbValue}. */
    public String getDbValue() {
        return dbValue;
    }

    /** "Huấn luyện viên" — JSP gọi bằng ${r.displayName}. */
    public String getDisplayName() {
        return displayName;
    }

    /** "/pt/dashboard" — nơi chuyển tới sau khi đăng nhập / chọn vai trò (khi không có returnUrl hợp lệ). */
    public String getHomePath() {
        return homePath;
    }

    /**
     * CHIỀU NGƯỢC: chữ → enum.  "PT" → Role.PT.
     *   Không phân biệt hoa thường ("pt" cũng ra Role.PT). Không khớp vai trò nào (hoặc null) → trả null.
     *   Dùng ở:
     *     - đọc Employees.Position từ DB      (AuthService.fetchPersonProfile)
     *     - đọc ô ẩn "role" gửi lên           (SelectRoleServlet doPost B3 — giá trị lạ → null → MSG07)
     *     - đọc chuỗi session "PT,Customer"   (SelectRoleServlet.splitRoles)
     */
    public static Role fromDbValue(String dbValue) {
        for (Role role : values()) {                               // duyệt 5 vai trò
            boolean isMatched = role.dbValue.equalsIgnoreCase(dbValue);
            if (isMatched) {
                return role;
            }
        }
        return null;
    }
}