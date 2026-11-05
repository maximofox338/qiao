package com.qiao.profile.web;

import com.qiao.profile.domain.EngineerProfile;

import java.util.List;

public record CvView(String cvText, String education, List<String> portfolioLinks) {

    public static CvView from(EngineerProfile p) {
        return new CvView(p.getCvText(), p.getEducation(), ProfileView.listOrEmpty(p.getPortfolioLinks()));
    }
}
