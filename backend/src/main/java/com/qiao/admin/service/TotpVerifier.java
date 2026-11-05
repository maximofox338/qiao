package com.qiao.admin.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * RFC 6238 TOTP (SHA-1, 6 digits, 30-second steps) accepting one step of clock drift. {@link #matchStep} returns the
 * time-step a code belongs to, so callers can refuse a step that was already used (replay protection).
 */
@Component
public class TotpVerifier {

    public static final long STEP_SECONDS = 30;
    public static final int DIGITS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TotpVerifier(Clock clock) {
        this.clock = clock;
    }

    public boolean verify(String base32Secret, String code) {
        return matchStep(base32Secret, code).isPresent();
    }

    /**
     * @return the time-step (current, previous or next) whose code equals {@code code}
     */
    public OptionalLong matchStep(String base32Secret, String code) {
        if (base32Secret == null || code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = decodeBase32(base32Secret);
        long counter = currentStep();
        long matched = -1;
        for (long offset = -1; offset <= 1; offset++) {
            // compare every candidate (constant work regardless of which one matches)
            if (MessageDigest.isEqual(code(key, counter + offset).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                matched = counter + offset;
            }
        }
        return matched < 0 ? OptionalLong.empty() : OptionalLong.of(matched);
    }

    public long currentStep() {
        return clock.instant().getEpochSecond() / STEP_SECONDS;
    }

    /**
     * New random secret (160 bits, base32 without padding), as recommended by RFC 4226.
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /**
     * Key URI understood by authenticator apps ({@code otpauth://totp/Issuer:account?secret=…&issuer=…}).
     */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * The code of a given time-step (used by tests and by authenticator-compatible tooling).
     */
    public static String codeAt(String base32Secret, long step) {
        return code(decodeBase32(base32Secret), step);
    }

    static String code(byte[] key, long counter) {
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

    static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
