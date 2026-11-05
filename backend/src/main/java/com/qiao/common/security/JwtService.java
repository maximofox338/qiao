package com.qiao.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Issues and verifies HS256 JWTs. Claims: {@code sub} = id, {@code typ} = user|admin, {@code role} or {@code roles};
 * admin tokens also carry {@code tv}, the admin's token version when the token was issued.
 */
@Service
public class JwtService {

    private static final String TYPE_USER = "user";
    private static final String TYPE_ADMIN = "admin";
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.algorithm = Algorithm.HMAC256(properties.secret());
        this.verifier = JWT.require(algorithm).acceptLeeway(5).build();
    }

    public IssuedToken issueUserToken(long userId, String role) {
        return issue(userId, TYPE_USER, builder -> builder.withClaim("role", role));
    }

    public IssuedToken issueAdminToken(long adminId, Collection<String> roles, int tokenVersion) {
        return issue(adminId, TYPE_ADMIN, builder -> builder.withClaim("roles", List.copyOf(roles))
                .withClaim(CLAIM_TOKEN_VERSION, tokenVersion));
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            long id = Long.parseLong(jwt.getSubject());
            String type = jwt.getClaim("typ").asString();
            Instant expiresAt = jwt.getExpiresAtAsInstant();
            if (TYPE_USER.equals(type)) {
                String role = jwt.getClaim("role").asString();
                return role == null ? Optional.empty()
                        : Optional.of(new AuthPrincipal(id, AuthPrincipal.Kind.USER, role, Set.of(), jwt.getId(), expiresAt, 0));
            }
            if (TYPE_ADMIN.equals(type)) {
                List<String> roles = jwt.getClaim("roles").asList(String.class);
                Set<String> adminRoles = roles == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(roles));
                // tokens issued before token versions existed have no claim: version 0, the column's default
                Integer tokenVersion = jwt.getClaim(CLAIM_TOKEN_VERSION).asInt();
                return Optional.of(new AuthPrincipal(id, AuthPrincipal.Kind.ADMIN, null, adminRoles, jwt.getId(), expiresAt,
                        tokenVersion == null ? 0 : tokenVersion));
            }
            return Optional.empty();
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private IssuedToken issue(long subject, String type, UnaryOperator<JWTCreator.Builder> claims) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.ttl());
        String tokenId = UUID.randomUUID().toString();
        String token = claims.apply(JWT.create()
                        .withSubject(Long.toString(subject))
                        .withClaim("typ", type)
                        .withJWTId(tokenId)
                        .withIssuedAt(now)
                        .withExpiresAt(expiresAt))
                .sign(algorithm);
        return new IssuedToken(token, tokenId, expiresAt);
    }
}
