package com.qiao.compliance.service;

import com.qiao.common.error.ApiException;
import com.qiao.common.util.FrontMatterDocument;
import com.qiao.common.util.Locales;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legal texts from {@code resources/legal/{doc}.{locale}.md}; each file declares {@code version} and {@code title}.
 * English is the fallback and the reference for the current version.
 */
@Service
public class LegalService {

    public static final String TERMS = "terms";
    public static final String PRIVACY = "privacy";
    public static final String CLIENT_DECLARATIONS = "client-declarations";
    public static final String ENGINEER_DECLARATION = "engineer-declaration";
    public static final String CROSS_BORDER = "cross-border";
    public static final List<String> DOCUMENTS = List.of(TERMS, PRIVACY, CLIENT_DECLARATIONS, ENGINEER_DECLARATION, CROSS_BORDER);

    private final Map<String, LegalDocument> documents = new HashMap<>();

    public LegalService() {
        for (String doc : DOCUMENTS) {
            for (String locale : Locales.SUPPORTED) {
                FrontMatterDocument.loadClasspath("legal/" + doc + "." + locale + ".md").ifPresent(file ->
                        documents.put(key(doc, locale), new LegalDocument(doc, file.attribute("version"), locale,
                                file.attribute("title"), file.body())));
            }
            if (!documents.containsKey(key(doc, Locales.EN))) {
                throw new IllegalStateException("Missing English legal text: " + doc);
            }
        }
    }

    public LegalDocument get(String doc, String locale) {
        if (!DOCUMENTS.contains(doc)) {
            throw ApiException.notFound("Legal document");
        }
        LegalDocument localized = documents.get(key(doc, Locales.normalize(locale, Locales.EN)));
        return localized != null ? localized : documents.get(key(doc, Locales.EN));
    }

    public String currentVersion(String doc) {
        return get(doc, Locales.EN).version();
    }

    private static String key(String doc, String locale) {
        return doc + "." + locale;
    }
}
