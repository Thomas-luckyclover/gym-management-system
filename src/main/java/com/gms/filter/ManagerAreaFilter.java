package com.gms.filter;

import com.gms.constant.Role;
import jakarta.servlet.annotation.WebFilter;

/**
 * Chỉ cho vai trò Quản lý (Manager) vào các URL /manager/* (GBR-10). Logic chung nằm ở RoleAreaFilter.
 */
@WebFilter("/manager/*")
public class ManagerAreaFilter extends RoleAreaFilter {

    @Override
    protected Role getAllowedRole() {
        return Role.MANAGER;
    }
}
