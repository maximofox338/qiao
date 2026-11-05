package com.qiao.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.account.domain.UserRole;
import com.qiao.client.domain.ClientCompany;
import com.qiao.client.service.CompanyService;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.util.Texts;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.contract.domain.Contract;
import com.qiao.contract.domain.ContractStatus;
import com.qiao.contract.service.ContractParticipant;
import com.qiao.contract.service.ContractService;
import com.qiao.contract.service.ReviewSummary;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.project.service.ProjectService;
import com.qiao.review.domain.Review;
import com.qiao.review.domain.ReviewMapper;
import com.qiao.review.web.AdminReviewView;
import com.qiao.review.web.ReviewRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reviews after a contract is COMPLETED: one per side. The client's stars go to the rating of the engineer (every
 * member of a team); review texts are visible to the back-office only for now.
 */
@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final ContractService contractService;
    private final ProjectService projectService;
    private final CompanyService companyService;
    private final EngineerProfileService profileService;
    private final AuditService auditService;
    private final Clock clock;

    public ReviewService(ReviewMapper reviewMapper, ContractService contractService, ProjectService projectService,
                         CompanyService companyService, EngineerProfileService profileService,
                         AuditService auditService, Clock clock) {
        this.reviewMapper = reviewMapper;
        this.contractService = contractService;
        this.projectService = projectService;
        this.companyService = companyService;
        this.profileService = profileService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * The client reviews the engineer, or the whole team: every member's rating gets the stars.
     */
    @Transactional
    public ReviewSummary reviewAsClient(long userId, long contractId, ReviewRequest request) {
        Contract contract = lockReviewable(userId, contractId, UserRole.CLIENT);
        List<Long> engineers = contractService.engineerUserIds(contract);
        Review review = insert(contract, userId, contract.getEngineerUserId(), UserRole.CLIENT, request);
        engineers.forEach(engineerUserId -> profileService.addRating(engineerUserId, request.stars()));
        return summary(review);
    }

    /**
     * The engineer (the lead, for a team) reviews the client company.
     */
    @Transactional
    public ReviewSummary reviewAsEngineer(long userId, long contractId, ReviewRequest request) {
        Contract contract = lockReviewable(userId, contractId, UserRole.ENGINEER);
        if (contract.getEngineerUserId() != userId) {
            throw new ApiException(ErrorCode.TEAM_LEAD_ONLY);
        }
        List<Long> members = companyService.memberUserIds(contract.getCompanyId());
        long clientUserId = members.isEmpty() ? 0L : members.getFirst();
        return summary(insert(contract, userId, clientUserId, UserRole.ENGINEER, request));
    }

    public PageResult<AdminReviewView> adminList(UserRole fromSide, PageQuery pageQuery) {
        var page = reviewMapper.selectPage(pageQuery.toPage(), new LambdaQueryWrapper<Review>()
                .eq(fromSide != null, Review::getFromSide, fromSide)
                .orderByDesc(Review::getId));
        List<Review> reviews = page.getRecords();
        Map<Long, Contract> contracts = contractService.byIds(reviews.stream().map(Review::getContractId).collect(Collectors.toSet()));
        Map<Long, String> titles = projectService.titles(contracts.values().stream().map(Contract::getProjectId).collect(Collectors.toSet()));
        Map<Long, ClientCompany> companies = companyService.byIds(contracts.values().stream().map(Contract::getCompanyId).collect(Collectors.toSet()));
        Set<Long> engineerIds = new HashSet<>();
        contracts.values().forEach(c -> engineerIds.addAll(contractService.engineerUserIds(c)));
        Map<Long, String> aliases = profileService.aliases(engineerIds);
        return PageResult.of(page, r -> {
            Contract c = contracts.get(r.getContractId());
            String engineerLabel = c == null ? null : c.getTeamName() != null
                    ? contractService.engineerUserIds(c).stream().map(aliases::get).collect(Collectors.joining(", "))
                    + " (" + c.getTeamName() + ")"
                    : aliases.get(c.getEngineerUserId());
            ClientCompany company = c == null ? null : companies.get(c.getCompanyId());
            String companyLabel = company == null ? null : company.getName();
            boolean fromClient = r.getFromSide() == UserRole.CLIENT;
            return new AdminReviewView(r.getId(), r.getContractId(), c == null ? null : c.getProjectId(),
                    c == null ? null : titles.get(c.getProjectId()), r.getFromSide(),
                    fromClient ? companyLabel : engineerLabel, fromClient ? engineerLabel : companyLabel,
                    r.getStars(), r.getComment(), r.getCreatedAt());
        });
    }

    // ------------------------------------------------------------------ personal data

    List<ReviewSummary> writtenBy(long userId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>().eq(Review::getFromUserId, userId)
                .orderByAsc(Review::getId)).stream().map(ReviewService::summary).toList();
    }

    /**
     * Comments are free text written by the user: removed on deletion; the stars stay (they feed ratings).
     */
    void clearComments(long userId) {
        reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .set(Review::getComment, null)
                .set(Review::getUpdatedAt, clock.instant())
                .eq(Review::getFromUserId, userId));
    }

    // ------------------------------------------------------------------ internals

    private Contract lockReviewable(long userId, long contractId, UserRole side) {
        ContractParticipant participant = contractService.requireParticipant(userId, contractId);
        if (participant.side() != side) {
            throw ApiException.notFound("Contract");
        }
        Contract contract = contractService.lock(contractId);
        if (contract.getStatus() != ContractStatus.COMPLETED) {
            throw new ApiException(ErrorCode.CONTRACT_NOT_COMPLETED);
        }
        if (reviewMapper.exists(new LambdaQueryWrapper<Review>()
                .eq(Review::getContractId, contractId)
                .eq(Review::getFromSide, side))) {
            throw new ApiException(ErrorCode.ALREADY_REVIEWED);
        }
        return contract;
    }

    private Review insert(Contract contract, long fromUserId, long toUserId, UserRole side, ReviewRequest request) {
        Review review = new Review();
        review.setContractId(contract.getId());
        review.setFromUserId(fromUserId);
        review.setToUserId(toUserId);
        review.setFromSide(side);
        review.setStars(request.stars());
        review.setComment(Texts.trimToNull(request.comment()));
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_REVIEWED);
        }
        auditService.log(Actor.user(fromUserId), AuditAction.REVIEW_CREATED, "review", review.getId(),
                Map.of("contractId", contract.getId(), "side", side.name(), "stars", request.stars()));
        return review;
    }

    private static ReviewSummary summary(Review r) {
        return new ReviewSummary(r.getStars(), r.getComment(), r.getCreatedAt());
    }
}
