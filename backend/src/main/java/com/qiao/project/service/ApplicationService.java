package com.qiao.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.error.ValidationCodes;
import com.qiao.common.money.FeeCalculator;
import com.qiao.common.money.Money;
import com.qiao.common.util.Texts;
import com.qiao.compliance.domain.ConsentType;
import com.qiao.compliance.domain.TransferDataKind;
import com.qiao.compliance.service.ConsentService;
import com.qiao.compliance.service.TransferService;
import com.qiao.compliance.web.ConsentView;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.profile.web.CvView;
import com.qiao.profile.web.PseudonymizedProfile;
import com.qiao.project.domain.Application;
import com.qiao.project.domain.ApplicationMapper;
import com.qiao.project.domain.ApplicationStatus;
import com.qiao.project.domain.MilestonePrice;
import com.qiao.project.domain.Project;
import com.qiao.project.domain.ProjectMilestone;
import com.qiao.project.domain.ProjectStatus;
import com.qiao.project.web.ApplyRequest;
import com.qiao.project.web.ClientApplicationView;
import com.qiao.project.web.EngineerApplicationView;
import com.qiao.project.web.PriceRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applications: SUBMITTED → SHORTLISTED → SELECTED → HIRED; DECLINED/WITHDRAWN at any point before HIRED.
 * Clients only ever see engineers through {@link PseudonymizedProfile}; every disclosure is a transfer event.
 */
@Service
public class ApplicationService {

    private final ApplicationMapper applicationMapper;
    private final ProjectService projectService;
    private final EngineerProfileService profileService;
    private final ConsentService consentService;
    private final TransferService transferService;
    private final FeeCalculator feeCalculator;
    private final Clock clock;

    public ApplicationService(ApplicationMapper applicationMapper, ProjectService projectService,
                              EngineerProfileService profileService, ConsentService consentService,
                              TransferService transferService, FeeCalculator feeCalculator, Clock clock) {
        this.applicationMapper = applicationMapper;
        this.projectService = projectService;
        this.profileService = profileService;
        this.consentService = consentService;
        this.transferService = transferService;
        this.feeCalculator = feeCalculator;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ engineer

    @Transactional
    public EngineerApplicationView apply(long engineerUserId, long projectId, ApplyRequest request) {
        Project project = projectService.get(projectId);
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new ApiException(ErrorCode.PROJECT_NOT_OPEN);
        }
        profileService.requireApproved(engineerUserId);
        if (!consentService.isGranted(engineerUserId, ConsentType.SHARE_PROFILE_PSEUDONYMIZED, null)) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED, "Consent to share the pseudonymized profile required",
                    Map.of("consentType", ConsentType.SHARE_PROFILE_PSEUDONYMIZED.name()));
        }
        if (findOwn(engineerUserId, projectId) != null) {
            throw new ApiException(ErrorCode.ALREADY_APPLIED);
        }
        List<MilestonePrice> prices = validatePrices(projectService.milestones(projectId), request.prices());
        Application application = new Application();
        application.setProjectId(projectId);
        application.setEngineerUserId(engineerUserId);
        application.setCoverNote(Texts.trimToNull(request.coverNote()));
        application.setPrices(prices);
        application.setTalentTotalUsd(Money.sum(prices.stream().map(MilestonePrice::talentAmountUsd).toList()));
        application.setClientTotalUsd(Money.sum(prices.stream().map(p -> feeCalculator.clientAmount(p.talentAmountUsd())).toList()));
        application.setStatus(ApplicationStatus.SUBMITTED);
        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_APPLIED);
        }
        return engineerView(application, project);
    }

    public List<EngineerApplicationView> listForEngineer(long engineerUserId) {
        List<Application> applications = applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                .eq(Application::getEngineerUserId, engineerUserId)
                .orderByDesc(Application::getId));
        return applications.stream().map(a -> engineerView(a, projectService.get(a.getProjectId()))).toList();
    }

    @Transactional
    public EngineerApplicationView withdraw(long engineerUserId, long applicationId) {
        Application application = lockForEngineer(engineerUserId, applicationId);
        if (!ApplicationStatus.ACTIVE.contains(application.getStatus())) {
            throw ApiException.invalidState("Application cannot be withdrawn in status " + application.getStatus());
        }
        setStatus(application, ApplicationStatus.WITHDRAWN);
        return engineerView(application, projectService.get(application.getProjectId()));
    }

    /**
     * Records {@code SHARE_FULL_CV} for this application's project ({@code scopeRef = projectId}).
     */
    @Transactional
    public ConsentView cvConsent(long engineerUserId, long applicationId, boolean granted) {
        Application application = lockForEngineer(engineerUserId, applicationId);
        if (granted && application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw ApiException.invalidState("Application was withdrawn");
        }
        return consentService.record(engineerUserId, ConsentType.SHARE_FULL_CV, granted,
                String.valueOf(application.getProjectId()), null);
    }

    // ------------------------------------------------------------------ client

    @Transactional
    public List<ClientApplicationView> listForProject(long clientUserId, long projectId) {
        Project project = projectService.getOwned(clientUserId, projectId);
        List<Application> applications = applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                .eq(Application::getProjectId, projectId)
                .ne(Application::getStatus, ApplicationStatus.WITHDRAWN)
                .orderByAsc(Application::getId));
        List<Long> engineerIds = applications.stream().map(Application::getEngineerUserId).toList();
        transferService.record(engineerIds, project.getCompanyId(), TransferDataKind.PSEUDONYMIZED_PROFILE);
        Map<Long, PseudonymizedProfile> profiles = profileService.pseudonymized(engineerIds);
        Set<Long> cvShared = consentService.usersWithGrant(engineerIds, ConsentType.SHARE_FULL_CV, String.valueOf(projectId));
        return applications.stream().map(a -> clientView(a, profiles.get(a.getEngineerUserId()),
                cvShared.contains(a.getEngineerUserId()))).toList();
    }

    @Transactional
    public ClientApplicationView shortlist(long clientUserId, long applicationId) {
        return clientTransition(clientUserId, applicationId, EnumSet.of(ApplicationStatus.SUBMITTED), ApplicationStatus.SHORTLISTED);
    }

    @Transactional
    public ClientApplicationView select(long clientUserId, long applicationId) {
        return clientTransition(clientUserId, applicationId,
                EnumSet.of(ApplicationStatus.SUBMITTED, ApplicationStatus.SHORTLISTED), ApplicationStatus.SELECTED);
    }

    @Transactional
    public ClientApplicationView decline(long clientUserId, long applicationId) {
        return clientTransition(clientUserId, applicationId, ApplicationStatus.ACTIVE, ApplicationStatus.DECLINED);
    }

    @Transactional
    public CvView cv(long clientUserId, long applicationId) {
        Application application = applicationMapper.selectById(applicationId);
        if (application == null || application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw ApiException.notFound("Application");
        }
        Project project = projectService.getOwned(clientUserId, application.getProjectId());
        long engineerUserId = application.getEngineerUserId();
        if (!consentService.isGranted(engineerUserId, ConsentType.SHARE_FULL_CV, String.valueOf(project.getId()))) {
            throw new ApiException(ErrorCode.CV_CONSENT_REQUIRED);
        }
        transferService.record(List.of(engineerUserId), project.getCompanyId(), TransferDataKind.FULL_CV);
        return CvView.from(profileService.getByUserId(engineerUserId));
    }

    // ------------------------------------------------------------------ contract module

    /**
     * Locks an application of the client's company for hiring. Returns it only when SELECTED on an OPEN project.
     */
    public Application lockForHire(long clientUserId, long applicationId) {
        Application application = applicationMapper.selectByIdForUpdate(applicationId);
        if (application == null) {
            throw ApiException.notFound("Application");
        }
        Project project = projectService.getOwned(clientUserId, application.getProjectId());
        if (application.getStatus() != ApplicationStatus.SELECTED) {
            throw ApiException.invalidState("Only SELECTED applications can be hired");
        }
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new ApiException(ErrorCode.PROJECT_NOT_OPEN);
        }
        return application;
    }

    /**
     * Marks the application HIRED and declines every other active application of the project.
     */
    public void markHired(Application hired) {
        setStatus(hired, ApplicationStatus.HIRED);
        applicationMapper.update(null, new LambdaUpdateWrapper<Application>()
                .set(Application::getStatus, ApplicationStatus.DECLINED)
                .set(Application::getUpdatedAt, clock.instant())
                .eq(Application::getProjectId, hired.getProjectId())
                .ne(Application::getId, hired.getId())
                .in(Application::getStatus, ApplicationStatus.ACTIVE));
    }

    // ------------------------------------------------------------------ personal data

    void withdrawAllAndClearNotes(long engineerUserId) {
        applicationMapper.update(null, new LambdaUpdateWrapper<Application>()
                .set(Application::getStatus, ApplicationStatus.WITHDRAWN)
                .set(Application::getUpdatedAt, clock.instant())
                .eq(Application::getEngineerUserId, engineerUserId)
                .in(Application::getStatus, ApplicationStatus.ACTIVE));
        applicationMapper.update(null, new LambdaUpdateWrapper<Application>()
                .set(Application::getCoverNote, null)
                .eq(Application::getEngineerUserId, engineerUserId));
    }

    // ------------------------------------------------------------------ internals

    private ClientApplicationView clientTransition(long clientUserId, long applicationId, Set<ApplicationStatus> from,
                                                   ApplicationStatus to) {
        Application application = applicationMapper.selectByIdForUpdate(applicationId);
        if (application == null || application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw ApiException.notFound("Application");
        }
        Project project = projectService.getOwned(clientUserId, application.getProjectId());
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new ApiException(ErrorCode.PROJECT_NOT_OPEN);
        }
        if (!from.contains(application.getStatus())) {
            throw ApiException.invalidState("Application cannot move from " + application.getStatus() + " to " + to);
        }
        setStatus(application, to);
        long engineerUserId = application.getEngineerUserId();
        transferService.record(List.of(engineerUserId), project.getCompanyId(), TransferDataKind.PSEUDONYMIZED_PROFILE);
        boolean cvShared = consentService.isGranted(engineerUserId, ConsentType.SHARE_FULL_CV, String.valueOf(project.getId()));
        return clientView(application, profileService.pseudonymized(List.of(engineerUserId)).get(engineerUserId), cvShared);
    }

    private void setStatus(Application application, ApplicationStatus status) {
        application.setStatus(status);
        applicationMapper.updateById(application);
    }

    private Application lockForEngineer(long engineerUserId, long applicationId) {
        Application application = applicationMapper.selectByIdForUpdate(applicationId);
        if (application == null || application.getEngineerUserId() != engineerUserId) {
            throw ApiException.notFound("Application");
        }
        return application;
    }

    private Application findOwn(long engineerUserId, long projectId) {
        return applicationMapper.selectOne(new LambdaQueryWrapper<Application>()
                .eq(Application::getProjectId, projectId)
                .eq(Application::getEngineerUserId, engineerUserId));
    }

    /**
     * Prices must cover every project milestone exactly once.
     */
    private static List<MilestonePrice> validatePrices(List<ProjectMilestone> milestones, List<PriceRequest> prices) {
        Map<Long, PriceRequest> byMilestone = prices.stream()
                .collect(Collectors.toMap(PriceRequest::milestoneId, Function.identity(), (a, b) -> a));
        Set<Long> milestoneIds = milestones.stream().map(ProjectMilestone::getId).collect(Collectors.toSet());
        if (byMilestone.size() != prices.size() || !byMilestone.keySet().equals(milestoneIds)) {
            throw ApiException.validation("prices", ValidationCodes.INVALID);
        }
        return milestones.stream()
                .map(m -> new MilestonePrice(m.getId(), Money.of(byMilestone.get(m.getId()).talentAmountUsd())))
                .toList();
    }

    private EngineerApplicationView engineerView(Application a, Project project) {
        boolean cvShared = consentService.isGranted(a.getEngineerUserId(), ConsentType.SHARE_FULL_CV,
                String.valueOf(a.getProjectId()));
        return new EngineerApplicationView(a.getId(), a.getProjectId(), project.getTitle(), project.getStatus(),
                a.getStatus(), a.getCoverNote(), a.getPrices(), a.getTalentTotalUsd(), cvShared, a.getCreatedAt());
    }

    private ClientApplicationView clientView(Application a, PseudonymizedProfile engineer, boolean cvAvailable) {
        List<ClientApplicationView.Price> prices = a.getPrices().stream()
                .map(p -> new ClientApplicationView.Price(p.milestoneId(), p.talentAmountUsd(),
                        feeCalculator.clientAmount(p.talentAmountUsd())))
                .toList();
        return new ClientApplicationView(a.getId(), a.getProjectId(), a.getStatus(), a.getCoverNote(),
                a.getTalentTotalUsd(), a.getClientTotalUsd(), prices, engineer, cvAvailable, a.getCreatedAt());
    }
}
