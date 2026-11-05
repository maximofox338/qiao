package com.qiao.common.security;

import com.qiao.common.util.Hashing;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates the payroll partner on {@code /api/partner/**} through the {@code X-Partner-Key} header.
 */
public class PartnerKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Partner-Key";

    private final String apiKey;

    public PartnerKeyFilter(PartnerApiProperties properties) {
        this.apiKey = properties.apiKey() == null || properties.apiKey().isBlank() ? null : properties.apiKey();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/partner/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (apiKey != null && Hashing.constantTimeEquals(apiKey, request.getHeader(HEADER))) {
            AuthPrincipal partner = AuthPrincipal.partner();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(partner, null, partner.authorities()));
        }
        chain.doFilter(request, response);
    }
}
