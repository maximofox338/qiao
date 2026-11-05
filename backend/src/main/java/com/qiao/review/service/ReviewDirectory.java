package com.qiao.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.account.domain.UserRole;
import com.qiao.contract.service.ContractReviews;
import com.qiao.contract.service.ReviewSummary;
import com.qiao.review.domain.Review;
import com.qiao.review.domain.ReviewMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Feeds the contract views with each side's own review (mapper only, no bean cycle with ContractService).
 */
@Component
public class ReviewDirectory implements ContractReviews {

    private final ReviewMapper reviewMapper;

    public ReviewDirectory(ReviewMapper reviewMapper) {
        this.reviewMapper = reviewMapper;
    }

    @Override
    public Optional<ReviewSummary> byContractAndSide(long contractId, UserRole fromSide) {
        return Optional.ofNullable(reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                        .eq(Review::getContractId, contractId)
                        .eq(Review::getFromSide, fromSide)))
                .map(r -> new ReviewSummary(r.getStars(), r.getComment(), r.getCreatedAt()));
    }
}
