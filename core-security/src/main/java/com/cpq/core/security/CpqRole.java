package com.cpq.core.security;

import java.util.Locale;

/**
 * The 3 generic RBAC roles shared by every CPQ deployment/domain pack
 * (ADR-006, backlog E5-02): Sales, Sales Manager, Pricing Admin. These are
 * a **core** concept, not automotive- or any-other-domain-specific
 * (CLAUDE.md "Ranh giới Core vs Domain", D-013) — exactly like "quote" is
 * core, not domain, knowledge.
 *
 * <p>{@link #keycloakRoleName()} is the exact realm role name configured
 * in Keycloak (see {@code keycloak/realm-export.json}); {@link #springAuthority()}
 * is the Spring Security {@link org.springframework.security.core.GrantedAuthority#getAuthority()}
 * name after {@code ROLE_}-prefixing + uppercasing, applied by
 * {@link KeycloakRealmRoleConverter}. Exact mapping (ADR-006):
 *
 * <pre>
 *   sales          -&gt; ROLE_SALES
 *   sales_manager  -&gt; ROLE_SALES_MANAGER
 *   pricing_admin  -&gt; ROLE_PRICING_ADMIN
 * </pre>
 *
 * <p>Per ADR-006's "Hệ quả" section, logic should prefer the {@link
 * org.springframework.security.core.GrantedAuthority} interface (e.g.
 * {@code @PreAuthorize("hasRole('PRICING_ADMIN')")}) over hardcoding this
 * enum wherever reasonably possible, to keep the door open for a future
 * finer-grained permission model without redesigning role handling. This
 * enum exists for documentation/tooling (e.g. realm-export generation,
 * tests) rather than as the primary mechanism application code should
 * branch on.
 */
public enum CpqRole {
    SALES("sales"),
    SALES_MANAGER("sales_manager"),
    PRICING_ADMIN("pricing_admin");

    private final String keycloakRoleName;

    CpqRole(String keycloakRoleName) {
        this.keycloakRoleName = keycloakRoleName;
    }

    public String keycloakRoleName() {
        return keycloakRoleName;
    }

    /** E.g. {@code ROLE_PRICING_ADMIN} — Spring's {@code hasRole(...)} convention. */
    public String springAuthority() {
        return "ROLE_" + keycloakRoleName.toUpperCase(Locale.ROOT);
    }
}
