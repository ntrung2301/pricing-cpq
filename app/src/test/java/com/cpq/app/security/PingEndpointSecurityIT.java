package com.cpq.app.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpq.core.security.KeycloakRealmRoleConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-006 / S1-T6 Definition of Done: "API bảo vệ trả 401/403 đúng theo
 * role" — proven here with MockMvc + spring-security-test's JWT support,
 * so it runs in the normal {@code ./gradlew build} suite without a live
 * Keycloak (real end-to-end verification against a live Keycloak is the
 * separate manual step recorded in docs/decisions.md).
 *
 * <p>Exercises {@code GET /api/ping} ({@code core-api}'s {@code PingController}
 * — a skeleton verification endpoint only, not a real business feature),
 * whose {@code @PreAuthorize("hasRole('PRICING_ADMIN')")} actually lives on
 * {@code com.cpq.core.api.ping.PingService#ping()} — a service method, not
 * the controller — per ADR-006's "enforce at the method layer" requirement.
 * If the annotation were only on the controller, these tests would still
 * pass, so this alone doesn't prove method-layer placement — that is
 * verified by reading {@code PingService}, not by this test — but the
 * 401/403/200 behavior itself is exactly what S1-T6 requires.
 *
 * <p>Authorities for the "wrong role" / "correct role" cases are derived
 * using the real {@link KeycloakRealmRoleConverter} (not reimplemented
 * here), so this test also exercises the exact production role-mapping
 * logic end-to-end through method security.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PingEndpointSecurityIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleIsRejectedWith403() throws Exception {
        mockMvc.perform(
                        get("/api/ping")
                                .with(
                                        jwt().jwt(builder ->
                                                        builder.claim(
                                                                "realm_access",
                                                                Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void pricingAdminRoleIsAcceptedWith200() throws Exception {
        mockMvc.perform(
                        get("/api/ping")
                                .with(
                                        jwt().jwt(builder ->
                                                        builder.claim(
                                                                "realm_access",
                                                                Map.of(
                                                                        "roles",
                                                                        List.of("pricing_admin"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }
}
