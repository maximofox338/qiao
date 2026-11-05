package com.qiao.account.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the last plain OTP per email for end-to-end tests. Exists only in the {@code dev} and {@code test} profiles,
 * and never when {@code prod} is also active (e.g. {@code SPRING_PROFILES_ACTIVE=prod,dev} by mistake).
 */
@Component
@Profile(DevOtpRegistry.PROFILES)
public class DevOtpRegistry {

    public static final String PROFILES = "(dev | test) & !prod";

    private final Map<String, String> lastCodes = new ConcurrentHashMap<>();

    void remember(String email, String code) {
        lastCodes.put(email, code);
    }

    public Optional<String> lastCode(String email) {
        return Optional.ofNullable(lastCodes.get(email));
    }
}
