package com.qiao.admin.service;

import com.qiao.account.domain.AppUser;
import com.qiao.account.domain.UserRole;
import com.qiao.account.service.UserService;
import com.qiao.admin.domain.AdminUser;
import com.qiao.client.domain.SanctionsStatus;
import com.qiao.client.service.CompanyService;
import com.qiao.client.web.CompanyRequest;
import com.qiao.client.web.DeclarationsRequest;
import com.qiao.common.review.ReviewDecision;
import com.qiao.common.util.Locales;
import com.qiao.compliance.domain.ConsentType;
import com.qiao.compliance.service.ConsentService;
import com.qiao.compliance.service.LegalService;
import com.qiao.profile.domain.LanguageLevel;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.profile.web.ProfileRequest;
import com.qiao.project.domain.ProjectDeclarations;
import com.qiao.project.service.ProjectService;
import com.qiao.project.web.MilestoneRequest;
import com.qiao.project.web.ProjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Dev-only demo data ({@code APP_DEMO_SEED=true}): an approved client company, an approved engineer and one OPEN
 * project with two milestones. Runs through the normal services, once (skipped when the demo client exists).
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.demo-seed", havingValue = "true")
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    static final String CLIENT_EMAIL = "client@demo.qiao";
    static final String ENGINEER_EMAIL = "engineer@demo.qiao";
    private static final String NOTE = "Demo data";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserService userService;
    private final ConsentService consentService;
    private final CompanyService companyService;
    private final EngineerProfileService profileService;
    private final ProjectService projectService;
    private final AdminUserService adminUserService;
    private final LegalService legalService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DemoDataSeeder(UserService userService, ConsentService consentService, CompanyService companyService,
                          EngineerProfileService profileService, ProjectService projectService,
                          AdminUserService adminUserService, LegalService legalService,
                          TransactionTemplate transactionTemplate, Clock clock) {
        this.userService = userService;
        this.consentService = consentService;
        this.companyService = companyService;
        this.profileService = profileService;
        this.projectService = projectService;
        this.adminUserService = adminUserService;
        this.legalService = legalService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.findByEmail(CLIENT_EMAIL).isPresent()) {
            log.info("Demo data already present");
            return;
        }
        AdminUser admin = adminUserService.firstSuperadmin().orElse(null);
        if (admin == null) {
            log.warn("Demo data needs a SUPERADMIN (set ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD)");
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            long clientId = seedClient(admin.getId());
            seedEngineer(admin.getId());
            seedProject(clientId, admin.getId());
        });
        log.info("Demo data created: {} / {}", CLIENT_EMAIL, ENGINEER_EMAIL);
    }

    private long seedClient(long adminId) {
        AppUser client = userService.create(CLIENT_EMAIL, UserRole.CLIENT, Locales.EN);
        grant(client.getId(), ConsentType.TERMS, ConsentType.PRIVACY);
        companyService.getOrCreateForUser(client.getId());
        companyService.update(client.getId(), new CompanyRequest("Demo Client Inc.", "US", "https://example.com", "11-50"));
        companyService.acceptDeclarations(client.getId(),
                new DeclarationsRequest(legalService.currentVersion(LegalService.CLIENT_DECLARATIONS), true));
        companyService.review(companyService.companyOf(client.getId()).getId(), ReviewDecision.APPROVE,
                SanctionsStatus.CLEAR, NOTE, adminId);
        return client.getId();
    }

    private void seedEngineer(long adminId) {
        AppUser engineer = userService.create(ENGINEER_EMAIL, UserRole.ENGINEER, Locales.ZH_CN);
        grant(engineer.getId(), ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.CROSS_BORDER_PROCESSING,
                ConsentType.SHARE_PROFILE_PSEUDONYMIZED);
        profileService.getOrCreate(engineer.getId());
        profileService.update(engineer.getId(), new ProfileRequest(
                "全栈工程师 · Vue / Spring Boot",
                "八年 Web 与后端开发经验，专注于 SaaS 产品交付。",
                List.of("Vue", "TypeScript", "Spring Boot", "MySQL"), 8, "广东", LanguageLevel.GOOD,
                LanguageLevel.BASIC, new BigDecimal("35.00"), 30,
                "2018 至今：独立开发者，交付 20 余个 Web 项目。", "计算机科学学士",
                List.of("https://example.com/portfolio"), true));
        profileService.submit(engineer.getId());
        profileService.review(profileService.getByUserId(engineer.getId()).getId(), ReviewDecision.APPROVE, NOTE,
                "PARTNER-DEMO-001", adminId);
    }

    private void seedProject(long clientId, long adminId) {
        LocalDate start = LocalDate.now(clock).plusDays(7);
        ProjectRequest request = new ProjectRequest(
                "Customer portal redesign",
                "Redesign our customer self-service portal: new data model, REST API and a responsive Vue front end.",
                "web-dev", List.of("Vue", "TypeScript", "REST"), new BigDecimal("4000.00"), start, start.plusMonths(3),
                List.of(new MilestoneRequest("API and data model",
                                "OpenAPI specification and backend endpoints deployed to staging.", start.plusMonths(1),
                                new BigDecimal("1500.00")),
                        new MilestoneRequest("Web front end",
                                "Responsive Vue front end connected to the API, with end-to-end tests.",
                                start.plusMonths(3), new BigDecimal("2500.00"))),
                new ProjectDeclarations(true, true, true, true), true);
        long projectId = projectService.create(clientId, request).id();
        projectService.review(projectId, ReviewDecision.APPROVE, NOTE, adminId);
    }

    private void grant(long userId, ConsentType... types) {
        for (ConsentType type : types) {
            consentService.record(userId, type, true, null, null);
        }
    }
}
