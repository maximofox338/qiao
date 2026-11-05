package com.qiao.profile.web;

import com.qiao.profile.domain.EngineerProfile;

import java.util.List;

/**
 * What engineers of the same team see about each other: alias, headline and skills. Never email, CV, education,
 * portfolio links or partner reference.
 */
public record TeammateProfile(String alias, String headline, List<String> skills) {

    public static TeammateProfile from(EngineerProfile p) {
        return new TeammateProfile(p.getAlias(), p.getHeadline(), ProfileView.listOrEmpty(p.getSkills()));
    }
}
