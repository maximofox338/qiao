package com.qiao.delivery.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Determines a file's content type from its first bytes; the client-declared type is never trusted.
 * Executables are rejected; unknown binaries are stored as {@code application/octet-stream}.
 */
final class ContentTypeSniffer {

    static final int HEAD_SIZE = 512;
    private static final String OCTET_STREAM = "application/octet-stream";

    private ContentTypeSniffer() {
    }

    /**
     * @return the content type, or empty when the file type is not allowed
     */
    static Optional<String> sniff(byte[] head, int length, String filename) {
        if (isExecutable(head, length)) {
            return Optional.empty();
        }
        if (startsWith(head, length, "%PDF-")) {
            return Optional.of("application/pdf");
        }
        if (startsWith(head, length, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(head, length, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(head, length, "GIF87a") || startsWith(head, length, "GIF89a")) {
            return Optional.of("image/gif");
        }
        if (startsWith(head, length, "RIFF") && length >= 12
                && new String(head, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return Optional.of("image/webp");
        }
        if (startsWith(head, length, 'P', 'K', 0x03, 0x04)) {
            return Optional.of(zipType(filename));
        }
        if (startsWith(head, length, 0x1F, 0x8B)) {
            return Optional.of("application/gzip");
        }
        if (startsWith(head, length, '7', 'z', 0xBC, 0xAF, 0x27, 0x1C)) {
            return Optional.of("application/x-7z-compressed");
        }
        return Optional.of(isText(head, length) ? "text/plain" : OCTET_STREAM);
    }

    private static boolean isExecutable(byte[] head, int length) {
        return startsWith(head, length, 'M', 'Z')
                || startsWith(head, length, 0x7F, 'E', 'L', 'F')
                || startsWith(head, length, 0xCA, 0xFE, 0xBA, 0xBE)
                || startsWith(head, length, 0xFE, 0xED, 0xFA, 0xCE)
                || startsWith(head, length, 0xFE, 0xED, 0xFA, 0xCF)
                || startsWith(head, length, 0xCF, 0xFA, 0xED, 0xFE)
                || startsWith(head, length, "#!");
    }

    private static String zipType(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (name.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (name.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        return "application/zip";
    }

    private static boolean isText(byte[] head, int length) {
        for (int i = 0; i < length; i++) {
            if (head[i] == 0) {
                return false;
            }
        }
        // a multi-byte character may be cut at the end of the sample
        int checked = Math.max(0, length - 3);
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(head, 0, length == HEAD_SIZE ? checked : length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean startsWith(byte[] head, int length, String prefix) {
        return startsWith(head, length, prefix.chars().toArray());
    }

    private static boolean startsWith(byte[] head, int length, int... prefix) {
        if (length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((head[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
