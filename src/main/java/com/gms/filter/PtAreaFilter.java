package com.gms.filter;

import com.gms.constant.Role;
import jakarta.servlet.annotation.WebFilter;

/**
 * Chỉ cho vai trò Huấn luyện viên (PT) vào các URL /pt/* (GBR-10). Logic chung nằm ở RoleAreaFilter.
 */
@WebFilter("/pt/*")
public class PtAreaFilter extends RoleAreaFilter {

    @Override
    protected Role getAllowedRole() {
        return Role.PT;
    }
}
