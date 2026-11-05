package com.qiao.client.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.account.domain.UserRole;
import com.qiao.account.service.OnboardingStatusProvider;
import com.qiao.account.service.UserRegisteredEvent;
import com.qiao.account.service.UserService;
import com.qiao.client.domain.ClientCompany;
import com.qiao.client.domain.ClientCompanyMapper;
import com.qiao.client.domain.ClientMember;
import com.qiao.client.domain.ClientMemberMapper;
import com.qiao.client.domain.CompanyStatus;
import com.qiao.client.domain.SanctionsStatus;
import com.qiao.client.web.AdminCompanyView;
import com.qiao.client.web.CompanyRequest;
import com.qiao.client.web.CompanyView;
import com.qiao.client.web.DeclarationsRequest;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.error.ValidationCodes;
import com.qiao.common.persistence.StatusCounts;
import com.qiao.common.review.ReviewDecision;
import com.qiao.common.util.Hashing;
import com.qiao.common.util.Texts;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.compliance.service.LegalDocument;
import com.qiao.compliance.service.LegalService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Client companies: DRAFT → (declarations) PENDING_REVIEW → APPROVED | REJECTED; APPROVED → BLOCKED on a
 * sanctions hit. Phase 1 has exactly one member user per company.
 */
@Service
public class CompanyService implements OnboardingStatusProvider {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private final ClientCompanyMapper companyMapper;
    private final ClientMemberMapper memberMapper;
    private final LegalService legalService;
    private final UserService userService;
    private final AuditService auditService;
    private final Clock clock;

    public CompanyService(ClientCompanyMapper companyMapper, ClientMemberMapper memberMapper,
                          LegalService legalService, UserService userService, AuditService auditService,
                          Clock clock) {
        this.companyMapper = companyMapper;
        this.memberMapper = memberMapper;
        this.legalService = legalService;
        this.userService = userService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event.role() == UserRole.CLIENT) {
            getOrCreateForUser(event.userId());
        }
    }

    @Override
    public UserRole role() {
        return UserRole.CLIENT;
    }

    @Override
    public String onboardingStatus(long userId) {
        ClientMember member = findMember(userId);
        return member == null ? CompanyStatus.DRAFT.name() : get(member.getCompanyId()).getStatus().name();
    }

    public ClientCompany getOrCreateForUser(long userId) {
        ClientMember member = findMember(userId);
        if (member != null) {
            return get(member.getCompanyId());
        }
        ClientCompany company = new ClientCompany();
        company.setStatus(CompanyStatus.DRAFT);
        company.setSanctionsStatus(SanctionsStatus.PENDING);
        companyMapper.insert(company);
        ClientMember newMember = new ClientMember();
        newMember.setCompanyId(company.getId());
        newMember.setUserId(userId);
        memberMapper.insert(newMember);
        return company;
    }

    @Transactional
    public CompanyView getOwn(long userId) {
        return CompanyView.from(getOrCreateForUser(userId));
    }

    @Transactional
    public CompanyView update(long userId, CompanyRequest request) {
        ClientCompany company = lockOwn(userId);
        if (company.getStatus() != CompanyStatus.DRAFT && company.getStatus() != CompanyStatus.REJECTED) {
            throw ApiException.invalidState("Company details cannot be changed in status " + company.getStatus());
        }
        String country = request.country().trim().toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRIES.contains(country)) {
            throw ApiException.validation("country", ValidationCodes.INVALID);
        }
        company.setName(request.name().trim());
        company.setCountry(country);
        company.setWebsite(Texts.trimToNull(request.website()));
        company.setSize(Texts.trimToNull(request.size()));
        company.setStatus(CompanyStatus.DRAFT);
        companyMapper.updateById(company);
        return CompanyView.from(company);
    }

    /**
     * Accepts the current client declarations; stores the version and the SHA-256 of the text shown to the user.
     */
    @Transactional
    public CompanyView acceptDeclarations(long userId, DeclarationsRequest request) {
        if (!Boolean.TRUE.equals(request.accepted())) {
            throw new ApiException(ErrorCode.DECLARATIONS_REQUIRED);
        }
        LegalDocument declarations = legalService.get(LegalService.CLIENT_DECLARATIONS, userService.get(userId).getLocale());
        if (!declarations.version().equals(request.version().trim())) {
            throw ApiException.validation("version", ValidationCodes.INVALID);
        }
        ClientCompany company = lockOwn(userId);
        if (company.getStatus() != CompanyStatus.DRAFT && company.getStatus() != CompanyStatus.REJECTED) {
            throw ApiException.invalidState("Declarations already submitted");
        }
        Map<String, String> missing = new LinkedHashMap<>();
        if (company.getName() == null) {
            missing.put("name", ValidationCodes.REQUIRED);
        }
        if (company.getCountry() == null) {
            missing.put("country", ValidationCodes.REQUIRED);
        }
        if (!missing.isEmpty()) {
            throw ApiException.validation(missing);
        }
        company.setDeclarationsVersion(declarations.version());
        company.setDeclarationsHash(Hashing.sha256Hex(declarations.body()));
        company.setDeclarationsAt(clock.instant());
        company.setStatus(CompanyStatus.PENDING_REVIEW);
        company.setReviewNote(null);
        companyMapper.updateById(company);
        auditService.log(Actor.user(userId), AuditAction.COMPANY_DECLARATIONS_ACCEPTED, "client_company",
                company.getId(), Map.of("version", declarations.version(), "locale", declarations.locale(),
                        "sha256", company.getDeclarationsHash()));
        return CompanyView.from(company);
    }

    public ClientCompany get(long companyId) {
        ClientCompany company = companyMapper.selectById(companyId);
        if (company == null) {
            throw ApiException.notFound("Company");
        }
        return company;
    }

    /**
     * Company of a client user (created on registration).
     */
    public ClientCompany companyOf(long userId) {
        ClientMember member = findMember(userId);
        if (member == null) {
            throw ApiException.notFound("Company");
        }
        return get(member.getCompanyId());
    }

    public Optional<ClientCompany> findCompanyOf(long userId) {
        return Optional.ofNullable(findMember(userId)).map(member -> get(member.getCompanyId()));
    }

    /**
     * @throws ApiException {@code COMPANY_NOT_APPROVED}
     */
    public ClientCompany requireApproved(long companyId) {
        ClientCompany company = get(companyId);
        if (company.getStatus() != CompanyStatus.APPROVED) {
            throw new ApiException(ErrorCode.COMPANY_NOT_APPROVED);
        }
        return company;
    }

    public boolean isMember(long userId, long companyId) {
        return memberMapper.exists(new LambdaQueryWrapper<ClientMember>()
                .eq(ClientMember::getUserId, userId)
                .eq(ClientMember::getCompanyId, companyId));
    }

    public List<Long> memberUserIds(long companyId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ClientMember>().eq(ClientMember::getCompanyId, companyId))
                .stream().map(ClientMember::getUserId).toList();
    }

    public Map<Long, ClientCompany> byIds(Collection<Long> companyIds) {
        if (companyIds.isEmpty()) {
            return Map.of();
        }
        return companyMapper.selectByIds(companyIds).stream()
                .collect(Collectors.toMap(ClientCompany::getId, Function.identity()));
    }

    public PageResult<AdminCompanyView> adminList(CompanyStatus status, PageQuery pageQuery) {
        var page = companyMapper.selectPage(pageQuery.toPage(), new LambdaQueryWrapper<ClientCompany>()
                .eq(status != null, ClientCompany::getStatus, status)
                .orderByDesc(ClientCompany::getUpdatedAt));
        Map<Long, List<AdminCompanyView.Member>> members = members(page.getRecords().stream().map(ClientCompany::getId).toList());
        return PageResult.of(page, c -> AdminCompanyView.from(c, members.getOrDefault(c.getId(), List.of())));
    }

    public AdminCompanyView adminGet(long companyId) {
        ClientCompany company = get(companyId);
        return AdminCompanyView.from(company, members(List.of(companyId)).getOrDefault(companyId, List.of()));
    }

    @Transactional
    public ClientCompany review(long companyId, ReviewDecision decision, SanctionsStatus sanctionsStatus, String note,
                                long adminId) {
        ClientCompany company = companyMapper.selectByIdForUpdate(companyId);
        if (company == null) {
            throw ApiException.notFound("Company");
        }
        CompanyStatus current = company.getStatus();
        CompanyStatus next = switch (decision) {
            case APPROVE -> {
                if (sanctionsStatus != SanctionsStatus.CLEAR) {
                    throw new ApiException(ErrorCode.SANCTIONS_NOT_CLEAR);
                }
                if (current != CompanyStatus.PENDING_REVIEW && current != CompanyStatus.APPROVED) {
                    throw ApiException.invalidState("Company is not pending review");
                }
                yield CompanyStatus.APPROVED;
            }
            case REJECT -> {
                if (current == CompanyStatus.PENDING_REVIEW) {
                    yield CompanyStatus.REJECTED;
                }
                if (current == CompanyStatus.APPROVED && sanctionsStatus == SanctionsStatus.HIT) {
                    yield CompanyStatus.BLOCKED;
                }
                throw ApiException.invalidState("Company cannot be rejected in status " + current);
            }
            case REQUEST_CHANGES -> throw ApiException.validation("decision", ValidationCodes.INVALID);
        };
        company.setStatus(next);
        company.setSanctionsStatus(sanctionsStatus);
        company.setSanctionsCheckedAt(clock.instant());
        company.setReviewNote(Texts.trimToNull(note));
        companyMapper.updateById(company);
        auditService.log(Actor.admin(adminId), AuditAction.COMPANY_REVIEWED, "client_company", companyId,
                Map.of("decision", decision.name(), "sanctionsStatus", sanctionsStatus.name(), "status", next.name()));
        return company;
    }

    public Map<String, Long> countByStatus() {
        return StatusCounts.count(companyMapper, CompanyStatus.class);
    }

    private ClientMember findMember(long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ClientMember>().eq(ClientMember::getUserId, userId));
    }

    private ClientCompany lockOwn(long userId) {
        return companyMapper.selectByIdForUpdate(getOrCreateForUser(userId).getId());
    }

    private Map<Long, List<AdminCompanyView.Member>> members(List<Long> companyIds) {
        if (companyIds.isEmpty()) {
            return Map.of();
        }
        List<ClientMember> rows = memberMapper.selectList(new LambdaQueryWrapper<ClientMember>()
                .in(ClientMember::getCompanyId, companyIds));
        Map<Long, String> emails = userService.emails(rows.stream().map(ClientMember::getUserId).toList());
        return rows.stream().collect(Collectors.groupingBy(ClientMember::getCompanyId,
                Collectors.mapping(m -> new AdminCompanyView.Member(m.getUserId(), emails.get(m.getUserId())),
                        Collectors.toList())));
    }
}
