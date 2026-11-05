package com.qiao.admin.web;

import com.qiao.admin.domain.AdminRole;
import com.qiao.admin.domain.AdminUser;

import java.util.Set;

public record AdminLoginResponse(String token, Admin admin) {

    public record Admin(Long id, String email, Set<AdminRole> roles, boolean totpEnabled) {

        public static Admin from(AdminUser a) {
            return new Admin(a.getId(), a.getEmail(), a.roleSet(), a.getTotpSecret() != null);
        }
    }
}
