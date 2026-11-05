package com.qiao.profile.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates public engineer aliases such as {@code QE-7K2P} (no ambiguous characters).
 */
@Component
public class AliasGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int LENGTH = 4;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder alias = new StringBuilder("QE-");
        for (int i = 0; i < LENGTH; i++) {
            alias.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return alias.toString();
    }
}
