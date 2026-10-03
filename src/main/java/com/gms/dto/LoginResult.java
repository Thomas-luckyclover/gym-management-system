package com.gms.dto;

import com.gms.constant.Role;

import java.util.Collections;
import java.util.List;

/**
 * DTO chứa thông tin trả về sau khi đăng nhập thành công.
 *
 * <p>
 * Cố ý KHÔNG chứa đối tượng Account: Account có PasswordHash và có setter,
 * không được đưa ra
 * khỏi tầng Service (DBR-02, SessionKey: "không lưu cả object Account vào
 * session").
 * Servlet chỉ cần accountId, role, displayName để tạo session.
 * </p>
 */
public class LoginResult {

    private final int accountId;
    private final int personId;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String avatar;
    private final Role role;
    private final String redirectUrl;
    /** Vai trò còn hiệu lực của tài khoản (DBR-65). */
    private final List<Role> availableRoles;

    public LoginResult(int accountId, int personId, String fullName, String email,
            String phone, String avatar, Role role, String redirectUrl) {
        this(accountId, personId, fullName, email, phone, avatar, role, redirectUrl,
                role == null ? Collections.emptyList() : List.of(role));
    }

    public LoginResult(int accountId, int personId, String fullName, String email,
            String phone, String avatar, Role role, String redirectUrl, List<Role> availableRoles) {
        this.accountId = accountId;
        this.personId = personId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.avatar = avatar;
        this.role = role;
        this.redirectUrl = redirectUrl;
        this.availableRoles = List.copyOf(availableRoles);
    }

    public int getAccountId() {
        return accountId;
    }

    public int getPersonId() {
        return personId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAvatar() {
        return avatar;
    }

    public Role getRole() {
        return role;
    }

    /**
     * Trang chính của vai trò (Role.getHomePath()), dùng khi không có returnUrl hợp
     * lệ.
     */
    public String getRedirectUrl() {
        return redirectUrl;
    }

    public List<Role> getAvailableRoles() {
        return availableRoles;
    }

    /** Có từ 2 vai trò trở lên (CBR-113). */
    public boolean isRoleChoiceRequired() {
        return availableRoles.size() > 1;
    }
}
