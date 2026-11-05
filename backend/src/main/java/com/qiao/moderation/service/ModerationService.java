package com.qiao.moderation.service;

import com.qiao.account.domain.AppUser;
import com.qiao.account.service.UserService;
import com.qiao.billing.service.PaymentService;
import com.qiao.client.domain.ClientCompany;
import com.qiao.client.domain.CompanyStatus;
import com.qiao.client.domain.SanctionsStatus;
import com.qiao.client.service.CompanyService;
import com.qiao.client.web.AdminCompanyView;
import com.qiao.common.review.ReviewDecision;
import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.service.MilestoneService;
import com.qiao.contract.web.DisputeView;
import com.qiao.notify.service.MailTemplate;
import com.qiao.notify.service.Notifier;
import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.profile.web.AdminEngineerView;
import com.qiao.project.domain.Project;
import com.qiao.project.service.ProjectService;
import com.qiao.project.web.AdminProjectView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Back-office review decisions. The owning modules apply the state transition and audit it; this service adds the
 * cross-module consequences and notifies the users concerned.
 */
@Service
public class ModerationService {

    private final CompanyService companyService;
    private final EngineerProfileService profileService;
    private final ProjectService projectService;
    private final MilestoneService milestoneService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final Notifier notifier;

    public ModerationService(CompanyService companyService, EngineerProfileService profileService,
                             ProjectService projectService, MilestoneService milestoneService,
                             PaymentService paymentService, UserService userService, Notifier notifier) {
        this.companyService = companyService;
        this.profileService = profileService;
        this.projectService = projectService;
        this.milestoneService = milestoneService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.notifier = notifier;
    }

    /**
     * A blocked company (sanctions hit) loses every project that is not under contract yet.
     */
    @Transactional
    public AdminCompanyView reviewCompany(long companyId, ReviewDecision decision, SanctionsStatus sanctions,
                                          String note, long adminId) {
        ClientCompany company = companyService.review(companyId, decision, sanctions, note, adminId);
        if (company.getStatus() == CompanyStatus.BLOCKED) {
            projectService.cancelActiveProjectsOfCompany(companyId);
        } else {
            MailTemplate template = company.getStatus() == CompanyStatus.APPROVED
                    ? MailTemplate.COMPANY_APPROVED : MailTemplate.COMPANY_REJECTED;
            notifyMembers(companyId, template, company.getName());
        }
        return companyService.adminGet(companyId);
    }

    @Transactional
    public AdminEngineerView reviewEngineer(long profileId, ReviewDecision decision, String note, String partnerRef,
                                            long adminId) {
        EngineerProfile profile = profileService.review(profileId, decision, note, partnerRef, adminId);
        notifyUser(profile.getUserId(), decision == ReviewDecision.APPROVE
                ? MailTemplate.ENGINEER_APPROVED : MailTemplate.ENGINEER_REJECTED, profile.getAlias());
        return profileService.adminGet(profileId);
    }

    @Transactional
    public AdminProjectView reviewProject(long projectId, ReviewDecision decision, String note, long adminId) {
        Project project = projectService.review(projectId, decision, note, adminId);
        MailTemplate template = switch (decision) {
            case APPROVE -> MailTemplate.PROJECT_APPROVED;
            case REJECT -> MailTemplate.PROJECT_REJECTED;
            case REQUEST_CHANGES -> MailTemplate.PROJECT_CHANGES_REQUESTED;
        };
        notifyMembers(project.getCompanyId(), template, project.getTitle());
        return projectService.adminGet(projectId);
    }

    public List<DisputeView> disputes() {
        return milestoneService.listDisputes();
    }

    /**
     * REFUND returns the milestone payment to the client through the payment provider.
     */
    @Transactional
    public ContractMilestone resolveDispute(long milestoneId, MilestoneService.Resolution resolution, String note,
                                            long adminId) {
        ContractMilestone milestone = milestoneService.resolveDispute(milestoneId, resolution, note, adminId);
        if (resolution == MilestoneService.Resolution.REFUND) {
            paymentService.refundMilestone(milestoneId, adminId);
        }
        return milestone;
    }

    private void notifyMembers(long companyId, MailTemplate template, Object... args) {
        companyService.memberUserIds(companyId).forEach(userId -> notifyUser(userId, template, args));
    }

    private void notifyUser(long userId, MailTemplate template, Object... args) {
        AppUser user = userService.get(userId);
        notifier.send(user.getEmail(), user.getLocale(), template, args);
    }
}
