package com.gms.filter;

import com.gms.constant.Role;
import jakarta.servlet.annotation.WebFilter;

/**
 * Chỉ cho vai trò Quản trị viên (Admin) vào các URL /admin/* (GBR-10). Logic chung nằm ở RoleAreaFilter.
 */
@WebFilter("/admin/*")
public class AdminAreaFilter extends RoleAreaFilter {

    @Override
    protected Role getAllowedRole() {
        return Role.ADMIN;
    }
}
