package com.qiao.review.service;

import com.qiao.account.service.PersonalDataHandler;
import org.springframework.stereotype.Component;

@Component
public class ReviewPersonalDataHandler implements PersonalDataHandler {

    private final ReviewService reviewService;

    public ReviewPersonalDataHandler(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Override
    public String section() {
        return "reviews";
    }

    @Override
    public Object export(long userId) {
        return reviewService.writtenBy(userId);
    }

    @Override
    public void anonymize(long userId) {
        reviewService.clearComments(userId);
    }
}
