package com.qiao.common.util;

import java.util.List;
import java.util.Locale;

/**
 * Supported UI/content locales: {@code en}, {@code es}, {@code zh-CN}.
 */
public final class Locales {

    public static final String EN = "en";
    public static final String ES = "es";
    public static final String ZH_CN = "zh-CN";
    public static final List<String> SUPPORTED = List.of(EN, ES, ZH_CN);

    private Locales() {
    }

    /**
     * Maps loose input ({@code zh}, {@code zh_CN}, {@code es-MX}) to a supported locale, or {@code fallback}.
     */
    public static String normalize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (value.startsWith("zh")) {
            return ZH_CN;
        }
        if (value.startsWith("es")) {
            return ES;
        }
        if (value.startsWith("en")) {
            return EN;
        }
        return fallback;
    }

    public static boolean isSupported(String locale) {
        return SUPPORTED.contains(locale);
    }
}
