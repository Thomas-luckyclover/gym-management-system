package com.gms.filter;

import com.gms.constant.Role;
import jakarta.servlet.annotation.WebFilter;

/**
 * Chỉ cho vai trò Lễ tân (Receptionist) vào các URL /receptionist/* (GBR-10). Logic chung nằm ở RoleAreaFilter.
 */
@WebFilter("/receptionist/*")
public class ReceptionistAreaFilter extends RoleAreaFilter {

    @Override
    protected Role getAllowedRole() {
        return Role.RECEPTIONIST;
    }
}
