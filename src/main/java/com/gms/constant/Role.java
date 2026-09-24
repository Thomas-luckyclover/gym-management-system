package com.gms.constant;

/**
 * Vai trò người dùng trong hệ thống (GBR-10, SRS 4.1 Actors).
 * Giá trị lưu DB là tiếng Anh, hiển thị ra giao diện là tiếng Việt (GBR-01).
 * Guest không có trong enum vì Guest là người chưa đăng nhập, không có Account.
 *
 * LƯU Ý: dbValue phải khớp đúng giá trị cột Position (Employee) trong database.
 * Nếu database đang lưu khác (VD: "Personal Trainer"), sửa dbValue ở đây — không sửa rải rác nơi khác.
 */
public enum Role {

    ADMIN("Admin", "Quản trị viên", "/admin/dashboard"),
    MANAGER("Manager", "Quản lý", "/manager/dashboard"),
    PT("PT", "Huấn luyện viên", "/pt/dashboard"),
    RECEPTIONIST("Receptionist", "Lễ tân", "/receptionist/check-in"),
    CUSTOMER("Customer", "Hội viên", "/customer/dashboard");

    private final String dbValue;
    private final String displayName;
    private final String homePath;

    Role(String dbValue, String displayName, String homePath) {
        this.dbValue = dbValue;
        this.displayName = displayName;
        this.homePath = homePath;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Trang chính của vai trò — nơi chuyển tới sau khi đăng nhập thành công (khi không có returnUrl).
     * @return đường dẫn tính từ context path, VD "/customer/dashboard"
     */
    public String getHomePath() {
        return homePath;
    }

    /**
     * Đổi giá trị lưu trong DB/session thành enum Role.
     * @param dbValue giá trị chuỗi, VD "Manager"
     * @return Role tương ứng, hoặc null nếu không khớp vai trò nào
     */
    public static Role fromDbValue(String dbValue) {
        for (Role role : values()) {
            boolean isMatched = role.dbValue.equalsIgnoreCase(dbValue);
            if (isMatched) {
                return role;
            }
        }
        return null;
    }
}
