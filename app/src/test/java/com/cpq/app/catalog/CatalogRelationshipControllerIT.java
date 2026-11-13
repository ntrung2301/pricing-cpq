package com.cpq.app.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.core.metamodel.EntityInstance;
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
 * HTTP-facing half of S2-T7's Definition of Done: "part bị supersede → API
 * gợi ý part thay thế" — exercised over real MockMvc + Testcontainers
 * Postgres (the real seeded automotive demo data, same pattern as
 * {@code com.cpq.app.demo.AutomotiveFitmentSeedRunnerIT}), plus the usual
 * RBAC check ({@code GET /api/configurator/compatible}'s own pattern,
 * S2-T8/D-027): 401 unauthenticated, 200 with the correct replacement part
 * for an authenticated role.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CatalogRelationshipControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityInstanceRepository entityInstanceRepository;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor salesJwt() {
        return jwt().jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("sales"))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void unauthenticatedSupersededByIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/catalog/instances/1/superseded-by"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void supersededOldPartResolvesToItsRealReplacementOverHttp() throws Exception {
        EntityInstance oldAlternator =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey("Product", "ALT-OLD-500")
                        .orElseThrow();

        mockMvc.perform(
                        get("/api/catalog/instances/" + oldAlternator.getId() + "/superseded-by")
                                .with(salesJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].businessKey").value("ALT-NEW-500"));
    }

    @Test
    void partWithNoSupersessionReturnsEmptyList() throws Exception {
        EntityInstance sparkPlug =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey("Product", "SPK-PLUG-001")
                        .orElseThrow();

        mockMvc.perform(
                        get("/api/catalog/instances/" + sparkPlug.getId() + "/superseded-by")
                                .with(salesJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
