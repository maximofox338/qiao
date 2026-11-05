package com.qiao.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.account.domain.UserRole;
import com.qiao.account.service.UserService;
import com.qiao.client.domain.ClientCompany;
import com.qiao.client.service.CompanyService;
import com.qiao.common.config.AppProperties;
import com.qiao.common.error.ApiException;
import com.qiao.common.money.FeeCalculator;
import com.qiao.common.money.Money;
import com.qiao.common.persistence.StatusCounts;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.common.util.Locales;
import com.qiao.common.web.RequestMeta;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.contract.domain.Contract;
import com.qiao.contract.domain.ContractDocument;
import com.qiao.contract.domain.ContractDocumentMapper;
import com.qiao.contract.domain.ContractMapper;
import com.qiao.contract.domain.ContractMember;
import com.qiao.contract.domain.ContractMemberMapper;
import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.domain.ContractMilestoneMapper;
import com.qiao.contract.domain.ContractStatus;
import com.qiao.contract.domain.DocumentKind;
import com.qiao.contract.domain.MilestoneStatus;
import com.qiao.contract.web.ClientContractView;
import com.qiao.contract.web.ClientMilestoneView;
import com.qiao.contract.web.ContractDocumentView;
import com.qiao.contract.web.EngineerContractView;
import com.qiao.contract.web.EngineerMilestoneView;
import com.qiao.notify.service.MailTemplate;
import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.project.domain.Application;
import com.qiao.project.domain.ApplicationMember;
import com.qiao.project.domain.MilestonePrice;
import com.qiao.project.domain.Project;
import com.qiao.project.domain.ProjectMilestone;
import com.qiao.project.service.ApplicationService;
import com.qiao.project.service.ProjectService;
import com.qiao.team.domain.Team;
import com.qiao.team.service.TeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contracts: PENDING_SIGNATURES → (every document accepted) ACTIVE → (all milestones final) COMPLETED.
 * A team contract (spec §12) has one ENGINEER_DELIVERABLES document per member.
 */
@Service
public class ContractService {

    static final String TEAM_DELIVERABLES_TEMPLATE = "ENGINEER_DELIVERABLES_TEAM";

    private final ContractMapper contractMapper;
    private final ContractDocumentMapper documentMapper;
    private final ContractMilestoneMapper milestoneMapper;
    private final ContractMemberMapper memberMapper;
    private final ContractParties parties;
    private final ApplicationService applicationService;
    private final ProjectService projectService;
    private final CompanyService companyService;
    private final EngineerProfileService profileService;
    private final TeamService teamService;
    private final UserService userService;
    private final ContractTemplateRenderer renderer;
    private final ContractProperties contractProperties;
    private final AppProperties appProperties;
    private final FeeCalculator feeCalculator;
    private final AttachmentDirectory attachmentDirectory;
    private final ContractNotifier notifier;
    private final ContractReviews reviews;
    private final AuditService auditService;
    private final Clock clock;

    public ContractService(ContractMapper contractMapper, ContractDocumentMapper documentMapper,
                           ContractMilestoneMapper milestoneMapper, ContractMemberMapper memberMapper,
                           ContractParties parties, ApplicationService applicationService,
                           ProjectService projectService, CompanyService companyService,
                           EngineerProfileService profileService, TeamService teamService, UserService userService,
                           ContractTemplateRenderer renderer, ContractProperties contractProperties,
                           AppProperties appProperties, FeeCalculator feeCalculator,
                           AttachmentDirectory attachmentDirectory, ContractNotifier notifier,
                           ContractReviews reviews, AuditService auditService, Clock clock) {
        this.contractMapper = contractMapper;
        this.documentMapper = documentMapper;
        this.milestoneMapper = milestoneMapper;
        this.memberMapper = memberMapper;
        this.parties = parties;
        this.applicationService = applicationService;
        this.projectService = projectService;
        this.companyService = companyService;
        this.profileService = profileService;
        this.teamService = teamService;
        this.userService = userService;
        this.renderer = renderer;
        this.contractProperties = contractProperties;
        this.appProperties = appProperties;
        this.feeCalculator = feeCalculator;
        this.attachmentDirectory = attachmentDirectory;
        this.notifier = notifier;
        this.reviews = reviews;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Hires a SELECTED application: creates the contract, its milestones and the documents (client agreement plus one
     * deliverables agreement per engineer), marks the application HIRED (declining the others) and the project
     * IN_CONTRACT.
     */
    @Transactional
    public ClientContractView hire(long clientUserId, long applicationId) {
        Application application = applicationService.lockForHire(clientUserId, applicationId);
        Project project = projectService.get(application.getProjectId());
        ClientCompany company = companyService.requireApproved(project.getCompanyId());
        List<ApplicationMember> applicationMembers = application.getTeamId() == null ? List.of()
                : applicationService.members(application.getId());
        List<Long> engineerIds = applicationMembers.isEmpty() ? List.of(application.getEngineerUserId())
                : applicationMembers.stream().map(ApplicationMember::getEngineerUserId).toList();
        Map<Long, EngineerProfile> engineers = new LinkedHashMap<>();
        engineerIds.forEach(id -> engineers.put(id, profileService.requireApproved(id)));
        EngineerProfile lead = engineers.get(application.getEngineerUserId());

        Contract contract = new Contract();
        contract.setProjectId(project.getId());
        contract.setApplicationId(application.getId());
        contract.setCompanyId(company.getId());
        contract.setEngineerUserId(application.getEngineerUserId());
        if (application.getTeamId() != null) {
            Team team = teamService.byIds(List.of(application.getTeamId())).get(application.getTeamId());
            contract.setTeamId(application.getTeamId());
            contract.setTeamName(team == null ? null : team.getName());
        }
        contract.setStatus(ContractStatus.PENDING_SIGNATURES);
        contract.setFeePct(feeCalculator.feePct());
        contract.setTalentTotalUsd(Money.ZERO);
        contract.setClientTotalUsd(Money.ZERO);
        contractMapper.insert(contract);

        Map<Long, BigDecimal> talentByMilestone = application.getPrices().stream()
                .collect(Collectors.toMap(MilestonePrice::milestoneId, MilestonePrice::talentAmountUsd));
        List<ContractMilestone> milestones = new ArrayList<>();
        for (ProjectMilestone source : projectService.milestones(project.getId())) {
            BigDecimal talent = Money.of(talentByMilestone.get(source.getId()));
            ContractMilestone milestone = new ContractMilestone();
            milestone.setContractId(contract.getId());
            milestone.setSeq(source.getSeq());
            milestone.setTitle(source.getTitle());
            milestone.setDeliverable(source.getDeliverable());
            milestone.setDueDate(source.getDueDate());
            milestone.setTalentAmountUsd(talent);
            milestone.setClientAmountUsd(feeCalculator.clientAmount(talent));
            milestone.setStatus(MilestoneStatus.PENDING_FUNDING);
            milestoneMapper.insert(milestone);
            milestones.add(milestone);
        }
        contract.setTalentTotalUsd(Money.sum(milestones.stream().map(ContractMilestone::getTalentAmountUsd).toList()));
        contract.setClientTotalUsd(Money.sum(milestones.stream().map(ContractMilestone::getClientAmountUsd).toList()));
        contractMapper.updateById(contract);

        List<ContractMember> members = new ArrayList<>();
        for (ApplicationMember source : applicationMembers) {
            ContractMember member = new ContractMember();
            member.setContractId(contract.getId());
            member.setEngineerUserId(source.getEngineerUserId());
            member.setRole(source.getRole());
            member.setSharePct(source.getSharePct());
            memberMapper.insert(member);
            members.add(member);
        }

        String clientLocale = userService.get(clientUserId).getLocale();
        String engineerLabel = members.isEmpty() ? lead.getAlias() : teamLabel(contract, members, engineers, clientLocale);
        insertDocument(contract, null, DocumentKind.CLIENT_SERVICES.name(), DocumentKind.CLIENT_SERVICES, clientLocale,
                clientValues(contract, project, company, engineerLabel, milestones, clientLocale));
        if (members.isEmpty()) {
            String engineerLocale = userService.get(lead.getUserId()).getLocale();
            insertDocument(contract, lead.getUserId(), DocumentKind.ENGINEER_DELIVERABLES.name(),
                    DocumentKind.ENGINEER_DELIVERABLES, engineerLocale,
                    engineerValues(contract, project, company, lead, milestones, engineerLocale));
        } else {
            for (ContractMember member : members) {
                EngineerProfile profile = engineers.get(member.getEngineerUserId());
                String locale = userService.get(member.getEngineerUserId()).getLocale();
                insertDocument(contract, member.getEngineerUserId(), TEAM_DELIVERABLES_TEMPLATE,
                        DocumentKind.ENGINEER_DELIVERABLES, locale,
                        memberValues(contract, project, company, profile, members, engineers, milestones, locale));
            }
        }

        applicationService.markHired(application);
        projectService.markInContract(project.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("applicationId", applicationId);
        detail.put("projectId", project.getId());
        if (contract.getTeamId() != null) {
            detail.put("teamId", contract.getTeamId());
            detail.put("members", members.size());
        }
        auditService.log(Actor.user(clientUserId), AuditAction.CONTRACT_CREATED, "contract", contract.getId(), detail);
        notifier.toEngineer(contract, MailTemplate.APPLICATION_HIRED, project.getTitle());
        return clientView(contract, true);
    }

    public List<ClientContractView> listForClient(long userId) {
        ClientCompany company = companyService.companyOf(userId);
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                        .eq(Contract::getCompanyId, company.getId())
                        .orderByDesc(Contract::getId))
                .stream().map(c -> clientView(c, false)).toList();
    }

    public List<EngineerContractView> listForEngineer(long userId) {
        return listByEngineer(userId).stream()
                .sorted(Comparator.comparing(Contract::getId).reversed())
                .map(c -> engineerView(c, userId, false)).toList();
    }

    public ClientContractView getForClient(long userId, long contractId) {
        return clientView(requireSide(userId, contractId, UserRole.CLIENT), true);
    }

    public EngineerContractView getForEngineer(long userId, long contractId) {
        return engineerView(requireSide(userId, contractId, UserRole.ENGINEER), userId, true);
    }

    /**
     * Accepts the caller's own document; the contract becomes ACTIVE once every document is accepted.
     */
    @Transactional
    public Contract acceptDocument(AuthPrincipal principal, long contractId, long documentId) {
        UserRole side = UserRole.valueOf(principal.role());
        Contract contract = contractMapper.selectByIdForUpdate(contractId);
        if (contract == null || !isParticipant(principal.id(), contract, side)) {
            throw ApiException.notFound("Contract");
        }
        DocumentKind expectedKind = side == UserRole.CLIENT ? DocumentKind.CLIENT_SERVICES : DocumentKind.ENGINEER_DELIVERABLES;
        ContractDocument document = documentMapper.selectById(documentId);
        if (document == null || !document.getContractId().equals(contractId) || document.getKind() != expectedKind
                || (side == UserRole.ENGINEER && owner(document, contract) != principal.id())) {
            throw ApiException.notFound("Contract document");
        }
        if (document.getAcceptedAt() != null) {
            return contract;
        }
        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURES) {
            throw ApiException.invalidState("Contract is not pending signatures");
        }
        document.setAcceptedByUserId(principal.id());
        document.setAcceptedAt(clock.instant());
        document.setAcceptedIp(RequestMeta.current().ip());
        documentMapper.updateById(document);
        auditService.log(Actor.user(principal.id()), AuditAction.CONTRACT_DOCUMENT_ACCEPTED, "contract_document",
                documentId, Map.of("contractId", contractId, "kind", document.getKind().name(), "sha256", document.getSha256()));
        boolean allAccepted = documents(contractId).stream().allMatch(d -> d.getAcceptedAt() != null);
        if (allAccepted) {
            contract.setStatus(ContractStatus.ACTIVE);
            contract.setActivatedAt(clock.instant());
            contractMapper.updateById(contract);
        }
        return contract;
    }

    /**
     * @throws ApiException {@code NOT_FOUND} unless the user is an engineer of the contract (the engineer or a team
     *                      member) or a member of the client company
     */
    public ContractParticipant requireParticipant(long userId, long contractId) {
        Contract contract = contractMapper.selectById(contractId);
        if (contract != null) {
            if (parties.isEngineer(contract, userId)) {
                return new ContractParticipant(contract, UserRole.ENGINEER);
            }
            if (companyService.isMember(userId, contract.getCompanyId())) {
                return new ContractParticipant(contract, UserRole.CLIENT);
            }
        }
        throw ApiException.notFound("Contract");
    }

    /**
     * Engineer-side user ids of a contract (the engineer, or every team member, lead first).
     */
    public List<Long> engineerUserIds(Contract contract) {
        return parties.engineerUserIds(contract);
    }

    public Contract get(long contractId) {
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw ApiException.notFound("Contract");
        }
        return contract;
    }

    public Contract lock(long contractId) {
        Contract contract = contractMapper.selectByIdForUpdate(contractId);
        if (contract == null) {
            throw ApiException.notFound("Contract");
        }
        return contract;
    }

    public Map<Long, Contract> byIds(Collection<Long> contractIds) {
        if (contractIds.isEmpty()) {
            return Map.of();
        }
        return contractMapper.selectByIds(contractIds).stream().collect(Collectors.toMap(Contract::getId, Function.identity()));
    }

    /**
     * Contracts of the engineer, alone or as a team member.
     */
    public List<Contract> listByEngineer(long engineerUserId) {
        Set<Long> asMember = parties.contractIdsOfMember(engineerUserId);
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEngineerUserId, engineerUserId)
                .or(!asMember.isEmpty(), w -> w.in(Contract::getId, asMember)));
    }

    /**
     * The engineer's view of one milestone (with the viewer's own amount).
     */
    public EngineerMilestoneView engineerMilestoneView(long userId, ContractMilestone milestone) {
        Contract contract = get(milestone.getContractId());
        Payee payee = payee(contract, parties.members(contract), milestone, userId);
        return EngineerMilestoneView.from(milestone, payee == null ? null : payee.talentAmountUsd());
    }

    /**
     * Marks the contract COMPLETED (and its project) once every milestone is ACCEPTED or REFUNDED. The caller holds
     * the contract lock.
     */
    void completeIfFinished(Contract contract) {
        List<ContractMilestone> milestones = milestoneMapper.selectList(new LambdaQueryWrapper<ContractMilestone>()
                .eq(ContractMilestone::getContractId, contract.getId())
                .last("FOR UPDATE"));
        if (contract.getStatus() == ContractStatus.ACTIVE
                && milestones.stream().allMatch(m -> MilestoneStatus.FINAL.contains(m.getStatus()))) {
            contract.setStatus(ContractStatus.COMPLETED);
            contract.setCompletedAt(clock.instant());
            contractMapper.updateById(contract);
            projectService.markCompleted(contract.getProjectId());
        }
    }

    public Map<String, Long> countByStatus() {
        return StatusCounts.count(contractMapper, ContractStatus.class);
    }

    // ------------------------------------------------------------------ personal data

    List<Contract> contractsOf(long userId) {
        Optional<ClientCompany> company = companyService.findCompanyOf(userId);
        Set<Long> asMember = parties.contractIdsOfMember(userId);
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEngineerUserId, userId)
                .or(!asMember.isEmpty(), w -> w.in(Contract::getId, asMember))
                .or(company.isPresent(), w -> w.eq(Contract::getCompanyId, company.map(ClientCompany::getId).orElse(null))));
    }

    void terminatePending(long userId) {
        contractsOf(userId).stream()
                .filter(c -> c.getStatus() == ContractStatus.PENDING_SIGNATURES)
                .forEach(c -> {
                    c.setStatus(ContractStatus.TERMINATED);
                    contractMapper.updateById(c);
                    projectService.cancelContracted(c.getProjectId());
                });
    }

    // ------------------------------------------------------------------ internals

    private Contract requireSide(long userId, long contractId, UserRole side) {
        ContractParticipant participant = requireParticipant(userId, contractId);
        if (participant.side() != side) {
            throw ApiException.notFound("Contract");
        }
        return participant.contract();
    }

    private boolean isParticipant(long userId, Contract contract, UserRole side) {
        return side == UserRole.ENGINEER ? parties.isEngineer(contract, userId)
                : companyService.isMember(userId, contract.getCompanyId());
    }

    /**
     * Owner of an engineer document; documents stored before teams existed belong to the contract's engineer.
     */
    private static long owner(ContractDocument document, Contract contract) {
        return document.getEngineerUserId() != null ? document.getEngineerUserId() : contract.getEngineerUserId();
    }

    private void insertDocument(Contract contract, Long engineerUserId, String template, DocumentKind kind,
                                String locale, Map<String, String> values) {
        ContractTemplateRenderer.RenderedDocument rendered = renderer.render(template, locale, values);
        ContractDocument document = new ContractDocument();
        document.setContractId(contract.getId());
        document.setKind(kind);
        document.setEngineerUserId(engineerUserId);
        document.setTemplateVersion(rendered.templateVersion());
        document.setLocale(rendered.locale());
        document.setBody(rendered.body());
        document.setSha256(rendered.sha256());
        documentMapper.insert(document);
    }

    /**
     * How the client agreement names a team: {@code QE-A, QE-B (team "Name")} (zh-CN: {@code QE-A、QE-B（团队“Name”）}).
     */
    private static String teamLabel(Contract contract, List<ContractMember> members, Map<Long, EngineerProfile> engineers,
                                    String locale) {
        boolean zh = ContractTemplateRenderer.templateLocale(locale).equals(Locales.ZH_CN);
        String aliases = members.stream().map(m -> engineers.get(m.getEngineerUserId()).getAlias())
                .collect(Collectors.joining(zh ? "、" : ", "));
        if (contract.getTeamName() == null) {
            return aliases;
        }
        return zh ? aliases + "（团队“" + contract.getTeamName() + "”）" : aliases + " (team \"" + contract.getTeamName() + "\")";
    }

    private Map<String, String> baseValues(Contract contract, Project project) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("contractNumber", String.format("QIAO-%d-%06d", LocalDate.now(clock).getYear(), contract.getId()));
        values.put("date", LocalDate.now(clock).toString());
        values.put("hkEntityName", contractProperties.hkEntityName());
        values.put("partnerEntityName", contractProperties.partnerEntityName());
        values.put("platformUrl", appProperties.baseUrl());
        values.put("projectTitle", project.getTitle());
        values.put("projectDescription", project.getDescription());
        values.put("startDate", project.getStartDate().toString());
        values.put("endDate", project.getEndDate().toString());
        return values;
    }

    private Map<String, String> clientValues(Contract contract, Project project, ClientCompany company,
                                             String engineerLabel, List<ContractMilestone> milestones, String locale) {
        Map<String, String> values = baseValues(contract, project);
        values.put("clientCompanyName", company.getName());
        values.put("clientCountry", company.getCountry());
        values.put("engineerAlias", engineerLabel);
        values.put("milestonesTable", milestonesTable(milestones, ContractMilestone::getClientAmountUsd, locale));
        values.put("totalUsd", contract.getClientTotalUsd().toPlainString());
        return values;
    }

    private Map<String, String> engineerValues(Contract contract, Project project, ClientCompany company,
                                               EngineerProfile engineer, List<ContractMilestone> milestones,
                                               String locale) {
        Map<String, String> values = baseValues(contract, project);
        values.put("clientCountry", company.getCountry());
        values.put("engineerAlias", engineer.getAlias());
        values.put("engineerPartnerRef", engineer.getPartnerRef());
        values.put("milestonesTable", milestonesTable(milestones, ContractMilestone::getTalentAmountUsd, locale));
        values.put("totalUsd", contract.getTalentTotalUsd().toPlainString());
        return values;
    }

    /**
     * A member's own agreement: their alias, partner reference, share and amounts; other members only by alias.
     */
    private Map<String, String> memberValues(Contract contract, Project project, ClientCompany company,
                                             EngineerProfile member, List<ContractMember> members,
                                             Map<Long, EngineerProfile> engineers, List<ContractMilestone> milestones,
                                             String locale) {
        Map<String, String> values = engineerValues(contract, project, company, member, milestones, locale);
        ContractMember mine = members.stream().filter(m -> m.getEngineerUserId().equals(member.getUserId())).findFirst()
                .orElseThrow();
        Function<ContractMilestone, BigDecimal> myAmount = m -> payee(contract, members, m, member.getUserId()).talentAmountUsd();
        values.put("teamName", Objects.toString(contract.getTeamName(), ""));
        values.put("teamMembers", members.stream().map(m -> engineers.get(m.getEngineerUserId()).getAlias())
                .collect(Collectors.joining(", ")));
        values.put("leadAlias", engineers.get(contract.getEngineerUserId()).getAlias());
        values.put("sharePct", mine.getSharePct().toPlainString());
        values.put("teamTotalUsd", contract.getTalentTotalUsd().toPlainString());
        values.put("milestonesTable", milestonesTable(milestones, myAmount, locale));
        values.put("totalUsd", Money.sum(milestones.stream().map(myAmount).toList()).toPlainString());
        return values;
    }

    private static Payee payee(Contract contract, List<ContractMember> members, ContractMilestone milestone, long userId) {
        return ContractParties.payees(contract, members, milestone.getTalentAmountUsd()).stream()
                .filter(p -> p.engineerUserId() == userId).findFirst().orElse(null);
    }

    private static String milestonesTable(List<ContractMilestone> milestones, Function<ContractMilestone, BigDecimal> amount,
                                          String locale) {
        String header = ContractTemplateRenderer.templateLocale(locale).equals(Locales.ZH_CN)
                ? "| # | 里程碑 | 交付成果 | 截止日期 | 金额（USD） |"
                : "| # | Milestone | Deliverable | Due date | Amount (USD) |";
        StringBuilder table = new StringBuilder(header).append("\n|---|---|---|---|---|\n");
        for (ContractMilestone m : milestones) {
            table.append("| ").append(m.getSeq())
                    .append(" | ").append(cell(m.getTitle()))
                    .append(" | ").append(cell(m.getDeliverable()))
                    .append(" | ").append(m.getDueDate())
                    .append(" | ").append(amount.apply(m).toPlainString())
                    .append(" |\n");
        }
        return table.toString();
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replaceAll("\\s*\\R\\s*", " ");
    }

    private List<ContractDocument> documents(long contractId) {
        return documentMapper.selectList(new LambdaQueryWrapper<ContractDocument>()
                .eq(ContractDocument::getContractId, contractId)
                .orderByAsc(ContractDocument::getId));
    }

    private List<ContractMilestone> milestones(long contractId) {
        return milestoneMapper.selectList(new LambdaQueryWrapper<ContractMilestone>()
                .eq(ContractMilestone::getContractId, contractId)
                .orderByAsc(ContractMilestone::getSeq));
    }

    private ClientContractView clientView(Contract c, boolean detail) {
        List<ContractDocument> documents = documents(c.getId());
        boolean completed = c.getStatus() == ContractStatus.COMPLETED;
        ReviewSummary review = completed ? reviews.byContractAndSide(c.getId(), UserRole.CLIENT).orElse(null) : null;
        String alias = profileService.aliases(List.of(c.getEngineerUserId())).get(c.getEngineerUserId());
        ClientContractView.Team team = null;
        if (c.getTeamId() != null) {
            List<ContractMember> members = parties.members(c);
            Map<Long, String> aliases = profileService.aliases(members.stream().map(ContractMember::getEngineerUserId).toList());
            team = new ClientContractView.Team(c.getTeamName(), members.stream().map(m -> {
                Instant acceptedAt = engineerAcceptedAt(documents, c, m.getEngineerUserId());
                return new ClientContractView.Member(aliases.get(m.getEngineerUserId()), m.getRole(), acceptedAt != null,
                        acceptedAt);
            }).toList());
        }
        return new ClientContractView(c.getId(), c.getProjectId(), projectService.get(c.getProjectId()).getTitle(),
                c.getStatus(), alias, c.getClientTotalUsd(), clientAcceptedAt(documents),
                allEngineersAcceptedAt(documents), c.getActivatedAt(), c.getCompletedAt(), c.getCreatedAt(),
                detail ? clientDocuments(documents) : null,
                milestones(c.getId()).stream().map(ClientMilestoneView::from).toList(),
                detail ? attachmentDirectory.list(c.getId()) : null, team, review, review == null && completed);
    }

    private EngineerContractView engineerView(Contract c, long viewerUserId, boolean detail) {
        List<ContractDocument> documents = documents(c.getId());
        boolean completed = c.getStatus() == ContractStatus.COMPLETED;
        ReviewSummary review = completed ? reviews.byContractAndSide(c.getId(), UserRole.ENGINEER).orElse(null) : null;
        List<ContractMember> members = parties.members(c);
        String alias = profileService.aliases(List.of(viewerUserId)).get(viewerUserId);
        List<ContractMilestone> milestones = milestones(c.getId());
        List<EngineerMilestoneView> milestoneViews = milestones.stream().map(m -> {
            Payee payee = payee(c, members, m, viewerUserId);
            return EngineerMilestoneView.from(m, payee == null ? null : payee.talentAmountUsd());
        }).toList();
        EngineerContractView.Team team = null;
        if (c.getTeamId() != null) {
            Map<Long, String> aliases = profileService.aliases(members.stream().map(ContractMember::getEngineerUserId).toList());
            ContractMember mine = members.stream().filter(m -> m.getEngineerUserId() == viewerUserId).findFirst().orElse(null);
            team = new EngineerContractView.Team(c.getTeamId(), c.getTeamName(), mine == null ? null : mine.getRole(),
                    mine == null ? null : mine.getSharePct(),
                    Money.sum(milestoneViews.stream().map(EngineerMilestoneView::myAmountUsd).filter(Objects::nonNull).toList()),
                    members.stream().map(m -> new EngineerContractView.Member(aliases.get(m.getEngineerUserId()), m.getRole(),
                            m.getSharePct(), engineerAcceptedAt(documents, c, m.getEngineerUserId()) != null)).toList());
        }
        return new EngineerContractView(c.getId(), c.getProjectId(), projectService.get(c.getProjectId()).getTitle(),
                c.getStatus(), alias, companyService.get(c.getCompanyId()).getCountry(), c.getTalentTotalUsd(),
                clientAcceptedAt(documents), allEngineersAcceptedAt(documents), c.getActivatedAt(), c.getCompletedAt(),
                c.getCreatedAt(),
                detail ? ownDocuments(documents, c, viewerUserId) : null,
                milestoneViews,
                detail ? attachmentDirectory.list(c.getId()) : null, team, review,
                review == null && completed && c.getEngineerUserId() == viewerUserId);
    }

    /**
     * Each party sees only the text of its own document: the client agreement carries the client price and an
     * engineer agreement carries that engineer's partner reference, so showing any other document would leak the
     * platform margin or another member's partner data. Other acceptances are exposed as timestamps/flags.
     */
    private static List<ContractDocumentView> clientDocuments(List<ContractDocument> documents) {
        return documents.stream().filter(d -> d.getKind() == DocumentKind.CLIENT_SERVICES).map(ContractDocumentView::from).toList();
    }

    private static List<ContractDocumentView> ownDocuments(List<ContractDocument> documents, Contract contract, long viewerUserId) {
        return documents.stream()
                .filter(d -> d.getKind() == DocumentKind.ENGINEER_DELIVERABLES && owner(d, contract) == viewerUserId)
                .map(ContractDocumentView::from).toList();
    }

    private static Instant clientAcceptedAt(List<ContractDocument> documents) {
        return documents.stream().filter(d -> d.getKind() == DocumentKind.CLIENT_SERVICES).findFirst()
                .map(ContractDocument::getAcceptedAt).orElse(null);
    }

    private static Instant engineerAcceptedAt(List<ContractDocument> documents, Contract contract, long engineerUserId) {
        return documents.stream()
                .filter(d -> d.getKind() == DocumentKind.ENGINEER_DELIVERABLES && owner(d, contract) == engineerUserId)
                .findFirst().map(ContractDocument::getAcceptedAt).orElse(null);
    }

    /**
     * When the last engineer document was accepted, or null while any is pending.
     */
    private static Instant allEngineersAcceptedAt(List<ContractDocument> documents) {
        List<ContractDocument> engineerDocs = documents.stream().filter(d -> d.getKind() == DocumentKind.ENGINEER_DELIVERABLES).toList();
        if (engineerDocs.isEmpty() || engineerDocs.stream().anyMatch(d -> d.getAcceptedAt() == null)) {
            return null;
        }
        return engineerDocs.stream().map(ContractDocument::getAcceptedAt).max(Comparator.naturalOrder()).orElse(null);
    }
}
