package com.qiao.common.util;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Markdown text with an optional {@code ---}-delimited header of {@code key: value} lines.
 */
public record FrontMatterDocument(Map<String, String> attributes, String body) {

    private static final String DELIMITER = "---";

    public static Optional<FrontMatterDocument> loadClasspath(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FrontMatterDocument parse(String raw) {
        String text = raw.replace("\r\n", "\n");
        if (!text.startsWith(DELIMITER + "\n")) {
            return new FrontMatterDocument(Map.of(), text);
        }
        int end = text.indexOf("\n" + DELIMITER, DELIMITER.length());
        if (end < 0) {
            return new FrontMatterDocument(Map.of(), text);
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String line : text.substring(DELIMITER.length() + 1, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                attributes.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        String body = text.substring(end + DELIMITER.length() + 1).stripLeading();
        return new FrontMatterDocument(Map.copyOf(attributes), body);
    }

    public String attribute(String name) {
        String value = attributes.get(name);
        if (value == null) {
            throw new IllegalStateException("Missing front matter attribute: " + name);
        }
        return value;
    }
}
