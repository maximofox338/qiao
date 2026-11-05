package com.qiao.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.account.domain.UserRole;
import com.qiao.account.service.OnboardingStatusProvider;
import com.qiao.account.service.UserRegisteredEvent;
import com.qiao.account.service.UserService;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.error.ValidationCodes;
import com.qiao.common.persistence.StatusCounts;
import com.qiao.common.review.ReviewDecision;
import com.qiao.common.util.Texts;
import com.qiao.compliance.domain.ConsentType;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.compliance.service.ConsentService;
import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.domain.EngineerProfileMapper;
import com.qiao.profile.domain.ProfileStatus;
import com.qiao.profile.web.AdminEngineerView;
import com.qiao.profile.web.ProfileRequest;
import com.qiao.profile.web.ProfileView;
import com.qiao.profile.web.PseudonymizedProfile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Engineer profiles and their review state machine: DRAFT → PENDING_REVIEW → APPROVED | REJECTED
 * (editing a REJECTED profile moves it back to DRAFT).
 */
@Service
public class EngineerProfileService implements OnboardingStatusProvider {

    private static final int ALIAS_ATTEMPTS = 20;

    private final EngineerProfileMapper profileMapper;
    private final AliasGenerator aliasGenerator;
    private final ConsentService consentService;
    private final AuditService auditService;
    private final UserService userService;
    private final Clock clock;

    public EngineerProfileService(EngineerProfileMapper profileMapper, AliasGenerator aliasGenerator,
                                  ConsentService consentService, AuditService auditService, UserService userService,
                                  Clock clock) {
        this.profileMapper = profileMapper;
        this.aliasGenerator = aliasGenerator;
        this.consentService = consentService;
        this.auditService = auditService;
        this.userService = userService;
        this.clock = clock;
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event.role() == UserRole.ENGINEER) {
            getOrCreate(event.userId());
        }
    }

    @Override
    public UserRole role() {
        return UserRole.ENGINEER;
    }

    @Override
    public String onboardingStatus(long userId) {
        return findByUserId(userId).map(p -> p.getStatus().name()).orElse(ProfileStatus.DRAFT.name());
    }

    public EngineerProfile getOrCreate(long userId) {
        return findByUserId(userId).orElseGet(() -> {
            EngineerProfile profile = new EngineerProfile();
            profile.setUserId(userId);
            profile.setAlias(uniqueAlias());
            profile.setDeclarationNoRestrictions(false);
            profile.setStatus(ProfileStatus.DRAFT);
            profile.setRatingCount(0);
            profileMapper.insert(profile);
            return profile;
        });
    }

    public Optional<EngineerProfile> findByUserId(long userId) {
        return Optional.ofNullable(profileMapper.selectOne(new LambdaQueryWrapper<EngineerProfile>()
                .eq(EngineerProfile::getUserId, userId)));
    }

    @Transactional
    public ProfileView getOwn(long userId) {
        return ProfileView.from(getOrCreate(userId));
    }

    @Transactional
    public ProfileView update(long userId, ProfileRequest request) {
        EngineerProfile profile = lockOwn(userId);
        if (profile.getStatus() == ProfileStatus.PENDING_REVIEW) {
            throw ApiException.invalidState("Profile is under review");
        }
        if (profile.getStatus() == ProfileStatus.REJECTED) {
            profile.setStatus(ProfileStatus.DRAFT);
        }
        profile.setHeadline(Texts.trimToNull(request.headline()));
        profile.setBio(Texts.trimToNull(request.bio()));
        profile.setSkills(request.skills() == null ? List.of() : request.skills().stream().map(String::trim).distinct().toList());
        profile.setYearsExperience(request.yearsExperience());
        profile.setRegion(Texts.trimToNull(request.region()));
        profile.setEnglishLevel(request.englishLevel());
        profile.setSpanishLevel(request.spanishLevel());
        profile.setHourlyRateUsd(request.hourlyRateUsd());
        profile.setAvailabilityHoursWeek(request.availabilityHoursWeek());
        profile.setCvText(Texts.trimToNull(request.cvText()));
        profile.setEducation(Texts.trimToNull(request.education()));
        profile.setPortfolioLinks(request.portfolioLinks() == null ? List.of() : request.portfolioLinks());
        boolean declared = Boolean.TRUE.equals(request.declarationNoRestrictions());
        if (declared && !Boolean.TRUE.equals(profile.getDeclarationNoRestrictions())) {
            profile.setDeclarationAt(clock.instant());
        } else if (!declared) {
            profile.setDeclarationAt(null);
        }
        profile.setDeclarationNoRestrictions(declared);
        profileMapper.updateById(profile);
        return ProfileView.from(profile);
    }

    @Transactional
    public ProfileView submit(long userId) {
        EngineerProfile profile = lockOwn(userId);
        if (profile.getStatus() != ProfileStatus.DRAFT && profile.getStatus() != ProfileStatus.REJECTED) {
            throw ApiException.invalidState("Profile cannot be submitted from " + profile.getStatus());
        }
        Map<String, String> missing = new LinkedHashMap<>();
        if (profile.getHeadline() == null) {
            missing.put("headline", ValidationCodes.REQUIRED);
        }
        if (profile.getSkills() == null || profile.getSkills().isEmpty()) {
            missing.put("skills", ValidationCodes.REQUIRED);
        }
        if (profile.getYearsExperience() == null) {
            missing.put("yearsExperience", ValidationCodes.REQUIRED);
        }
        if (profile.getRegion() == null) {
            missing.put("region", ValidationCodes.REQUIRED);
        }
        if (profile.getEnglishLevel() == null) {
            missing.put("englishLevel", ValidationCodes.REQUIRED);
        }
        if (!missing.isEmpty()) {
            throw ApiException.validation(missing);
        }
        if (!Boolean.TRUE.equals(profile.getDeclarationNoRestrictions())) {
            throw new ApiException(ErrorCode.DECLARATIONS_REQUIRED);
        }
        if (!consentService.isGranted(userId, ConsentType.CROSS_BORDER_PROCESSING, null)) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED, "Cross-border processing consent required",
                    Map.of("consentType", ConsentType.CROSS_BORDER_PROCESSING.name()));
        }
        profile.setStatus(ProfileStatus.PENDING_REVIEW);
        profile.setReviewNote(null);
        profileMapper.updateById(profile);
        auditService.log(Actor.user(userId), AuditAction.ENGINEER_PROFILE_SUBMITTED, "engineer_profile", profile.getId());
        return ProfileView.from(profile);
    }

    /**
     * @throws ApiException {@code PROFILE_NOT_APPROVED}
     */
    public EngineerProfile requireApproved(long userId) {
        return findByUserId(userId)
                .filter(p -> p.getStatus() == ProfileStatus.APPROVED)
                .orElseThrow(() -> new ApiException(ErrorCode.PROFILE_NOT_APPROVED));
    }

    public EngineerProfile getByUserId(long userId) {
        return findByUserId(userId).orElseThrow(() -> ApiException.notFound("Engineer profile"));
    }

    public Map<Long, EngineerProfile> byUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return profileMapper.selectList(new LambdaQueryWrapper<EngineerProfile>().in(EngineerProfile::getUserId, userIds))
                .stream().collect(Collectors.toMap(EngineerProfile::getUserId, Function.identity()));
    }

    public Map<Long, PseudonymizedProfile> pseudonymized(Collection<Long> userIds) {
        return byUserIds(userIds).values().stream()
                .collect(Collectors.toMap(EngineerProfile::getUserId, PseudonymizedProfile::from));
    }

    /**
     * The APPROVED profile with this alias (case-insensitive), if any. Used to invite engineers to teams.
     */
    public Optional<EngineerProfile> findApprovedByAlias(String alias) {
        String normalized = Texts.trimToNull(alias);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(profileMapper.selectOne(new LambdaQueryWrapper<EngineerProfile>()
                        .eq(EngineerProfile::getAlias, normalized.toUpperCase(java.util.Locale.ROOT))))
                .filter(p -> p.getStatus() == ProfileStatus.APPROVED);
    }

    public Map<Long, String> aliases(Collection<Long> userIds) {
        return byUserIds(userIds).values().stream()
                .collect(Collectors.toMap(EngineerProfile::getUserId, EngineerProfile::getAlias));
    }

    public PageResult<AdminEngineerView> adminList(ProfileStatus status, PageQuery pageQuery) {
        var page = profileMapper.selectPage(pageQuery.toPage(), new LambdaQueryWrapper<EngineerProfile>()
                .eq(status != null, EngineerProfile::getStatus, status)
                .orderByDesc(EngineerProfile::getUpdatedAt));
        Map<Long, String> emails = userService.emails(page.getRecords().stream().map(EngineerProfile::getUserId).toList());
        return PageResult.of(page, p -> AdminEngineerView.from(p, emails.get(p.getUserId())));
    }

    public AdminEngineerView adminGet(long profileId) {
        EngineerProfile profile = profileMapper.selectById(profileId);
        if (profile == null) {
            throw ApiException.notFound("Engineer profile");
        }
        return AdminEngineerView.from(profile, userService.get(profile.getUserId()).getEmail());
    }

    @Transactional
    public EngineerProfile review(long profileId, ReviewDecision decision, String note, String partnerRef, long adminId) {
        EngineerProfile profile = profileMapper.selectByIdForUpdate(profileId);
        if (profile == null) {
            throw ApiException.notFound("Engineer profile");
        }
        if (profile.getStatus() != ProfileStatus.PENDING_REVIEW) {
            throw ApiException.invalidState("Profile is not pending review");
        }
        String ref = Texts.trimToNull(partnerRef);
        switch (decision) {
            case APPROVE -> {
                if (ref == null) {
                    throw new ApiException(ErrorCode.PARTNER_REF_REQUIRED);
                }
                profile.setStatus(ProfileStatus.APPROVED);
                profile.setPartnerRef(ref);
            }
            case REJECT -> profile.setStatus(ProfileStatus.REJECTED);
            case REQUEST_CHANGES -> throw ApiException.validation("decision", ValidationCodes.INVALID);
        }
        profile.setReviewNote(Texts.trimToNull(note));
        profileMapper.updateById(profile);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("decision", decision.name());
        if (ref != null) {
            detail.put("partnerRef", ref);
        }
        auditService.log(Actor.admin(adminId), AuditAction.ENGINEER_REVIEWED, "engineer_profile", profileId, detail);
        return profile;
    }

    public Map<String, Long> countByStatus() {
        return StatusCounts.count(profileMapper, ProfileStatus.class);
    }

    /**
     * Adds a client review to the engineer's rating (running average, two decimals) under a row lock.
     */
    public void addRating(long userId, int stars) {
        EngineerProfile profile = findByUserId(userId).orElse(null);
        if (profile == null) {
            return;
        }
        EngineerProfile locked = profileMapper.selectByIdForUpdate(profile.getId());
        int count = locked.getRatingCount() == null ? 0 : locked.getRatingCount();
        BigDecimal total = (locked.getRatingAvg() == null ? BigDecimal.ZERO : locked.getRatingAvg())
                .multiply(BigDecimal.valueOf(count)).add(BigDecimal.valueOf(stars));
        locked.setRatingCount(count + 1);
        locked.setRatingAvg(total.divide(BigDecimal.valueOf(count + 1L), 2, RoundingMode.HALF_UP));
        profileMapper.updateById(locked);
    }

    /**
     * Wipes free-text personal data; alias and partner reference are kept for settlement history.
     */
    void anonymize(long userId) {
        findByUserId(userId).ifPresent(profile -> {
            profile.setHeadline(null);
            profile.setBio(null);
            profile.setSkills(List.of());
            profile.setYearsExperience(null);
            profile.setRegion(null);
            profile.setHourlyRateUsd(null);
            profile.setAvailabilityHoursWeek(null);
            profile.setCvText(null);
            profile.setEducation(null);
            profile.setPortfolioLinks(List.of());
            profile.setStatus(ProfileStatus.DRAFT);
            profileMapper.updateById(profile);
        });
    }

    private EngineerProfile lockOwn(long userId) {
        EngineerProfile profile = getOrCreate(userId);
        return profileMapper.selectByIdForUpdate(profile.getId());
    }

    private String uniqueAlias() {
        for (int i = 0; i < ALIAS_ATTEMPTS; i++) {
            String alias = aliasGenerator.next();
            if (!profileMapper.exists(new LambdaQueryWrapper<EngineerProfile>().eq(EngineerProfile::getAlias, alias))) {
                return alias;
            }
        }
        throw new IllegalStateException("Could not generate a unique alias");
    }
}
