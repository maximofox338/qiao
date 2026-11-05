package com.qiao.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.client.domain.ClientCompany;
import com.qiao.client.service.CompanyService;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.money.FeeCalculator;
import com.qiao.common.money.Money;
import com.qiao.common.persistence.StatusCounts;
import com.qiao.common.review.ReviewDecision;
import com.qiao.common.util.Texts;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.compliance.service.KeywordScanner;
import com.qiao.project.domain.Application;
import com.qiao.project.domain.ApplicationMapper;
import com.qiao.project.domain.ApplicationMember;
import com.qiao.project.domain.ApplicationMemberMapper;
import com.qiao.project.domain.ApplicationStatus;
import com.qiao.project.domain.Project;
import com.qiao.project.domain.ProjectMapper;
import com.qiao.project.domain.ProjectMilestone;
import com.qiao.project.domain.ProjectMilestoneMapper;
import com.qiao.project.domain.ProjectStatus;
import com.qiao.project.web.AdminProjectView;
import com.qiao.project.web.ClientProjectView;
import com.qiao.project.web.EngineerProjectView;
import com.qiao.project.web.MilestoneRequest;
import com.qiao.project.web.ProjectMilestoneView;
import com.qiao.project.web.ProjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Projects: DRAFT → SUBMITTED → OPEN | CHANGES_REQUESTED (→ SUBMITTED) | REJECTED; OPEN → IN_CONTRACT → COMPLETED;
 * the client can cancel from DRAFT/SUBMITTED/CHANGES_REQUESTED/OPEN.
 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final ApplicationMapper applicationMapper;
    private final ApplicationMemberMapper applicationMemberMapper;
    private final ProjectValidator validator;
    private final CategoryService categoryService;
    private final CompanyService companyService;
    private final KeywordScanner keywordScanner;
    private final FeeCalculator feeCalculator;
    private final AuditService auditService;
    private final Clock clock;

    public ProjectService(ProjectMapper projectMapper, ProjectMilestoneMapper milestoneMapper,
                          ApplicationMapper applicationMapper, ApplicationMemberMapper applicationMemberMapper,
                          ProjectValidator validator,
                          CategoryService categoryService, CompanyService companyService, KeywordScanner keywordScanner, FeeCalculator feeCalculator,
                          AuditService auditService, Clock clock) {
        this.projectMapper = projectMapper;
        this.milestoneMapper = milestoneMapper;
        this.applicationMapper = applicationMapper;
        this.applicationMemberMapper = applicationMemberMapper;
        this.validator = validator;
        this.categoryService = categoryService;
        this.companyService = companyService;
        this.keywordScanner = keywordScanner;
        this.feeCalculator = feeCalculator;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ client

    @Transactional
    public ClientProjectView create(long userId, ProjectRequest request) {
        ClientCompany company = companyService.getOrCreateForUser(userId);
        boolean submit = Boolean.TRUE.equals(request.submit());
        validator.validate(request, submit);
        if (submit) {
            companyService.requireApproved(company.getId());
        }
        Project project = new Project();
        project.setCompanyId(company.getId());
        applyRequest(project, request);
        project.setStatus(submit ? ProjectStatus.SUBMITTED : ProjectStatus.DRAFT);
        projectMapper.insert(project);
        replaceMilestones(project.getId(), request.milestones());
        return clientView(project);
    }

    @Transactional
    public ClientProjectView update(long userId, long projectId, ProjectRequest request) {
        Project project = lockOwned(userId, projectId);
        requireEditable(project);
        boolean submit = Boolean.TRUE.equals(request.submit());
        validator.validate(request, submit);
        if (submit) {
            companyService.requireApproved(project.getCompanyId());
            project.setStatus(ProjectStatus.SUBMITTED);
        }
        applyRequest(project, request);
        projectMapper.updateById(project);
        replaceMilestones(projectId, request.milestones());
        return clientView(project);
    }

    @Transactional
    public ClientProjectView submit(long userId, long projectId) {
        Project project = lockOwned(userId, projectId);
        requireEditable(project);
        validator.validate(toRequest(project, milestones(projectId)), true);
        companyService.requireApproved(project.getCompanyId());
        project.setStatus(ProjectStatus.SUBMITTED);
        projectMapper.updateById(project);
        return clientView(project);
    }

    @Transactional
    public ClientProjectView cancel(long userId, long projectId) {
        Project project = lockOwned(userId, projectId);
        if (!ProjectStatus.CANCELLABLE.contains(project.getStatus())) {
            throw ApiException.invalidState("Project cannot be cancelled in status " + project.getStatus());
        }
        cancel(project);
        return clientView(project);
    }

    public List<ClientProjectView> listForClient(long userId) {
        ClientCompany company = companyService.companyOf(userId);
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getCompanyId, company.getId())
                .orderByDesc(Project::getId));
        Map<Long, List<ProjectMilestoneView>> milestones = milestoneViews(projects.stream().map(Project::getId).toList());
        Map<Long, Long> counts = applicationCounts(projects.stream().map(Project::getId).toList());
        return projects.stream().map(p -> ClientProjectView.from(p, milestones.getOrDefault(p.getId(), List.of()),
                counts.getOrDefault(p.getId(), 0L))).toList();
    }

    public ClientProjectView getForClient(long userId, long projectId) {
        return clientView(getOwned(userId, projectId));
    }

    /**
     * @throws ApiException {@code NOT_FOUND} unless the user is a member of the project's company
     */
    public Project getOwned(long userId, long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !companyService.isMember(userId, project.getCompanyId())) {
            throw ApiException.notFound("Project");
        }
        return project;
    }

    // ------------------------------------------------------------------ engineer

    public PageResult<EngineerProjectView> listOpen(long engineerUserId, String q, String categoryCode,
                                                    PageQuery pageQuery) {
        String term = Texts.trimToNull(q);
        String category = Texts.trimToNull(categoryCode);
        var page = projectMapper.selectPage(pageQuery.toPage(), new LambdaQueryWrapper<Project>()
                .eq(Project::getStatus, ProjectStatus.OPEN)
                .eq(category != null, Project::getCategoryCode, category)
                .and(term != null, w -> w.like(Project::getTitle, term).or().like(Project::getDescription, term))
                .orderByDesc(Project::getReviewedAt)
                .orderByDesc(Project::getId));
        return PageResult.of(page, engineerViews(engineerUserId, page.getRecords()));
    }

    /**
     * Visible when OPEN, or when the engineer has applied to it.
     */
    public EngineerProjectView getForEngineer(long engineerUserId, long projectId) {
        Project project = projectMapper.selectById(projectId);
        boolean applied = project != null && appliedProjectIds(engineerUserId, List.of(projectId)).contains(projectId);
        if (project == null || (project.getStatus() != ProjectStatus.OPEN && !applied)) {
            throw ApiException.notFound("Project");
        }
        return engineerViews(engineerUserId, List.of(project)).getFirst();
    }

    // ------------------------------------------------------------------ admin

    public PageResult<AdminProjectView> adminList(ProjectStatus status, PageQuery pageQuery) {
        var page = projectMapper.selectPage(pageQuery.toPage(), new LambdaQueryWrapper<Project>()
                .eq(status != null, Project::getStatus, status)
                .orderByDesc(Project::getUpdatedAt));
        List<Project> projects = page.getRecords();
        Map<Long, ClientCompany> companies = companyService.byIds(projects.stream().map(Project::getCompanyId).collect(Collectors.toSet()));
        Map<Long, List<ProjectMilestoneView>> milestones = milestoneViews(projects.stream().map(Project::getId).toList());
        return PageResult.of(page, p -> AdminProjectView.from(p, companies.get(p.getCompanyId()),
                milestones.getOrDefault(p.getId(), List.of())));
    }

    public AdminProjectView adminGet(long projectId) {
        Project project = get(projectId);
        return AdminProjectView.from(project, companyService.get(project.getCompanyId()),
                milestoneViews(List.of(projectId)).getOrDefault(projectId, List.of()));
    }

    @Transactional
    public Project review(long projectId, ReviewDecision decision, String note, long adminId) {
        Project project = projectMapper.selectByIdForUpdate(projectId);
        if (project == null) {
            throw ApiException.notFound("Project");
        }
        if (project.getStatus() != ProjectStatus.SUBMITTED) {
            throw ApiException.invalidState("Project is not submitted");
        }
        ProjectStatus next = switch (decision) {
            case APPROVE -> {
                if (!categoryService.isAllowed(project.getCategoryCode())) {
                    throw new ApiException(ErrorCode.CATEGORY_NOT_ALLOWED);
                }
                validator.validate(toRequest(project, milestones(projectId)), true);
                companyService.requireApproved(project.getCompanyId());
                yield ProjectStatus.OPEN;
            }
            case REJECT -> ProjectStatus.REJECTED;
            case REQUEST_CHANGES -> ProjectStatus.CHANGES_REQUESTED;
        };
        project.setStatus(next);
        project.setReviewNote(Texts.trimToNull(note));
        project.setReviewedBy(adminId);
        project.setReviewedAt(clock.instant());
        projectMapper.updateById(project);
        auditService.log(Actor.admin(adminId), AuditAction.PROJECT_REVIEWED, "project", projectId,
                Map.of("decision", decision.name(), "flags", project.getFlags() == null ? 0 : project.getFlags().size()));
        return project;
    }

    public Map<String, Long> countByStatus() {
        return StatusCounts.count(projectMapper, ProjectStatus.class);
    }

    // ------------------------------------------------------------------ other modules

    public Project get(long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ApiException.notFound("Project");
        }
        return project;
    }

    public List<ProjectMilestone> milestones(long projectId) {
        return milestoneMapper.selectList(new LambdaQueryWrapper<ProjectMilestone>()
                .eq(ProjectMilestone::getProjectId, projectId)
                .orderByAsc(ProjectMilestone::getSeq));
    }

    public Map<Long, String> titles(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return projectMapper.selectByIds(projectIds).stream().collect(Collectors.toMap(Project::getId, Project::getTitle));
    }

    /**
     * OPEN → IN_CONTRACT when an engineer is hired.
     */
    public void markInContract(long projectId) {
        transition(projectId, ProjectStatus.OPEN, ProjectStatus.IN_CONTRACT, ErrorCode.PROJECT_NOT_OPEN);
    }

    /**
     * IN_CONTRACT → COMPLETED when the contract completes.
     */
    public void markCompleted(long projectId) {
        transition(projectId, ProjectStatus.IN_CONTRACT, ProjectStatus.COMPLETED, ErrorCode.INVALID_STATE);
    }

    /**
     * IN_CONTRACT → CANCELLED when the contract is terminated before activation.
     */
    public void cancelContracted(long projectId) {
        transition(projectId, ProjectStatus.IN_CONTRACT, ProjectStatus.CANCELLED, ErrorCode.INVALID_STATE);
    }

    /**
     * Cancels every not-yet-contracted project of a company (blocked company or deleted client).
     */
    public void cancelActiveProjectsOfCompany(long companyId) {
        projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getCompanyId, companyId)
                        .in(Project::getStatus, ProjectStatus.CANCELLABLE))
                .forEach(this::cancel);
    }

    // ------------------------------------------------------------------ internals

    private void cancel(Project project) {
        project.setStatus(ProjectStatus.CANCELLED);
        projectMapper.updateById(project);
        applicationMapper.update(null, new LambdaUpdateWrapper<Application>()
                .set(Application::getStatus, ApplicationStatus.DECLINED)
                .set(Application::getUpdatedAt, clock.instant())
                .eq(Application::getProjectId, project.getId())
                .in(Application::getStatus, ApplicationStatus.ACTIVE));
    }

    private void transition(long projectId, ProjectStatus from, ProjectStatus to, ErrorCode error) {
        int updated = projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .set(Project::getStatus, to)
                .set(Project::getUpdatedAt, clock.instant())
                .eq(Project::getId, projectId)
                .eq(Project::getStatus, from));
        if (updated == 0) {
            throw new ApiException(error, "Project is not " + from);
        }
    }

    private Project lockOwned(long userId, long projectId) {
        Project project = projectMapper.selectByIdForUpdate(projectId);
        if (project == null || !companyService.isMember(userId, project.getCompanyId())) {
            throw ApiException.notFound("Project");
        }
        return project;
    }

    private static void requireEditable(Project project) {
        if (!ProjectStatus.EDITABLE.contains(project.getStatus())) {
            throw ApiException.invalidState("Project cannot be edited in status " + project.getStatus());
        }
    }

    private void applyRequest(Project project, ProjectRequest request) {
        List<String> skills = request.skills() == null ? List.of()
                : request.skills().stream().map(String::trim).distinct().toList();
        project.setTitle(request.title().trim());
        project.setDescription(request.description().trim());
        project.setCategoryCode(request.categoryCode());
        project.setSkills(skills);
        project.setBudgetUsd(Money.of(request.budgetUsd()));
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setDeclarations(request.declarations());
        List<String> texts = new ArrayList<>(List.of(project.getTitle(), project.getDescription(), String.join(", ", skills)));
        request.milestones().forEach(m -> {
            texts.add(m.title());
            texts.add(m.deliverable());
        });
        project.setFlags(keywordScanner.scan(texts));
    }

    private void replaceMilestones(long projectId, List<MilestoneRequest> requests) {
        milestoneMapper.delete(new LambdaQueryWrapper<ProjectMilestone>().eq(ProjectMilestone::getProjectId, projectId));
        for (int i = 0; i < requests.size(); i++) {
            MilestoneRequest request = requests.get(i);
            ProjectMilestone milestone = new ProjectMilestone();
            milestone.setProjectId(projectId);
            milestone.setSeq(i + 1);
            milestone.setTitle(request.title().trim());
            milestone.setDeliverable(request.deliverable().trim());
            milestone.setDueDate(request.dueDate());
            milestone.setAmountUsd(Money.of(request.amountUsd()));
            milestoneMapper.insert(milestone);
        }
    }

    private static ProjectRequest toRequest(Project project, List<ProjectMilestone> milestones) {
        return new ProjectRequest(project.getTitle(), project.getDescription(), project.getCategoryCode(),
                project.getSkills(), project.getBudgetUsd(), project.getStartDate(), project.getEndDate(),
                milestones.stream().map(m -> new MilestoneRequest(m.getTitle(), m.getDeliverable(), m.getDueDate(),
                        m.getAmountUsd())).toList(),
                project.getDeclarations(), true);
    }

    private ClientProjectView clientView(Project project) {
        return ClientProjectView.from(project, milestoneViews(List.of(project.getId())).getOrDefault(project.getId(), List.of()),
                applicationCounts(List.of(project.getId())).getOrDefault(project.getId(), 0L));
    }

    private Map<Long, List<ProjectMilestoneView>> milestoneViews(List<Long> projectIds) {
        return milestonesByProject(projectIds).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().stream().map(ProjectMilestoneView::from).toList()));
    }

    private Map<Long, List<ProjectMilestone>> milestonesByProject(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return milestoneMapper.selectList(new LambdaQueryWrapper<ProjectMilestone>()
                        .in(ProjectMilestone::getProjectId, projectIds)
                        .orderByAsc(ProjectMilestone::getSeq))
                .stream().collect(Collectors.groupingBy(ProjectMilestone::getProjectId));
    }

    private Map<Long, Long> applicationCounts(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        applicationMapper.selectMaps(new QueryWrapper<Application>()
                        .select("project_id", "COUNT(*) AS total")
                        .in("project_id", projectIds)
                        .ne("status", ApplicationStatus.WITHDRAWN.name())
                        .groupBy("project_id"))
                .forEach(row -> counts.put(((Number) row.get("project_id")).longValue(), ((Number) row.get("total")).longValue()));
        return counts;
    }

    /**
     * Projects (among {@code projectIds}) the engineer applied to, alone or as a member of a team application.
     */
    private Set<Long> appliedProjectIds(long engineerUserId, Collection<Long> projectIds) {
        Set<Long> applied = applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                        .select(Application::getProjectId)
                        .eq(Application::getEngineerUserId, engineerUserId)
                        .in(Application::getProjectId, projectIds))
                .stream().map(Application::getProjectId).collect(Collectors.toCollection(HashSet::new));
        applicationMemberMapper.selectList(new LambdaQueryWrapper<ApplicationMember>()
                        .select(ApplicationMember::getProjectId)
                        .eq(ApplicationMember::getEngineerUserId, engineerUserId)
                        .in(ApplicationMember::getProjectId, projectIds))
                .forEach(m -> applied.add(m.getProjectId()));
        return applied;
    }

    private List<EngineerProjectView> engineerViews(long engineerUserId, List<Project> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> ids = projects.stream().map(Project::getId).toList();
        Map<Long, List<ProjectMilestone>> milestones = milestonesByProject(ids);
        Map<Long, ClientCompany> companies = companyService.byIds(projects.stream().map(Project::getCompanyId).collect(Collectors.toSet()));
        Set<Long> applied = appliedProjectIds(engineerUserId, ids);
        return projects.stream().map(p -> new EngineerProjectView(p.getId(), p.getTitle(), p.getDescription(),
                p.getCategoryCode(), p.getSkills() == null ? List.of() : p.getSkills(),
                companies.containsKey(p.getCompanyId()) ? companies.get(p.getCompanyId()).getCountry() : null,
                feeCalculator.talentAmount(p.getBudgetUsd()), p.getStartDate(), p.getEndDate(),
                milestones.getOrDefault(p.getId(), List.of()).stream()
                        .map(m -> new EngineerProjectView.Milestone(m.getId(), m.getSeq(), m.getTitle(),
                                m.getDeliverable(), m.getDueDate(), feeCalculator.talentAmount(m.getAmountUsd())))
                        .toList(),
                applied.contains(p.getId()))).toList();
    }
}
