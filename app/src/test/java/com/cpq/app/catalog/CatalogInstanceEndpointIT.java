package com.cpq.app.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.security.KeycloakRealmRoleConverter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * HTTP-facing half of S2-T9-prereq's Definition of Done (sprint-2.md, see
 * docs/decisions.md D-030): {@code GET /api/catalog/{entityDefCode}/instances}
 * must be reachable by any of the 3 core roles (same RBAC level as {@code
 * GET /api/entities}, D-018 mục 1 — not {@code PRICING_ADMIN}-only like
 * import), reject unauthenticated callers with 401, and surface an unknown
 * {@code entityDefCode} as a clear 404, never a 500 — same pattern as
 * {@code ConfiguratorEndpointIT} (S2-T8) / {@code ImportEndpointSecurityIT}
 * (S2-T5).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CatalogInstanceEndpointIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceService entityInstanceService;

    private EntityDefinition defineGadget() {
        String uniqueCode = "Gadget_" + UUID.randomUUID();
        AttributeDefinition sku =
                new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        EntityDefinition entityDefinition = new EntityDefinition(uniqueCode, "Gadget", 1);
        entityDefinition.addAttribute(sku);
        entityDefinition.designateKeyAttribute(sku);
        entityDefinition.designateLabelAttribute(sku);
        return entityDefinitionRepository.save(entityDefinition);
    }

    private EntityInstance createInstance(EntityDefinition definition, String sku) {
        AttributeDefinition skuAttribute = definition.getAttributes().get(0);
        EntityInstance instance = entityInstanceService.createInstance(definition, "test-pack", 1);
        entityInstanceService.writeAttributeValue(instance, skuAttribute, AttributeValue.ofString(sku));
        return instance;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor salesJwt() {
        return jwt().jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("sales"))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        EntityDefinition gadgetDef = defineGadget();
        mockMvc.perform(get("/api/catalog/{code}/instances", gadgetDef.getCode()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsInstancesOverHttpForAnAuthorizedCaller() throws Exception {
        EntityDefinition gadgetDef = defineGadget();
        createInstance(gadgetDef, "GAD-HTTP-1");

        mockMvc.perform(get("/api/catalog/{code}/instances", gadgetDef.getCode()).with(salesJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].businessKey").value("GAD-HTTP-1"))
                .andExpect(jsonPath("$.content[0].attributes.sku").value("GAD-HTTP-1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sales", "sales_manager", "pricing_admin"})
    void anyOfTheThreeCoreRolesCanListInstances(String keycloakRole) throws Exception {
        EntityDefinition gadgetDef = defineGadget();

        mockMvc.perform(
                        get("/api/catalog/{code}/instances", gadgetDef.getCode())
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access",
                                                                        Map.of("roles", List.of(keycloakRole))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEntityDefCodeReturns404NotA500() throws Exception {
        mockMvc.perform(get("/api/catalog/{code}/instances", "NoSuchEntityDef").with(salesJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void pageAndSizeQueryParamsAreHonored() throws Exception {
        EntityDefinition gadgetDef = defineGadget();
        for (int i = 0; i < 5; i++) {
            createInstance(gadgetDef, "GAD-PAGE-" + i);
        }

        mockMvc.perform(
                        get("/api/catalog/{code}/instances", gadgetDef.getCode())
                                .param("page", "1")
                                .param("size", "2")
                                .with(salesJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
