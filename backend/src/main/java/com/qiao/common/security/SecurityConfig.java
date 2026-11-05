package com.qiao.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.List;

/**
 * Stateless JWT security. Coarse role checks by path here; fine-grained admin roles via {@code @PreAuthorize};
 * ownership is always checked in services.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   List<TokenAcceptancePolicy> policies,
                                                   PartnerApiProperties partnerApiProperties,
                                                   JsonErrorWriter errorWriter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.deny()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorWriter.authenticationEntryPoint())
                        .accessDeniedHandler(errorWriter.accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/public/**", "/api/auth/otp/**", "/api/admin/auth/login",
                                "/api/payments/stripe/webhook", "/api/dev/**", "/api/openapi/**", "/api/docs/**",
                                "/swagger-ui/**", "/error").permitAll()
                        .requestMatchers("/api/partner/**").hasRole(AuthPrincipal.ROLE_PARTNER)
                        .requestMatchers("/api/admin/**").hasRole(AuthPrincipal.ROLE_ADMIN)
                        .requestMatchers("/api/engineer/**").hasRole("ENGINEER")
                        .requestMatchers("/api/client/**").hasRole("CLIENT")
                        .requestMatchers("/api/me/**", "/api/me", "/api/contracts/**", "/api/attachments/**",
                                "/api/payments/**").hasAnyRole("CLIENT", "ENGINEER")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, policies), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new PartnerKeyFilter(partnerApiProperties), JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * SUPERADMIN implies every other admin role.
     */
    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPERADMIN > ROLE_OPS
                ROLE_SUPERADMIN > ROLE_FINANCE
                ROLE_SUPERADMIN > ROLE_COMPLIANCE
                """);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
