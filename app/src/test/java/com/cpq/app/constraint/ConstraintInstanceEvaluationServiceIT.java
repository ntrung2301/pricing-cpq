package com.cpq.app.constraint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.constraintengine.ConstraintEvaluationResult;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.ConstraintDefinition;
import com.cpq.core.metamodel.ConstraintInstance;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T2 Definition of Done (sprint-2.md): "Test tích hợp tạo 1
 * constraint_def 'compatibility', 1 constraint_instance giữa 2
 * entity_instance tuỳ ý (không phải Part/Vehicle) — evaluate đúng
 * pass/fail theo điều kiện." Deliberately uses two made-up,
 * non-automotive entities ("Gadget"/"Widget") — the point of this test is
 * to prove the constraint machinery (constraint_def/constraint_instance/
 * ConstraintEngine) has no idea what "fitment"/"Part"/"Vehicle" are
 * (E1-02). Follows the same Testcontainers pattern as
 * {@code EntityInstanceServiceIT} (S2-T1): a real, disposable Postgres
 * container with Flyway migrating every {@code V*.sql}, including the new
 * {@code V5__constraint_instance.sql}.
 */
@Testcontainers
@SpringBootTest
class ConstraintInstanceEvaluationServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceService entityInstanceService;
    @Autowired private ConstraintDefinitionRepository constraintDefinitionRepository;
    @Autowired private ConstraintInstanceRepository constraintInstanceRepository;
    @Autowired private ConstraintInstanceEvaluationService evaluationService;

    // Each @Test below shares the same static Testcontainers Postgres (one schema
    // per test class, not per test method), so codes must be unique per call to
    // avoid tripping unique constraints across methods.
    private EntityDefinition defineColoredEntity(String namePrefix) {
        String uniqueCode = namePrefix + "_" + UUID.randomUUID();
        AttributeDefinition color =
                new AttributeDefinition("color", "Color", AttributeDataType.STRING, true, null);
        AttributeDefinition weight =
                new AttributeDefinition("weight", "Weight", AttributeDataType.DECIMAL, false, null);
        EntityDefinition entityDefinition = new EntityDefinition(uniqueCode, namePrefix, 1);
        entityDefinition.addAttribute(color);
        entityDefinition.addAttribute(weight);
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

    @Test
    void evaluatesPassWhenTwoArbitraryEntityInstancesSatisfyTheCondition() {
        EntityDefinition gadgetDef = defineColoredEntity("Gadget");
        EntityDefinition widgetDef = defineColoredEntity("Widget");
        AttributeDefinition gadgetColor = findByCode(gadgetDef, "color");
        AttributeDefinition widgetColor = findByCode(widgetDef, "color");

        EntityInstance gadget = entityInstanceService.createInstance(gadgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(gadget, gadgetColor, AttributeValue.ofString("red"));

        EntityInstance widget = entityInstanceService.createInstance(widgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(widget, widgetColor, AttributeValue.ofString("red"));

        ConstraintDefinition compatibility = defineCompatibilityConstraint();
        String condition = "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}";
        ConstraintInstance constraintInstance =
                constraintInstanceRepository.save(
                        new ConstraintInstance(compatibility, condition, gadget, widget));

        ConstraintEvaluationResult result = evaluationService.evaluate(constraintInstance);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void evaluatesFailWhenTwoArbitraryEntityInstancesViolateTheCondition() {
        EntityDefinition gadgetDef = defineColoredEntity("Gadget");
        EntityDefinition widgetDef = defineColoredEntity("Widget");
        AttributeDefinition gadgetColor = findByCode(gadgetDef, "color");
        AttributeDefinition widgetColor = findByCode(widgetDef, "color");

        EntityInstance gadget = entityInstanceService.createInstance(gadgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(gadget, gadgetColor, AttributeValue.ofString("red"));

        EntityInstance widget = entityInstanceService.createInstance(widgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(widget, widgetColor, AttributeValue.ofString("blue"));

        ConstraintDefinition compatibility = defineCompatibilityConstraint();
        String condition = "{\"==\": [{\"var\": \"x.color\"}, {\"var\": \"y.color\"}]}";
        ConstraintInstance constraintInstance =
                constraintInstanceRepository.save(
                        new ConstraintInstance(compatibility, condition, gadget, widget));

        ConstraintEvaluationResult result = evaluationService.evaluate(constraintInstance);

        assertThat(result.passed()).isFalse();
    }

    @Test
    void evaluatesUsingNumericAttributesAcrossBothInstances() {
        EntityDefinition gadgetDef = defineColoredEntity("Gadget");
        EntityDefinition widgetDef = defineColoredEntity("Widget");
        AttributeDefinition gadgetWeight = findByCode(gadgetDef, "weight");
        AttributeDefinition widgetWeight = findByCode(widgetDef, "weight");
        AttributeDefinition gadgetColor = findByCode(gadgetDef, "color");
        AttributeDefinition widgetColor = findByCode(widgetDef, "color");

        EntityInstance gadget = entityInstanceService.createInstance(gadgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(gadget, gadgetColor, AttributeValue.ofString("n/a"));
        entityInstanceService.writeAttributeValue(
                gadget, gadgetWeight, AttributeValue.ofNumber(new BigDecimal("3.2")));

        EntityInstance widget = entityInstanceService.createInstance(widgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(widget, widgetColor, AttributeValue.ofString("n/a"));
        entityInstanceService.writeAttributeValue(
                widget, widgetWeight, AttributeValue.ofNumber(new BigDecimal("5")));

        ConstraintDefinition compatibility = defineCompatibilityConstraint();
        String condition = "{\"<=\": [{\"var\": \"x.weight\"}, {\"var\": \"y.weight\"}]}";
        ConstraintInstance constraintInstance =
                constraintInstanceRepository.save(
                        new ConstraintInstance(compatibility, condition, gadget, widget));

        assertThat(evaluationService.evaluate(constraintInstance).passed()).isTrue();
    }
}
