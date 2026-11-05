package com.qiao.profile.service;

import com.qiao.account.service.PersonalDataHandler;
import com.qiao.profile.web.ProfileView;
import org.springframework.stereotype.Component;

@Component
public class ProfilePersonalDataHandler implements PersonalDataHandler {

    private final EngineerProfileService profileService;

    public ProfilePersonalDataHandler(EngineerProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public String section() {
        return "engineerProfile";
    }

    @Override
    public Object export(long userId) {
        return profileService.findByUserId(userId).map(ProfileView::from).orElse(null);
    }

    @Override
    public void anonymize(long userId) {
        profileService.anonymize(userId);
    }
}
