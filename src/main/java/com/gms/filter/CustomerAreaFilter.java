package com.gms.filter;

import com.gms.constant.Role;
import jakarta.servlet.annotation.WebFilter;

/**
 * Chỉ cho vai trò Hội viên (Customer) vào các URL /customer/* (GBR-10). Logic chung nằm ở RoleAreaFilter.
 */
@WebFilter("/customer/*")
public class CustomerAreaFilter extends RoleAreaFilter {

    @Override
    protected Role getAllowedRole() {
        return Role.CUSTOMER;
    }
}
