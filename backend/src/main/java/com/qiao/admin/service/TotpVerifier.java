package com.qiao.admin.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Locale;

/**
 * RFC 6238 TOTP (SHA-1, 6 digits, 30-second steps) accepting one step of clock drift.
 */
@Component
public class TotpVerifier {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    private final Clock clock;

    public TotpVerifier(Clock clock) {
        this.clock = clock;
    }

    public boolean verify(String base32Secret, String code) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        byte[] key = decodeBase32(base32Secret);
        long counter = clock.instant().getEpochSecond() / STEP_SECONDS;
        boolean match = false;
        for (long offset = -1; offset <= 1; offset++) {
            match |= MessageDigest.isEqual(code(key, counter + offset).getBytes(), code.getBytes());
        }
        return match;
    }

    String code(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % (int) Math.pow(10, DIGITS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] decodeBase32(String secret) {
        String clean = secret.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base32 character");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
                buffer &= (1 << bits) - 1;
            }
        }
        return out.array();
    }
}
