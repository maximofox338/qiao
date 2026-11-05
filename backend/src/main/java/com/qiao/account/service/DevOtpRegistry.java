package com.qiao.account.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the last plain OTP per email for end-to-end tests. Exists only in the {@code dev} and {@code test} profiles.
 */
@Component
@Profile({"dev", "test"})
public class DevOtpRegistry {

    private final Map<String, String> lastCodes = new ConcurrentHashMap<>();

    void remember(String email, String code) {
        lastCodes.put(email, code);
    }

    public Optional<String> lastCode(String email) {
        return Optional.ofNullable(lastCodes.get(email));
    }
}
