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
import com.qiao.project.domain.MilestonePrice;
import com.qiao.project.domain.Project;
import com.qiao.project.domain.ProjectMilestone;
import com.qiao.project.service.ApplicationService;
import com.qiao.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contracts: PENDING_SIGNATURES → (both documents accepted) ACTIVE → (all milestones final) COMPLETED.
 */
@Service
public class ContractService {

    private final ContractMapper contractMapper;
    private final ContractDocumentMapper documentMapper;
    private final ContractMilestoneMapper milestoneMapper;
    private final ApplicationService applicationService;
    private final ProjectService projectService;
    private final CompanyService companyService;
    private final EngineerProfileService profileService;
    private final UserService userService;
    private final ContractTemplateRenderer renderer;
    private final ContractProperties contractProperties;
    private final AppProperties appProperties;
    private final FeeCalculator feeCalculator;
    private final AttachmentDirectory attachmentDirectory;
    private final ContractNotifier notifier;
    private final AuditService auditService;
    private final Clock clock;

    public ContractService(ContractMapper contractMapper, ContractDocumentMapper documentMapper,
                           ContractMilestoneMapper milestoneMapper, ApplicationService applicationService,
                           ProjectService projectService, CompanyService companyService,
                           EngineerProfileService profileService, UserService userService,
                           ContractTemplateRenderer renderer, ContractProperties contractProperties,
                           AppProperties appProperties, FeeCalculator feeCalculator,
                           AttachmentDirectory attachmentDirectory, ContractNotifier notifier,
                           AuditService auditService, Clock clock) {
        this.contractMapper = contractMapper;
        this.documentMapper = documentMapper;
        this.milestoneMapper = milestoneMapper;
        this.applicationService = applicationService;
        this.projectService = projectService;
        this.companyService = companyService;
        this.profileService = profileService;
        this.userService = userService;
        this.renderer = renderer;
        this.contractProperties = contractProperties;
        this.appProperties = appProperties;
        this.feeCalculator = feeCalculator;
        this.attachmentDirectory = attachmentDirectory;
        this.notifier = notifier;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Hires a SELECTED application: creates the contract, its milestones and both documents, marks the application
     * HIRED (declining the others) and the project IN_CONTRACT.
     */
    @Transactional
    public ClientContractView hire(long clientUserId, long applicationId) {
        Application application = applicationService.lockForHire(clientUserId, applicationId);
        Project project = projectService.get(application.getProjectId());
        ClientCompany company = companyService.requireApproved(project.getCompanyId());
        EngineerProfile engineer = profileService.requireApproved(application.getEngineerUserId());

        Contract contract = new Contract();
        contract.setProjectId(project.getId());
        contract.setApplicationId(application.getId());
        contract.setCompanyId(company.getId());
        contract.setEngineerUserId(application.getEngineerUserId());
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

        String clientLocale = userService.get(clientUserId).getLocale();
        String engineerLocale = userService.get(engineer.getUserId()).getLocale();
        insertDocument(contract, DocumentKind.CLIENT_SERVICES, clientLocale,
                templateValues(contract, project, company, engineer, milestones, DocumentKind.CLIENT_SERVICES, clientLocale));
        insertDocument(contract, DocumentKind.ENGINEER_DELIVERABLES, engineerLocale,
                templateValues(contract, project, company, engineer, milestones, DocumentKind.ENGINEER_DELIVERABLES,
                        engineerLocale));

        applicationService.markHired(application);
        projectService.markInContract(project.getId());
        auditService.log(Actor.user(clientUserId), AuditAction.CONTRACT_CREATED, "contract", contract.getId(),
                Map.of("applicationId", applicationId, "projectId", project.getId()));
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
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                        .eq(Contract::getEngineerUserId, userId)
                        .orderByDesc(Contract::getId))
                .stream().map(c -> engineerView(c, false)).toList();
    }

    public ClientContractView getForClient(long userId, long contractId) {
        return clientView(requireSide(userId, contractId, UserRole.CLIENT), true);
    }

    public EngineerContractView getForEngineer(long userId, long contractId) {
        return engineerView(requireSide(userId, contractId, UserRole.ENGINEER), true);
    }

    /**
     * Accepts the caller's own document; the contract becomes ACTIVE once both documents are accepted.
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
        if (document == null || !document.getContractId().equals(contractId) || document.getKind() != expectedKind) {
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
     * @throws ApiException {@code NOT_FOUND} unless the user is the engineer or a member of the client company
     */
    public ContractParticipant requireParticipant(long userId, long contractId) {
        Contract contract = contractMapper.selectById(contractId);
        if (contract != null) {
            if (contract.getEngineerUserId() == userId) {
                return new ContractParticipant(contract, UserRole.ENGINEER);
            }
            if (companyService.isMember(userId, contract.getCompanyId())) {
                return new ContractParticipant(contract, UserRole.CLIENT);
            }
        }
        throw ApiException.notFound("Contract");
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

    public List<Contract> listByEngineer(long engineerUserId) {
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>().eq(Contract::getEngineerUserId, engineerUserId));
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
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEngineerUserId, userId)
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
        return side == UserRole.ENGINEER ? contract.getEngineerUserId() == userId
                : companyService.isMember(userId, contract.getCompanyId());
    }

    private void insertDocument(Contract contract, DocumentKind kind, String locale, Map<String, String> values) {
        ContractTemplateRenderer.RenderedDocument rendered = renderer.render(kind, locale, values);
        ContractDocument document = new ContractDocument();
        document.setContractId(contract.getId());
        document.setKind(kind);
        document.setTemplateVersion(rendered.templateVersion());
        document.setLocale(rendered.locale());
        document.setBody(rendered.body());
        document.setSha256(rendered.sha256());
        documentMapper.insert(document);
    }

    private Map<String, String> templateValues(Contract contract, Project project, ClientCompany company,
                                               EngineerProfile engineer, List<ContractMilestone> milestones,
                                               DocumentKind kind, String locale) {
        boolean client = kind == DocumentKind.CLIENT_SERVICES;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("contractNumber", String.format("QIAO-%d-%06d", LocalDate.now(clock).getYear(), contract.getId()));
        values.put("date", LocalDate.now(clock).toString());
        values.put("hkEntityName", contractProperties.hkEntityName());
        values.put("partnerEntityName", contractProperties.partnerEntityName());
        values.put("platformUrl", appProperties.baseUrl());
        values.put("clientCompanyName", company.getName());
        values.put("clientCountry", company.getCountry());
        values.put("engineerAlias", engineer.getAlias());
        values.put("engineerPartnerRef", engineer.getPartnerRef());
        values.put("projectTitle", project.getTitle());
        values.put("projectDescription", project.getDescription());
        values.put("startDate", project.getStartDate().toString());
        values.put("endDate", project.getEndDate().toString());
        values.put("milestonesTable", milestonesTable(milestones, client, locale));
        values.put("totalUsd", (client ? contract.getClientTotalUsd() : contract.getTalentTotalUsd()).toPlainString());
        return values;
    }

    private static String milestonesTable(List<ContractMilestone> milestones, boolean clientAmounts, String locale) {
        String header = ContractTemplateRenderer.templateLocale(locale).equals(Locales.ZH_CN)
                ? "| # | 里程碑 | 交付成果 | 截止日期 | 金额（USD） |"
                : "| # | Milestone | Deliverable | Due date | Amount (USD) |";
        StringBuilder table = new StringBuilder(header).append("\n|---|---|---|---|---|\n");
        for (ContractMilestone m : milestones) {
            BigDecimal amount = clientAmounts ? m.getClientAmountUsd() : m.getTalentAmountUsd();
            table.append("| ").append(m.getSeq())
                    .append(" | ").append(cell(m.getTitle()))
                    .append(" | ").append(cell(m.getDeliverable()))
                    .append(" | ").append(m.getDueDate())
                    .append(" | ").append(amount.toPlainString())
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
        String alias = profileService.aliases(List.of(c.getEngineerUserId())).get(c.getEngineerUserId());
        return new ClientContractView(c.getId(), c.getProjectId(), projectService.get(c.getProjectId()).getTitle(),
                c.getStatus(), alias, c.getClientTotalUsd(), acceptedAt(documents, DocumentKind.CLIENT_SERVICES),
                acceptedAt(documents, DocumentKind.ENGINEER_DELIVERABLES), c.getActivatedAt(), c.getCompletedAt(),
                c.getCreatedAt(),
                detail ? ownDocuments(documents, DocumentKind.CLIENT_SERVICES) : null,
                milestones(c.getId()).stream().map(ClientMilestoneView::from).toList(),
                detail ? attachmentDirectory.list(c.getId()) : null);
    }

    private EngineerContractView engineerView(Contract c, boolean detail) {
        List<ContractDocument> documents = documents(c.getId());
        String alias = profileService.aliases(List.of(c.getEngineerUserId())).get(c.getEngineerUserId());
        return new EngineerContractView(c.getId(), c.getProjectId(), projectService.get(c.getProjectId()).getTitle(),
                c.getStatus(), alias, companyService.get(c.getCompanyId()).getCountry(), c.getTalentTotalUsd(),
                acceptedAt(documents, DocumentKind.CLIENT_SERVICES),
                acceptedAt(documents, DocumentKind.ENGINEER_DELIVERABLES), c.getActivatedAt(), c.getCompletedAt(),
                c.getCreatedAt(),
                detail ? ownDocuments(documents, DocumentKind.ENGINEER_DELIVERABLES) : null,
                milestones(c.getId()).stream().map(EngineerMilestoneView::from).toList(),
                detail ? attachmentDirectory.list(c.getId()) : null);
    }

    /**
     * Each party sees only the text of its own document: the client agreement carries the client price and the
     * engineer agreement carries the partner reference, so showing both would leak the platform margin and partner data.
     * The other party's acceptance is exposed through the view's acceptedAt fields.
     */
    private static List<ContractDocumentView> ownDocuments(List<ContractDocument> documents, DocumentKind kind) {
        return documents.stream().filter(d -> d.getKind() == kind).map(ContractDocumentView::from).toList();
    }

    private static Instant acceptedAt(List<ContractDocument> documents, DocumentKind kind) {
        return documents.stream().filter(d -> d.getKind() == kind).findFirst().map(ContractDocument::getAcceptedAt).orElse(null);
    }
}
