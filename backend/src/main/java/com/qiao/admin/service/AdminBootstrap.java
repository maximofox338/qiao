package com.qiao.admin.service;

import com.qiao.admin.domain.AdminRole;
import com.qiao.compliance.service.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates the first SUPERADMIN from {@code ADMIN_BOOTSTRAP_EMAIL}/{@code ADMIN_BOOTSTRAP_PASSWORD} when no admin exists.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserService adminUserService;
    private final AdminProperties properties;

    public AdminBootstrap(AdminUserService adminUserService, AdminProperties properties) {
        this.adminUserService = adminUserService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!adminUserService.noneExists()) {
            return;
        }
        String email = properties.bootstrapEmail();
        String password = properties.bootstrapPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No admin exists and ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD are not set");
            return;
        }
        adminUserService.create(email, password, Set.of(AdminRole.SUPERADMIN), Actor.system());
        log.info("Bootstrap SUPERADMIN {} created", email);
    }
}
