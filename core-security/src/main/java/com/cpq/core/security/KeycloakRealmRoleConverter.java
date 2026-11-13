package com.cpq.core.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Bridges Keycloak's realm-role JWT claim ({@code realm_access.roles}) to
 * Spring Security {@link GrantedAuthority}s (ADR-006). Keycloak issues
 * lowercase, underscore-separated role names ({@code sales},
 * {@code sales_manager}, {@code pricing_admin}); Spring's
 * {@code hasRole("X")}/{@code @PreAuthorize} convention expects an
 * uppercase authority prefixed with {@code ROLE_} ({@code ROLE_SALES},
 * {@code ROLE_SALES_MANAGER}, {@code ROLE_PRICING_ADMIN}) — see
 * {@link CpqRole} for the exact table.
 *
 * <p>Any realm role beyond the 3 known {@link CpqRole}s is still mapped
 * (uppercased, {@code ROLE_}-prefixed) rather than silently dropped, so a
 * misconfigured/unexpected Keycloak role simply fails authorization checks
 * (no matching authority grants access) instead of disappearing without a
 * trace.
 *
 * <p>Wired into {@code JwtAuthenticationConverter#setJwtGrantedAuthoritiesConverter}
 * by the Spring Security {@code @Configuration} class in {@code app}
 * ({@code com.cpq.app.security.SecurityConfig}).
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !(realmAccess.get(ROLES_CLAIM) instanceof List<?> roles)) {
            return List.of();
        }

        List<GrantedAuthority> authorities = new ArrayList<>(roles.size());
        for (Object role : roles) {
            String roleName = String.valueOf(role).toUpperCase(Locale.ROOT);
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
        }
        return authorities;
    }
}
