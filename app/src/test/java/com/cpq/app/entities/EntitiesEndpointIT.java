package com.cpq.app.entities;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpq.app.metamodel.MetamodelDefinitionService;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.security.KeycloakRealmRoleConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * S1-T8 Definition of Done: "1 API thật (GET /api/entities trả entity
 * definitions từ S1-T7)" — proven here against a real Testcontainers
 * Postgres (not mocked), same pattern as {@code PingEndpointSecurityIT}
 * (S1-T6) and {@code MetamodelDefinitionServiceIT} (S1-T7) combined: seed
 * an entity definition through the real {@link MetamodelDefinitionService},
 * then assert {@code GET /api/entities} round-trips it over HTTP as the
 * generic {@code EntityDefinitionSummary} DTO shape — never the raw JPA
 * entity.
 *
 * <p>Auth requirement (per the task's scope decision, recorded in
 * docs/decisions.md): this is generic read-only metamodel metadata, not a
 * sensitive pricing/approval action, so any of the 3 core roles
 * (sales/sales_manager/pricing_admin) may call it — unlike
 * {@code PingService}, which is deliberately restricted to
 * {@code PRICING_ADMIN} only as a skeleton example.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EntitiesEndpointIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;

    @Autowired private MetamodelDefinitionService metamodelDefinitionService;

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/entities")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCallerSeesSeededEntityDefinitionWithAttributesAsGenericDto() throws Exception {
        var seeded =
                metamodelDefinitionService.defineEntity(
                        "IT_ENTITIES_WIDGET",
                        "IT Entities Widget",
                        1,
                        List.of(
                                new AttributeDefinition(
                                        "name", "Name", AttributeDataType.STRING, true, null),
                                new AttributeDefinition(
                                        "quantity", "Quantity", AttributeDataType.NUMBER, false, null)));

        mockMvc.perform(
                        get("/api/entities")
                                .with(
                                        jwt().jwt(builder ->
                                                        builder.claim(
                                                                "realm_access",
                                                                Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem("IT_ENTITIES_WIDGET")))
                .andExpect(
                        jsonPath("$[?(@.code=='IT_ENTITIES_WIDGET')].id")
                                .value(hasItem(seeded.getId().intValue())))
                .andExpect(jsonPath("$[?(@.code=='IT_ENTITIES_WIDGET')].label").value(hasItem("IT Entities Widget")))
                .andExpect(jsonPath("$[?(@.code=='IT_ENTITIES_WIDGET')].version").value(hasItem(1)))
                .andExpect(
                        jsonPath("$[?(@.code=='IT_ENTITIES_WIDGET')].attributes[0].code")
                                .value(hasItem("name")))
                .andExpect(
                        jsonPath("$[?(@.code=='IT_ENTITIES_WIDGET')].attributes[0].dataType")
                                .value(hasItem("STRING")))
                .andExpect(
                        jsonPath("$[?(@.code=='IT_ENTITIES_WIDGET')].attributes[0].required")
                                .value(hasItem(true)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sales", "sales_manager", "pricing_admin"})
    void anyOfTheThreeCoreRolesCanAccessTheEndpoint(String keycloakRole) throws Exception {
        mockMvc.perform(
                        get("/api/entities")
                                .with(
                                        jwt().jwt(builder ->
                                                        builder.claim(
                                                                "realm_access",
                                                                Map.of("roles", List.of(keycloakRole))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }
}
