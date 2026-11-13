package com.cpq.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T1 Definition of Done (sprint-2.md): "Test tích hợp tạo 1 entity_def
 * có key+label, tạo 2 entity_instance — ghi trùng business_key → lỗi
 * unique constraint". Follows the same Testcontainers pattern as
 * {@code MetamodelDefinitionServiceIT} (S1-T7): a real, disposable
 * Postgres container with Flyway migrating every {@code V*.sql} (now
 * including {@code V4__entity_instance_and_pack_tracking.sql}).
 */
@Testcontainers
@SpringBootTest
class EntityInstanceServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceRepository entityInstanceRepository;
    @Autowired private EntityInstanceService entityInstanceService;

    // Each @Test below shares the same static Testcontainers Postgres (one schema
    // per test class, not per test method), so entity_def.code must be unique
    // per call to avoid tripping the uq_entity_def_code constraint across methods.
    private EntityDefinition defineWidgetWithKeyAndLabel() {
        String uniqueCode = "EIS_WIDGET_" + UUID.randomUUID();
        AttributeDefinition sku =
                new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        AttributeDefinition name =
                new AttributeDefinition("name", "Name", AttributeDataType.STRING, true, null);
        EntityDefinition entityDefinition = new EntityDefinition(uniqueCode, "EIS Widget", 1);
        entityDefinition.addAttribute(sku);
        entityDefinition.addAttribute(name);
        EntityDefinition saved = entityDefinitionRepository.save(entityDefinition);

        AttributeDefinition savedSku = findByCode(saved, "sku");
        AttributeDefinition savedName = findByCode(saved, "name");
        saved.designateKeyAttribute(savedSku);
        saved.designateLabelAttribute(savedName);
        return entityDefinitionRepository.save(saved);
    }

    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void writingTheKeyAndLabelAttributeValuesSyncsBusinessKeyAndDisplayLabel() {
        EntityDefinition widget = defineWidgetWithKeyAndLabel();
        AttributeDefinition sku = findByCode(widget, "sku");
        AttributeDefinition name = findByCode(widget, "name");

        EntityInstance instance = entityInstanceService.createInstance(widget, "test-pack", 1);
        entityInstanceService.writeAttributeValue(instance, sku, AttributeValue.ofString("ABC-100"));
        entityInstanceService.writeAttributeValue(instance, name, AttributeValue.ofString("Widget ABC"));

        EntityInstance reloaded = entityInstanceRepository.findById(instance.getId()).orElseThrow();
        assertThat(reloaded.getBusinessKey()).isEqualTo("ABC-100");
        assertThat(reloaded.getDisplayLabel()).isEqualTo("Widget ABC");
    }

    @Test
    void writingADuplicateBusinessKeyForTheSamePackFailsWithUniqueConstraintViolation() {
        EntityDefinition widget = defineWidgetWithKeyAndLabel();
        AttributeDefinition sku = findByCode(widget, "sku");

        EntityInstance first = entityInstanceService.createInstance(widget, "test-pack", 1);
        entityInstanceService.writeAttributeValue(first, sku, AttributeValue.ofString("DUP-KEY"));

        EntityInstance second = entityInstanceService.createInstance(widget, "test-pack", 1);

        assertThatThrownBy(
                        () ->
                                entityInstanceService.writeAttributeValue(
                                        second, sku, AttributeValue.ofString("DUP-KEY")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void writingAValueThatDoesNotMatchTheAttributesDataTypeIsRejected() {
        EntityDefinition widget = defineWidgetWithKeyAndLabel();
        AttributeDefinition sku = findByCode(widget, "sku"); // STRING

        EntityInstance instance = entityInstanceService.createInstance(widget, "test-pack", 1);

        assertThatThrownBy(
                        () ->
                                entityInstanceService.writeAttributeValue(
                                        instance, sku, AttributeValue.ofBoolean(true)))
                .isInstanceOf(AttributeValueTypeMismatchException.class);
    }
}
