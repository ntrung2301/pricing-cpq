package com.cpq.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityDefinitionDimensionAttribute;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityRole;
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
    @Autowired private EntityDefinitionDimensionAttributeRepository dimensionAttributeRepository;

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
    void anchorInstanceIdIsIgnoredWhenTheEntityDefinitionIsNotAnExtension() {
        EntityDefinition widget = defineWidgetWithKeyAndLabel();

        // S3-T1 (D-039): passing a non-null anchorInstanceId for a
        // non-EXTENSION entity definition is not an error — it simply has
        // no effect (the 4-arg overload degrades to the plain 3-arg one).
        EntityInstance instance = entityInstanceService.createInstance(widget, "test-pack", 1, 999_999L);

        assertThat(instance.getAnchorInstance()).isNull();
    }

    @Test
    void writingAllOrderedDimensionAttributesOfAMultiDimensionExtensionInstanceComposesKeyInPositionOrder() {
        String uniqueSuffix = UUID.randomUUID().toString();
        AttributeDefinition anchorCode =
                new AttributeDefinition("anchor_code", "Anchor Code", AttributeDataType.STRING, true, null);
        EntityDefinition anchor = new EntityDefinition("EIS_ANCHOR_" + uniqueSuffix, "EIS Anchor", 1);
        anchor.addAttribute(anchorCode);
        EntityDefinition savedAnchor = entityDefinitionRepository.save(anchor);
        AttributeDefinition savedAnchorCode = findByCode(savedAnchor, "anchor_code");
        savedAnchor.designateKeyAttribute(savedAnchorCode);
        savedAnchor = entityDefinitionRepository.save(savedAnchor);

        AttributeDefinition period =
                new AttributeDefinition("period", "Period", AttributeDataType.STRING, true, null);
        AttributeDefinition city = new AttributeDefinition("city", "City", AttributeDataType.STRING, true, null);
        EntityDefinition extension = new EntityDefinition("EIS_EXTENSION_" + uniqueSuffix, "EIS Extension", 1);
        extension.addAttribute(period);
        extension.addAttribute(city);
        extension.assignRole(EntityRole.EXTENSION);
        extension.assignExtendsEntityDefinition(savedAnchor);
        EntityDefinition savedExtension = entityDefinitionRepository.save(extension);
        AttributeDefinition savedPeriod = findByCode(savedExtension, "period");
        AttributeDefinition savedCity = findByCode(savedExtension, "city");
        // position 0 = period, 1 = city — deliberately not alphabetical, to
        // prove the composite key follows `position`, not declaration or
        // write order.
        dimensionAttributeRepository.save(
                new EntityDefinitionDimensionAttribute(savedExtension, savedPeriod, 0));
        dimensionAttributeRepository.save(new EntityDefinitionDimensionAttribute(savedExtension, savedCity, 1));

        EntityInstance anchorInstance = entityInstanceService.createInstance(savedAnchor, "test-pack", 1);
        entityInstanceService.writeAttributeValue(anchorInstance, savedAnchorCode, AttributeValue.ofString("ANCHOR-1"));

        EntityInstance extensionInstance =
                entityInstanceService.createInstance(savedExtension, "test-pack", 1, anchorInstance.getId());
        // Written out of position order (city before period) — final key
        // must still be ordered by `position`, not write order.
        entityInstanceService.writeAttributeValue(extensionInstance, savedCity, AttributeValue.ofString("HCMC"));
        entityInstanceService.writeAttributeValue(extensionInstance, savedPeriod, AttributeValue.ofString("2026-Q1"));

        EntityInstance reloaded = entityInstanceRepository.findById(extensionInstance.getId()).orElseThrow();
        assertThat(reloaded.getAnchorInstance().getId()).isEqualTo(anchorInstance.getId());
        assertThat(reloaded.getBusinessKey()).isEqualTo("ANCHOR-1|2026-Q1|HCMC");
    }

    @Test
    void creatingAnExtensionInstanceWithANonExistentAnchorInstanceIdFailsClearly() {
        String uniqueSuffix = UUID.randomUUID().toString();
        EntityDefinition anchor = new EntityDefinition("EIS_ANCHOR2_" + uniqueSuffix, "EIS Anchor 2", 1);
        EntityDefinition savedAnchor = entityDefinitionRepository.save(anchor);
        EntityDefinition extension = new EntityDefinition("EIS_EXTENSION2_" + uniqueSuffix, "EIS Extension 2", 1);
        extension.assignRole(EntityRole.EXTENSION);
        extension.assignExtendsEntityDefinition(savedAnchor);
        EntityDefinition savedExtension = entityDefinitionRepository.save(extension);

        assertThatThrownBy(
                        () -> entityInstanceService.createInstance(savedExtension, "test-pack", 1, 999_999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999999");
    }

    @Test
    void creatingAnExtensionInstanceWithoutAnAnchorInstanceIdFailsClearly() {
        String uniqueSuffix = UUID.randomUUID().toString();
        EntityDefinition anchor = new EntityDefinition("EIS_ANCHOR3_" + uniqueSuffix, "EIS Anchor 3", 1);
        EntityDefinition savedAnchor = entityDefinitionRepository.save(anchor);
        EntityDefinition extension = new EntityDefinition("EIS_EXTENSION3_" + uniqueSuffix, "EIS Extension 3", 1);
        extension.assignRole(EntityRole.EXTENSION);
        extension.assignExtendsEntityDefinition(savedAnchor);
        EntityDefinition savedExtension = entityDefinitionRepository.save(extension);

        assertThatThrownBy(() -> entityInstanceService.createInstance(savedExtension, "test-pack", 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(savedExtension.getCode())
                .hasMessageContaining("anchor");
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
