package com.qiao.admin.web;

import com.qiao.admin.domain.AdminRole;

import java.util.Set;

public record AdminLoginResponse(String token, Admin admin) {

    public record Admin(Long id, String email, Set<AdminRole> roles) {
    }
}
