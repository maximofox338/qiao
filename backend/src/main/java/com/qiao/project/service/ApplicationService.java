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
import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.domain.ProfileStatus;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.profile.web.CvView;
import com.qiao.profile.web.PseudonymizedProfile;
import com.qiao.project.domain.Application;
import com.qiao.project.domain.ApplicationMapper;
import com.qiao.project.domain.ApplicationMember;
import com.qiao.project.domain.ApplicationMemberMapper;
import com.qiao.project.domain.ApplicationStatus;
import com.qiao.project.domain.MilestonePrice;
import com.qiao.project.domain.Project;
import com.qiao.project.domain.ProjectMilestone;
import com.qiao.project.domain.ProjectStatus;
import com.qiao.project.web.ApplyRequest;
import com.qiao.project.web.ClientApplicationView;
import com.qiao.project.web.EngineerApplicationView;
import com.qiao.project.web.PriceRequest;
import com.qiao.project.web.ShareRequest;
import com.qiao.project.web.TeamApplyRequest;
import com.qiao.team.domain.Team;
import com.qiao.team.domain.TeamMember;
import com.qiao.team.domain.TeamRole;
import com.qiao.team.service.ActiveTeam;
import com.qiao.team.service.TeamService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applications: SUBMITTED → SHORTLISTED → SELECTED → HIRED; DECLINED/WITHDRAWN at any point before HIRED.
 * Clients only ever see engineers through {@link PseudonymizedProfile}; every disclosure is a transfer event (one per
 * member for team applications, spec §12).
 */
@Service
public class ApplicationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final ApplicationMapper applicationMapper;
    private final ApplicationMemberMapper memberMapper;
    private final ProjectService projectService;
    private final EngineerProfileService profileService;
    private final ConsentService consentService;
    private final TransferService transferService;
    private final TeamService teamService;
    private final FeeCalculator feeCalculator;
    private final Clock clock;

    public ApplicationService(ApplicationMapper applicationMapper, ApplicationMemberMapper memberMapper,
                              ProjectService projectService, EngineerProfileService profileService,
                              ConsentService consentService, TransferService transferService,
                              TeamService teamService, FeeCalculator feeCalculator, Clock clock) {
        this.applicationMapper = applicationMapper;
        this.memberMapper = memberMapper;
        this.projectService = projectService;
        this.profileService = profileService;
        this.consentService = consentService;
        this.transferService = transferService;
        this.teamService = teamService;
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
        if (findOwn(engineerUserId, projectId) != null || !alreadyInTeamApplication(projectId, List.of(engineerUserId)).isEmpty()) {
            throw new ApiException(ErrorCode.ALREADY_APPLIED);
        }
        List<MilestonePrice> prices = validatePrices(projectService.milestones(projectId), request.prices());
        Application application = newApplication(projectId, engineerUserId, null, request.coverNote(), prices);
        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_APPLIED);
        }
        return engineerView(application, project, engineerUserId, List.of(), Map.of());
    }

    /**
     * The lead applies on behalf of an ACTIVE team with a split of every milestone among its active members.
     */
    @Transactional
    public EngineerApplicationView applyAsTeam(long leadUserId, long projectId, TeamApplyRequest request) {
        Project project = projectService.get(projectId);
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new ApiException(ErrorCode.PROJECT_NOT_OPEN);
        }
        ActiveTeam team = teamService.lockLedTeam(leadUserId, request.teamId());
        if (team.members().size() < TeamService.MIN_MEMBERS_TO_APPLY) {
            throw new ApiException(ErrorCode.TEAM_TOO_SMALL);
        }
        List<Long> memberIds = team.memberUserIds();
        Map<Long, EngineerProfile> profiles = profileService.byUserIds(memberIds);
        Map<Long, BigDecimal> shares = validateShares(team, profiles, request.shares());

        List<String> notApproved = aliasesWhere(memberIds, profiles,
                id -> profiles.get(id) == null || profiles.get(id).getStatus() != ProfileStatus.APPROVED);
        if (!notApproved.isEmpty()) {
            throw new ApiException(ErrorCode.PROFILE_NOT_APPROVED, "Every member needs an approved profile",
                    Map.of("aliases", notApproved));
        }
        Set<Long> consented = consentService.usersWithGrant(memberIds, ConsentType.SHARE_PROFILE_PSEUDONYMIZED, null);
        List<String> missingConsent = aliasesWhere(memberIds, profiles, id -> !consented.contains(id));
        if (!missingConsent.isEmpty()) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED,
                    "Every member must consent to share the pseudonymized profile",
                    Map.of("consentType", ConsentType.SHARE_PROFILE_PSEUDONYMIZED.name(), "aliases", missingConsent));
        }
        Set<Long> applied = new HashSet<>(alreadyInTeamApplication(projectId, memberIds));
        applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                        .select(Application::getEngineerUserId)
                        .eq(Application::getProjectId, projectId)
                        .in(Application::getEngineerUserId, memberIds))
                .forEach(a -> applied.add(a.getEngineerUserId()));
        if (!applied.isEmpty()) {
            throw new ApiException(ErrorCode.ALREADY_APPLIED, "A member already applied to this project",
                    Map.of("aliases", aliasesWhere(memberIds, profiles, applied::contains)));
        }

        List<MilestonePrice> prices = validatePrices(projectService.milestones(projectId), request.prices());
        Application application = newApplication(projectId, leadUserId, team.team().getId(), request.coverNote(), prices);
        List<ApplicationMember> members = new ArrayList<>();
        try {
            applicationMapper.insert(application);
            for (TeamMember m : team.members()) {
                ApplicationMember member = new ApplicationMember();
                member.setApplicationId(application.getId());
                member.setProjectId(projectId);
                member.setEngineerUserId(m.getUserId());
                member.setRole(m.getRole());
                member.setSharePct(shares.get(m.getUserId()));
                memberMapper.insert(member);
                members.add(member);
            }
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_APPLIED);
        }
        return engineerView(application, project, leadUserId, members, Map.of(team.team().getId(), team.team()));
    }

    /**
     * Own applications plus the team applications the engineer is a member of.
     */
    public List<EngineerApplicationView> listForEngineer(long engineerUserId) {
        Set<Long> asMember = memberMapper.selectList(new LambdaQueryWrapper<ApplicationMember>()
                        .eq(ApplicationMember::getEngineerUserId, engineerUserId))
                .stream().map(ApplicationMember::getApplicationId).collect(Collectors.toSet());
        List<Application> applications = applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                .and(w -> w.eq(Application::getEngineerUserId, engineerUserId)
                        .or(!asMember.isEmpty(), o -> o.in(Application::getId, asMember)))
                .orderByDesc(Application::getId));
        Map<Long, List<ApplicationMember>> members = membersByApplication(applications);
        Map<Long, Team> teams = teamService.byIds(applications.stream().map(Application::getTeamId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return applications.stream().map(a -> engineerView(a, projectService.get(a.getProjectId()), engineerUserId,
                members.getOrDefault(a.getId(), List.of()), teams)).toList();
    }

    @Transactional
    public EngineerApplicationView withdraw(long engineerUserId, long applicationId) {
        Application application = lockForEngineer(engineerUserId, applicationId);
        if (application.getEngineerUserId() != engineerUserId) {
            throw new ApiException(ErrorCode.TEAM_LEAD_ONLY);
        }
        if (!ApplicationStatus.ACTIVE.contains(application.getStatus())) {
            throw ApiException.invalidState("Application cannot be withdrawn in status " + application.getStatus());
        }
        setStatus(application, ApplicationStatus.WITHDRAWN);
        List<ApplicationMember> members = members(application.getId());
        return engineerView(application, projectService.get(application.getProjectId()), engineerUserId, members,
                application.getTeamId() == null ? Map.of() : teamService.byIds(List.of(application.getTeamId())));
    }

    /**
     * Records the caller's own {@code SHARE_FULL_CV} for this application's project ({@code scopeRef = projectId});
     * in a team application every member decides for their own CV.
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
        Map<Long, List<ApplicationMember>> members = membersByApplication(applications);
        return clientViews(project, applications, members);
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

    /**
     * Full CV of the engineer (or, for a team application, of the member with {@code alias}), only with that
     * engineer's SHARE_FULL_CV for the project.
     */
    @Transactional
    public CvView cv(long clientUserId, long applicationId, String alias) {
        Application application = applicationMapper.selectById(applicationId);
        if (application == null || application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw ApiException.notFound("Application");
        }
        Project project = projectService.getOwned(clientUserId, application.getProjectId());
        List<Long> candidates = application.getTeamId() == null ? List.of(application.getEngineerUserId())
                : members(applicationId).stream().map(ApplicationMember::getEngineerUserId).toList();
        String wanted = Texts.trimToNull(alias);
        if (wanted == null && application.getTeamId() != null) {
            throw ApiException.validation("alias", ValidationCodes.REQUIRED);
        }
        Map<Long, EngineerProfile> profiles = profileService.byUserIds(candidates);
        EngineerProfile profile = candidates.stream().map(profiles::get).filter(Objects::nonNull)
                .filter(p -> wanted == null || p.getAlias().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Application member"));
        long engineerUserId = profile.getUserId();
        if (!consentService.isGranted(engineerUserId, ConsentType.SHARE_FULL_CV, String.valueOf(project.getId()))) {
            throw new ApiException(ErrorCode.CV_CONSENT_REQUIRED);
        }
        transferService.record(List.of(engineerUserId), project.getCompanyId(), TransferDataKind.FULL_CV);
        return CvView.from(profile);
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
     * Members of a team application, lead first (empty for individual applications).
     */
    public List<ApplicationMember> members(long applicationId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ApplicationMember>()
                        .eq(ApplicationMember::getApplicationId, applicationId)
                        .orderByAsc(ApplicationMember::getId))
                .stream().sorted(LEAD_FIRST).toList();
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

    /**
     * Withdraws the engineer's open applications, including the team applications they are a member of, and clears
     * the cover notes they wrote.
     */
    void withdrawAllAndClearNotes(long engineerUserId) {
        Set<Long> asMember = memberMapper.selectList(new LambdaQueryWrapper<ApplicationMember>()
                        .eq(ApplicationMember::getEngineerUserId, engineerUserId))
                .stream().map(ApplicationMember::getApplicationId).collect(Collectors.toSet());
        applicationMapper.update(null, new LambdaUpdateWrapper<Application>()
                .set(Application::getStatus, ApplicationStatus.WITHDRAWN)
                .set(Application::getUpdatedAt, clock.instant())
                .and(w -> w.eq(Application::getEngineerUserId, engineerUserId)
                        .or(!asMember.isEmpty(), o -> o.in(Application::getId, asMember)))
                .in(Application::getStatus, ApplicationStatus.ACTIVE));
        applicationMapper.update(null, new LambdaUpdateWrapper<Application>()
                .set(Application::getCoverNote, null)
                .eq(Application::getEngineerUserId, engineerUserId));
    }

    // ------------------------------------------------------------------ internals

    private static final Comparator<ApplicationMember> LEAD_FIRST =
            Comparator.comparing((ApplicationMember m) -> m.getRole() != TeamRole.LEAD).thenComparing(ApplicationMember::getId);

    private Application newApplication(long projectId, long engineerUserId, Long teamId, String coverNote,
                                       List<MilestonePrice> prices) {
        Application application = new Application();
        application.setProjectId(projectId);
        application.setEngineerUserId(engineerUserId);
        application.setTeamId(teamId);
        application.setCoverNote(Texts.trimToNull(coverNote));
        application.setPrices(prices);
        application.setTalentTotalUsd(Money.sum(prices.stream().map(MilestonePrice::talentAmountUsd).toList()));
        application.setClientTotalUsd(Money.sum(prices.stream().map(p -> feeCalculator.clientAmount(p.talentAmountUsd())).toList()));
        application.setStatus(ApplicationStatus.SUBMITTED);
        return application;
    }

    /**
     * Shares must name every active member exactly once (by alias) and add up to 100.
     */
    private static Map<Long, BigDecimal> validateShares(ActiveTeam team, Map<Long, EngineerProfile> profiles,
                                                        List<ShareRequest> requests) {
        Map<String, Long> byAlias = new HashMap<>();
        for (TeamMember m : team.members()) {
            EngineerProfile p = profiles.get(m.getUserId());
            if (p != null) {
                byAlias.put(p.getAlias().toUpperCase(Locale.ROOT), m.getUserId());
            }
        }
        Map<Long, BigDecimal> shares = new HashMap<>();
        for (ShareRequest request : requests) {
            Long userId = byAlias.get(request.alias().trim().toUpperCase(Locale.ROOT));
            if (userId == null || shares.put(userId, Money.of(request.sharePct())) != null) {
                throw ApiException.validation("shares", ValidationCodes.INVALID);
            }
        }
        if (shares.size() != team.members().size()) {
            throw ApiException.validation("shares", ValidationCodes.INVALID);
        }
        if (Money.sum(shares.values()).compareTo(HUNDRED) != 0) {
            throw new ApiException(ErrorCode.SHARES_SUM_MISMATCH);
        }
        return shares;
    }

    private static List<String> aliasesWhere(List<Long> userIds, Map<Long, EngineerProfile> profiles,
                                             java.util.function.Predicate<Long> condition) {
        return userIds.stream().filter(condition)
                .map(id -> profiles.containsKey(id) ? profiles.get(id).getAlias() : String.valueOf(id))
                .toList();
    }

    /**
     * Engineers (among {@code userIds}) already in a team application of the project.
     */
    private List<Long> alreadyInTeamApplication(long projectId, Collection<Long> userIds) {
        return memberMapper.selectList(new LambdaQueryWrapper<ApplicationMember>()
                        .eq(ApplicationMember::getProjectId, projectId)
                        .in(ApplicationMember::getEngineerUserId, userIds))
                .stream().map(ApplicationMember::getEngineerUserId).toList();
    }

    private Map<Long, List<ApplicationMember>> membersByApplication(List<Application> applications) {
        List<Long> teamApplicationIds = applications.stream().filter(a -> a.getTeamId() != null).map(Application::getId).toList();
        if (teamApplicationIds.isEmpty()) {
            return Map.of();
        }
        return memberMapper.selectList(new LambdaQueryWrapper<ApplicationMember>()
                        .in(ApplicationMember::getApplicationId, teamApplicationIds))
                .stream().sorted(LEAD_FIRST)
                .collect(Collectors.groupingBy(ApplicationMember::getApplicationId));
    }

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
        return clientViews(project, List.of(application), membersByApplication(List.of(application))).getFirst();
    }

    /**
     * Builds the client views and records one PSEUDONYMIZED_PROFILE transfer per engineer shown (every member of a
     * team application).
     */
    private List<ClientApplicationView> clientViews(Project project, List<Application> applications,
                                                    Map<Long, List<ApplicationMember>> members) {
        Set<Long> engineerIds = new LinkedHashSet<>();
        for (Application a : applications) {
            engineerIds.add(a.getEngineerUserId());
            members.getOrDefault(a.getId(), List.of()).forEach(m -> engineerIds.add(m.getEngineerUserId()));
        }
        transferService.record(List.copyOf(engineerIds), project.getCompanyId(), TransferDataKind.PSEUDONYMIZED_PROFILE);
        Map<Long, PseudonymizedProfile> profiles = profileService.pseudonymized(engineerIds);
        Set<Long> cvShared = consentService.usersWithGrant(engineerIds, ConsentType.SHARE_FULL_CV, String.valueOf(project.getId()));
        Map<Long, Team> teams = teamService.byIds(applications.stream().map(Application::getTeamId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return applications.stream().map(a -> {
            ClientApplicationView.Team team = null;
            if (a.getTeamId() != null) {
                Team t = teams.get(a.getTeamId());
                team = new ClientApplicationView.Team(t == null ? null : t.getName(), t == null ? null : t.getDescription(),
                        members.getOrDefault(a.getId(), List.of()).stream()
                                .map(m -> new ClientApplicationView.Member(profiles.get(m.getEngineerUserId()) == null ? null
                                        : profiles.get(m.getEngineerUserId()).alias(), m.getRole(),
                                        profiles.get(m.getEngineerUserId()), cvShared.contains(m.getEngineerUserId())))
                                .toList());
            }
            return clientView(a, profiles.get(a.getEngineerUserId()), cvShared.contains(a.getEngineerUserId()), team);
        }).toList();
    }

    private void setStatus(Application application, ApplicationStatus status) {
        application.setStatus(status);
        applicationMapper.updateById(application);
    }

    /**
     * Locks an application the engineer applied to, alone or as a team member.
     */
    private Application lockForEngineer(long engineerUserId, long applicationId) {
        Application application = applicationMapper.selectByIdForUpdate(applicationId);
        if (application == null) {
            throw ApiException.notFound("Application");
        }
        boolean member = application.getEngineerUserId() == engineerUserId
                || (application.getTeamId() != null && memberMapper.exists(new LambdaQueryWrapper<ApplicationMember>()
                .eq(ApplicationMember::getApplicationId, applicationId)
                .eq(ApplicationMember::getEngineerUserId, engineerUserId)));
        if (!member) {
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

    private EngineerApplicationView engineerView(Application a, Project project, long viewerUserId,
                                                 List<ApplicationMember> members, Map<Long, Team> teams) {
        boolean cvShared = consentService.isGranted(viewerUserId, ConsentType.SHARE_FULL_CV,
                String.valueOf(a.getProjectId()));
        EngineerApplicationView.Team team = null;
        if (a.getTeamId() != null) {
            Map<Long, String> aliases = profileService.aliases(members.stream().map(ApplicationMember::getEngineerUserId).toList());
            ApplicationMember mine = members.stream().filter(m -> m.getEngineerUserId() == viewerUserId).findFirst().orElse(null);
            Team t = teams.get(a.getTeamId());
            team = new EngineerApplicationView.Team(a.getTeamId(), t == null ? null : t.getName(),
                    mine == null ? null : mine.getRole(), mine == null ? null : mine.getSharePct(),
                    members.stream().map(m -> new EngineerApplicationView.Member(aliases.get(m.getEngineerUserId()),
                            m.getRole(), m.getSharePct())).toList());
        }
        return new EngineerApplicationView(a.getId(), a.getProjectId(), project.getTitle(), project.getStatus(),
                a.getStatus(), a.getCoverNote(), a.getPrices(), a.getTalentTotalUsd(), cvShared, a.getCreatedAt(), team);
    }

    private ClientApplicationView clientView(Application a, PseudonymizedProfile engineer, boolean cvAvailable,
                                             ClientApplicationView.Team team) {
        List<ClientApplicationView.Price> prices = a.getPrices().stream()
                .map(p -> new ClientApplicationView.Price(p.milestoneId(), p.talentAmountUsd(),
                        feeCalculator.clientAmount(p.talentAmountUsd())))
                .toList();
        return new ClientApplicationView(a.getId(), a.getProjectId(), a.getStatus(), a.getCoverNote(),
                a.getTalentTotalUsd(), a.getClientTotalUsd(), prices, engineer, cvAvailable, a.getCreatedAt(), team);
    }
}
