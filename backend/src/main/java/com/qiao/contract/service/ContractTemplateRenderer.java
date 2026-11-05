package com.qiao.contract.service;

import com.qiao.common.util.FrontMatterDocument;
import com.qiao.common.util.Hashing;
import com.qiao.common.util.Locales;
import com.qiao.contract.domain.DocumentKind;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders {@code resources/contracts/{KIND}.{locale}.md} (en, zh-CN) by filling {@code {{placeholder}}} values.
 * Fails if a placeholder has no value, so an incomplete contract is never stored.
 */
@Component
public class ContractTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9]+)\\s*}}");

    public record RenderedDocument(String templateVersion, String locale, String body, String sha256) {
    }

    /**
     * Templates exist in English and Simplified Chinese; every other locale uses English.
     */
    public static String templateLocale(String locale) {
        return Locales.ZH_CN.equals(locale) ? Locales.ZH_CN : Locales.EN;
    }

    public RenderedDocument render(DocumentKind kind, String locale, Map<String, String> values) {
        String templateLocale = templateLocale(locale);
        FrontMatterDocument template = FrontMatterDocument
                .loadClasspath("contracts/" + kind.name() + "." + templateLocale + ".md")
                .orElseThrow(() -> new IllegalStateException("Missing contract template " + kind + "/" + templateLocale));
        Matcher matcher = PLACEHOLDER.matcher(template.body());
        StringBuilder body = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("No value for contract placeholder " + matcher.group(1));
            }
            matcher.appendReplacement(body, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(body);
        String rendered = body.toString();
        return new RenderedDocument(template.attribute("version"), templateLocale, rendered, Hashing.sha256Hex(rendered));
    }
}
