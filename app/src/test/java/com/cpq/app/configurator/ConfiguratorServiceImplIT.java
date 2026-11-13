package com.cpq.app.configurator;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.app.constraint.ConstraintDefinitionRepository;
import com.cpq.app.constraint.ConstraintInstanceRepository;
import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.api.configurator.CompatibleInstanceSummary;
import com.cpq.core.api.configurator.ConfiguratorService;
import com.cpq.core.api.configurator.ValidateSelectionRequest;
import com.cpq.core.api.configurator.ValidateSelectionResponse;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.ConstraintDefinition;
import com.cpq.core.metamodel.ConstraintInstance;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T8/E2-01 Definition of Done (sprint-2.md): "Chọn combination hợp lệ →
 * pass; vi phạm constraint → lỗi rõ (400 có message), không phải lỗi 500."
 * Exercised here at the service layer (the HTTP 200/400 mapping itself is
 * covered by {@link ConfiguratorEndpointIT}), against a real Testcontainers
 * Postgres — same pattern as {@code ConstraintInstanceEvaluationServiceIT}
 * (S2-T2). Deliberately uses two made-up, non-automotive entities
 * ("Sprocket"/"Cog") — {@link ConfiguratorServiceImpl} must have no idea
 * whether a selection is "vehicle + part" or anything else (D-024's own
 * generic "x"/"y" framing).
 */
@Testcontainers
@SpringBootTest
class ConfiguratorServiceImplIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void authenticateAsSales() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("test-sales-rep", null, "ROLE_SALES"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceService entityInstanceService;
    @Autowired private ConstraintDefinitionRepository constraintDefinitionRepository;
    @Autowired private ConstraintInstanceRepository constraintInstanceRepository;
    @Autowired private ConfiguratorService configuratorService;

    // Each @Test shares the same static Testcontainers Postgres (one schema per
    // test class), so codes must be unique per call to avoid unique-constraint
    // collisions across test methods.
    private EntityDefinition defineColoredEntity(String namePrefix) {
        String uniqueCode = namePrefix + "_" + UUID.randomUUID();
        AttributeDefinition color =
                new AttributeDefinition("color", "Color", AttributeDataType.STRING, true, null);
        EntityDefinition entityDefinition = new EntityDefinition(uniqueCode, namePrefix, 1);
        entityDefinition.addAttribute(color);
        return entityDefinitionRepository.save(entityDefinition);
    }

    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private ConstraintDefinition defineCompatibilityConstraint() {
        ConstraintDefinition definition =
                new ConstraintDefinition("compat-" + UUID.randomUUID(), "compatibility");
        return constraintDefinitionRepository.save(definition);
    }

    private EntityInstance createInstanceWithColor(
            EntityDefinition definition, AttributeDefinition colorAttribute, String color) {
        EntityInstance instance = entityInstanceService.createInstance(definition, "test-pack", 1);
        entityInstanceService.writeAttributeValue(instance, colorAttribute, AttributeValue.ofString(color));
        return instance;
    }

    @Test
    void selectionPassesWhenTheOnlyRelevantConstraintInstancePasses() {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        AttributeDefinition sprocketColor = findByCode(sprocketDef, "color");
        AttributeDefinition cogColor = findByCode(cogDef, "color");

        EntityInstance sprocket = createInstanceWithColor(sprocketDef, sprocketColor, "red");
        EntityInstance cog = createInstanceWithColor(cogDef, cogColor, "red");

        ConstraintDefinition compatibility = defineCompatibilityConstraint();
        constraintInstanceRepository.save(
                new ConstraintInstance(
                        compatibility,
                        "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}",
                        sprocket,
                        cog));

        ValidateSelectionResponse response =
                configuratorService.validateSelection(
                        new ValidateSelectionRequest(List.of(sprocket.getId(), cog.getId())));

        assertThat(response.valid()).isTrue();
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void selectionFailsWithClearMessageWhenARelevantConstraintInstanceFails() {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        AttributeDefinition sprocketColor = findByCode(sprocketDef, "color");
        AttributeDefinition cogColor = findByCode(cogDef, "color");

        EntityInstance sprocket = createInstanceWithColor(sprocketDef, sprocketColor, "red");
        EntityInstance cog = createInstanceWithColor(cogDef, cogColor, "blue");

        ConstraintDefinition compatibility = defineCompatibilityConstraint();
        ConstraintInstance constraintInstance =
                constraintInstanceRepository.save(
                        new ConstraintInstance(
                                compatibility,
                                "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}",
                                sprocket,
                                cog));

        ValidateSelectionResponse response =
                configuratorService.validateSelection(
                        new ValidateSelectionRequest(List.of(sprocket.getId(), cog.getId())));

        assertThat(response.valid()).isFalse();
        assertThat(response.violations()).hasSize(1);
        var violation = response.violations().get(0);
        assertThat(violation.constraintInstanceId()).isEqualTo(constraintInstance.getId());
        assertThat(violation.constraintDefCode()).isEqualTo(compatibility.getCode());
        assertThat(violation.entityInstanceXId()).isEqualTo(sprocket.getId());
        assertThat(violation.entityInstanceYId()).isEqualTo(cog.getId());
        assertThat(violation.message()).contains(compatibility.getCode());
        assertThat(violation.message()).contains(String.valueOf(sprocket.getId()));
        assertThat(violation.message()).contains(String.valueOf(cog.getId()));
    }

    @Test
    void selectionWithNoRelatedConstraintInstanceIsValid() {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        AttributeDefinition sprocketColor = findByCode(sprocketDef, "color");
        AttributeDefinition cogColor = findByCode(cogDef, "color");

        EntityInstance sprocket = createInstanceWithColor(sprocketDef, sprocketColor, "red");
        EntityInstance cog = createInstanceWithColor(cogDef, cogColor, "blue");
        // Deliberately no constraint_instance created between them at all.

        ValidateSelectionResponse response =
                configuratorService.validateSelection(
                        new ValidateSelectionRequest(List.of(sprocket.getId(), cog.getId())));

        assertThat(response.valid()).isTrue();
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void selectionWithFewerThanTwoIdsIsTriviallyValid() {
        ValidateSelectionResponse response =
                configuratorService.validateSelection(new ValidateSelectionRequest(List.of(999L)));

        assertThat(response.valid()).isTrue();
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void listCompatibleInstancesReturnsOnlyPassingMatchesOfTheRequestedEntityDef() {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        EntityDefinition otherDef = defineColoredEntity("Gizmo");
        AttributeDefinition sprocketColor = findByCode(sprocketDef, "color");
        AttributeDefinition cogColor = findByCode(cogDef, "color");
        AttributeDefinition otherColor = findByCode(otherDef, "color");

        EntityInstance anchor = createInstanceWithColor(sprocketDef, sprocketColor, "red");
        EntityInstance matchingCog = createInstanceWithColor(cogDef, cogColor, "red");
        EntityInstance nonMatchingCog = createInstanceWithColor(cogDef, cogColor, "blue");
        EntityInstance matchingButWrongDef = createInstanceWithColor(otherDef, otherColor, "red");

        ConstraintDefinition compatibility = defineCompatibilityConstraint();
        String sameColorCondition = "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}";
        constraintInstanceRepository.save(
                new ConstraintInstance(compatibility, sameColorCondition, anchor, matchingCog));
        constraintInstanceRepository.save(
                new ConstraintInstance(compatibility, sameColorCondition, anchor, nonMatchingCog));
        constraintInstanceRepository.save(
                new ConstraintInstance(compatibility, sameColorCondition, anchor, matchingButWrongDef));

        List<CompatibleInstanceSummary> result =
                configuratorService.listCompatibleInstances(anchor.getId(), cogDef.getCode());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityInstanceId()).isEqualTo(matchingCog.getId());
    }

    @Test
    void listCompatibleInstancesWorksRegardlessOfWhichSideTheAnchorIsOn() {
        EntityDefinition sprocketDef = defineColoredEntity("Sprocket");
        EntityDefinition cogDef = defineColoredEntity("Cog");
        AttributeDefinition sprocketColor = findByCode(sprocketDef, "color");
        AttributeDefinition cogColor = findByCode(cogDef, "color");

        EntityInstance anchor = createInstanceWithColor(cogDef, cogColor, "green");
        EntityInstance matchingSprocket = createInstanceWithColor(sprocketDef, sprocketColor, "green");

        ConstraintDefinition compatibility = defineCompatibilityConstraint();
        String sameColorCondition = "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}";
        // Anchor placed on the "y" side this time, matching instance on "x".
        constraintInstanceRepository.save(
                new ConstraintInstance(compatibility, sameColorCondition, matchingSprocket, anchor));

        List<CompatibleInstanceSummary> result =
                configuratorService.listCompatibleInstances(anchor.getId(), sprocketDef.getCode());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityInstanceId()).isEqualTo(matchingSprocket.getId());
    }
}
