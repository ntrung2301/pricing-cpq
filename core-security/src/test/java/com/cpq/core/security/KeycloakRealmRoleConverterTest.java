package com.cpq.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit test (no Spring context, no network, no Keycloak needed) for the
 * Keycloak realm-role &rarr; Spring authority bridge documented in
 * {@link CpqRole} / ADR-006:
 *
 * <pre>
 *   sales          -&gt; ROLE_SALES
 *   sales_manager  -&gt; ROLE_SALES_MANAGER
 *   pricing_admin  -&gt; ROLE_PRICING_ADMIN
 * </pre>
 *
 * This is the exact converter wired into the real {@code JwtAuthenticationConverter}
 * bean in {@code app}'s {@code SecurityConfig} and reused verbatim in
 * {@code PingEndpointSecurityIT}'s MockMvc tests (S1-T6), so a bug here
 * would be caught by both.
 */
class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void mapsEachKnownKeycloakRealmRoleToItsSpringRoleAuthority() {
        Jwt jwt = jwtWithRealmRoles("sales", "sales_manager", "pricing_admin");

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_SALES", "ROLE_SALES_MANAGER", "ROLE_PRICING_ADMIN");
    }

    @Test
    void returnsNoAuthoritiesWhenRealmAccessClaimIsAbsent() {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .claim("sub", "someone")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private static Jwt jwtWithRealmRoles(String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "someone")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
