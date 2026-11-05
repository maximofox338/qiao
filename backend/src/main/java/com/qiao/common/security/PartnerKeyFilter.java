package com.qiao.common.security;

import com.qiao.common.util.Hashing;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates the payroll partner on {@code /api/partner/**} through the {@code X-Partner-Key} header. The header is
 * compared in constant time with {@code PARTNER_API_KEY} and, during a rotation, with {@code PARTNER_API_KEY_PREVIOUS}
 * (both comparisons always run). A request authenticated with the previous key is marked so that its audit entries
 * carry a warning ({@link #previousKeyUsed()}), and it is logged at WARN.
 */
public class PartnerKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Partner-Key";
    static final String PREVIOUS_KEY_ATTRIBUTE = PartnerKeyFilter.class.getName() + ".previousKey";

    private static final Logger log = LoggerFactory.getLogger(PartnerKeyFilter.class);

    private final String apiKey;
    private final String previousKey;

    public PartnerKeyFilter(PartnerApiProperties properties) {
        this.apiKey = blankToNull(properties.apiKey());
        // the previous key only extends a configured current key; it never enables the API on its own
        this.previousKey = apiKey == null ? null : blankToNull(properties.apiKeyPrevious());
    }

    /**
     * Whether the current request was authenticated with {@code PARTNER_API_KEY_PREVIOUS}.
     */
    public static boolean previousKeyUsed() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes != null
                && Boolean.TRUE.equals(attributes.getAttribute(PREVIOUS_KEY_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/partner/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        boolean current = apiKey != null && Hashing.constantTimeEquals(apiKey, presented);
        boolean previous = previousKey != null && Hashing.constantTimeEquals(previousKey, presented);
        if (current || previous) {
            if (!current) {
                request.setAttribute(PREVIOUS_KEY_ATTRIBUTE, Boolean.TRUE);
                log.warn("Partner API call {} {} authenticated with PARTNER_API_KEY_PREVIOUS: the partner still uses "
                        + "the old key; finish the rotation only after it switched", request.getMethod(),
                        request.getRequestURI());
            }
            AuthPrincipal partner = AuthPrincipal.partner();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(partner, null, partner.authorities()));
        }
        chain.doFilter(request, response);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
