package com.qiao.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerKeyFilterTest {

    private static final String CURRENT = "current-partner-key-0123456789abcdef";
    private static final String PREVIOUS = "previous-partner-key-0123456789abcdef";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void theCurrentKeyAuthenticatesWithoutWarning() throws Exception {
        MockHttpServletRequest request = call(new PartnerKeyFilter(new PartnerApiProperties(CURRENT, PREVIOUS)), CURRENT);
        assertThat(partnerAuthenticated()).isTrue();
        assertThat(request.getAttribute(PartnerKeyFilter.PREVIOUS_KEY_ATTRIBUTE)).isNull();
        assertThat(PartnerKeyFilter.previousKeyUsed()).isFalse();
    }

    @Test
    void thePreviousKeyAuthenticatesAndIsMarked() throws Exception {
        call(new PartnerKeyFilter(new PartnerApiProperties(CURRENT, PREVIOUS)), PREVIOUS);
        assertThat(partnerAuthenticated()).isTrue();
        assertThat(PartnerKeyFilter.previousKeyUsed()).isTrue();
    }

    @Test
    void otherKeysAndAPreviousKeyWithoutCurrentKeyDoNotAuthenticate() throws Exception {
        call(new PartnerKeyFilter(new PartnerApiProperties(CURRENT, PREVIOUS)), "some-other-key-0123456789abcdef");
        assertThat(partnerAuthenticated()).isFalse();
        call(new PartnerKeyFilter(new PartnerApiProperties(CURRENT, "")), PREVIOUS);
        assertThat(partnerAuthenticated()).isFalse();
        // the previous key never enables a partner API that is switched off (blank current key)
        call(new PartnerKeyFilter(new PartnerApiProperties(" ", PREVIOUS)), PREVIOUS);
        assertThat(partnerAuthenticated()).isFalse();
        call(new PartnerKeyFilter(new PartnerApiProperties(CURRENT, PREVIOUS)), null);
        assertThat(partnerAuthenticated()).isFalse();
    }

    private static MockHttpServletRequest call(PartnerKeyFilter filter, String key) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/partner/settlements");
        if (key != null) {
            request.addHeader(PartnerKeyFilter.HEADER, key);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return request;
    }

    private static boolean partnerAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p && p.kind() == AuthPrincipal.Kind.PARTNER;
    }
}
