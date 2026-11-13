package com.cpq.app.configurator;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpq.app.constraint.ConstraintDefinitionRepository;
import com.cpq.app.constraint.ConstraintInstanceRepository;
import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.ConstraintDefinition;
import com.cpq.core.metamodel.ConstraintInstance;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * HTTP-facing half of S2-T8/E2-01's Definition of Done (sprint-2.md):
 * "Chọn combination hợp lệ → pass; vi phạm constraint → lỗi rõ (400 có
 * message), không phải lỗi 500" — exercised over real MockMvc +
 * Testcontainers Postgres, same pattern as {@code PricingEndpointIT}
 * (S2-T10): 401 unauthenticated, 200 for a passing selection, 400 (never
 * 500) for a failing one, and 200 for any of the 3 core roles.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ConfiguratorEndpointIT {

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
    @Autowired private ConstraintDefinitionRepository constraintDefinitionRepository;
    @Autowired private ConstraintInstanceRepository constraintInstanceRepository;

    private EntityDefinition defineColoredEntity(String namePrefix) {
        String uniqueCode = namePrefix + "_" + UUID.randomUUID();
        AttributeDefinition color =
                new AttributeDefinition("color", "Color", AttributeDataType.STRING, true, null);
        EntityDefinition entityDefinition = new EntityDefinition(uniqueCode, namePrefix, 1);
        entityDefinition.addAttribute(color);
        return entityDefinitionRepository.save(entityDefinition);
    }

    private static AttributeDefinition colorAttribute(EntityDefinition entityDefinition) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals("color"))
                .findFirst()
                .orElseThrow();
    }

    private EntityInstance createInstanceWithColor(EntityDefinition definition, String color) {
        EntityInstance instance = entityInstanceService.createInstance(definition, "test-pack", 1);
        entityInstanceService.writeAttributeValue(instance, colorAttribute(definition), AttributeValue.ofString(color));
        return instance;
    }

    private ConstraintInstance defineSameColorConstraint(EntityInstance x, EntityInstance y) {
        ConstraintDefinition compatibility =
                constraintDefinitionRepository.save(
                        new ConstraintDefinition("compat-" + UUID.randomUUID(), "compatibility"));
        return constraintInstanceRepository.save(
                new ConstraintInstance(
                        compatibility, "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}", x, y));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor salesJwt() {
        return jwt().jwt(
                        builder ->
                                builder.claim("realm_access", Map.of("roles", List.of("sales"))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void unauthenticatedValidateSelectionIsRejectedWith401() throws Exception {
        mockMvc.perform(
                        post("/api/configurator/validate-selection")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entityInstanceIds\": [1, 2]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passingSelectionReturns200WithValidTrue() throws Exception {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        EntityInstance sprocket = createInstanceWithColor(sprocketDef, "red");
        EntityInstance cog = createInstanceWithColor(cogDef, "red");
        defineSameColorConstraint(sprocket, cog);

        mockMvc.perform(
                        post("/api/configurator/validate-selection")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"entityInstanceIds\": [%d, %d]}"
                                                .formatted(sprocket.getId(), cog.getId()))
                                .with(salesJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.violations").isEmpty());
    }

    @Test
    void failingSelectionReturns400WithClearMessageNotA500() throws Exception {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        EntityInstance sprocket = createInstanceWithColor(sprocketDef, "red");
        EntityInstance cog = createInstanceWithColor(cogDef, "blue");
        defineSameColorConstraint(sprocket, cog);

        mockMvc.perform(
                        post("/api/configurator/validate-selection")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"entityInstanceIds\": [%d, %d]}"
                                                .formatted(sprocket.getId(), cog.getId()))
                                .with(salesJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.violations[0].entityInstanceXId").value(sprocket.getId()))
                .andExpect(jsonPath("$.violations[0].entityInstanceYId").value(cog.getId()))
                .andExpect(jsonPath("$.violations[0].message").isNotEmpty());
    }

    @Test
    void selectionWithNoRelatedConstraintInstanceReturns200() throws Exception {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        EntityInstance sprocket = createInstanceWithColor(sprocketDef, "red");
        EntityInstance cog = createInstanceWithColor(cogDef, "blue");
        // No constraint_instance created between them.

        mockMvc.perform(
                        post("/api/configurator/validate-selection")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"entityInstanceIds\": [%d, %d]}"
                                                .formatted(sprocket.getId(), cog.getId()))
                                .with(salesJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sales", "sales_manager", "pricing_admin"})
    void anyOfTheThreeCoreRolesCanValidateASelection(String keycloakRole) throws Exception {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        EntityInstance sprocket = createInstanceWithColor(sprocketDef, "red");
        EntityInstance cog = createInstanceWithColor(cogDef, "red");
        defineSameColorConstraint(sprocket, cog);

        mockMvc.perform(
                        post("/api/configurator/validate-selection")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"entityInstanceIds\": [%d, %d]}"
                                                .formatted(sprocket.getId(), cog.getId()))
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
    void unauthenticatedListCompatibleIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/configurator/compatible").param("anchorInstanceId", "1").param("targetEntityDefCode", "X"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listCompatibleReturnsOnlyPassingMatchesOverHttp() throws Exception {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        EntityInstance anchor = createInstanceWithColor(sprocketDef, "red");
        EntityInstance matchingCog = createInstanceWithColor(cogDef, "red");
        EntityInstance nonMatchingCog = createInstanceWithColor(cogDef, "blue");
        defineSameColorConstraint(anchor, matchingCog);
        defineSameColorConstraint(anchor, nonMatchingCog);

        mockMvc.perform(
                        get("/api/configurator/compatible")
                                .param("anchorInstanceId", String.valueOf(anchor.getId()))
                                .param("targetEntityDefCode", cogDef.getCode())
                                .with(salesJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityInstanceId").value(matchingCog.getId()));
    }
}
