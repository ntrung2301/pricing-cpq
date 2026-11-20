package com.cpq.app.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityRole;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S3-T1 Definition of Done (sprint-3.md, E8-01, ADR-009 mục 1,
 * D-037/D-038/D-039): the {@code role=EXTENSION}/{@code extends}/{@code
 * dimension_attributes} mechanism works end-to-end (pack.yaml → {@code
 * PackLoaderService} → {@code EntityInstanceService}) with a deliberately
 * non-automotive, generic pack fixture — "Gadget" as the anchor-like entity
 * being extended, "GadgetCostHistory" as the {@code EXTENSION} — to prove
 * the mechanism is generic (CLAUDE.md "Ranh giới Core vs Domain"): nothing
 * here is Product/Customer/sku/customerId.
 */
@Testcontainers
@SpringBootTest
class ExtensionEntityTaxonomyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private PackLoaderService packLoaderService;
    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceService entityInstanceService;
    @Autowired private EntityInstanceRepository entityInstanceRepository;

    private static final String PACK_ID = "extension-taxonomy-test-pack";

    private static final String PACK_YAML =
            """
            pack_id: extension-taxonomy-test-pack
            version: 1
            entities:
              - code: Gadget
                label: Gadget
                key_attribute: gadget_code
                label_attribute: name
                attributes:
                  - code: gadget_code
                    label: Gadget Code
                    data_type: STRING
                    required: true
                  - code: name
                    label: Name
                    data_type: STRING
                    required: true
              - code: GadgetCostHistory
                label: Gadget Cost History
                role: EXTENSION
                extends: Gadget
                dimension_attributes: [period]
                attributes:
                  - code: period
                    label: Period
                    data_type: STRING
                    required: true
                  - code: cost
                    label: Cost
                    data_type: DECIMAL
                    required: true
            """;

    @Test
    void loadsExtensionEntityWithExtendsPointerAndDimensionAttribute() {
        packLoaderService.loadPack(PACK_YAML);

        EntityDefinition gadget = entityDefinitionRepository.findByCodeWithAttributes("Gadget").orElseThrow();
        EntityDefinition history =
                entityDefinitionRepository.findByCodeWithAttributes("GadgetCostHistory").orElseThrow();

        assertThat(history.getRole()).isEqualTo(EntityRole.EXTENSION);
        assertThat(history.getExtendsEntityDefinition().getId()).isEqualTo(gadget.getId());
    }

    @Test
    void createsExtensionInstancesWithComposedBusinessKeyNotCollidingAcrossAnchorsOrDimensionValues() {
        packLoaderService.loadPack(PACK_YAML);
        EntityDefinition gadget = entityDefinitionRepository.findByCodeWithAttributes("Gadget").orElseThrow();
        EntityDefinition history =
                entityDefinitionRepository.findByCodeWithAttributes("GadgetCostHistory").orElseThrow();
        AttributeDefinition gadgetCode = attrByCode(gadget, "gadget_code");
        AttributeDefinition period = attrByCode(history, "period");
        AttributeDefinition cost = attrByCode(history, "cost");

        EntityInstance gadgetA = entityInstanceService.createInstance(gadget, PACK_ID, 1);
        entityInstanceService.writeAttributeValue(gadgetA, gadgetCode, AttributeValue.ofString("GDG-A"));
        EntityInstance gadgetB = entityInstanceService.createInstance(gadget, PACK_ID, 1);
        entityInstanceService.writeAttributeValue(gadgetB, gadgetCode, AttributeValue.ofString("GDG-B"));

        EntityInstance histA1 = entityInstanceService.createInstance(history, PACK_ID, 1, gadgetA.getId());
        entityInstanceService.writeAttributeValue(histA1, cost, AttributeValue.ofNumber(new BigDecimal("10.00")));
        entityInstanceService.writeAttributeValue(histA1, period, AttributeValue.ofString("2026-01"));

        EntityInstance histA2 = entityInstanceService.createInstance(history, PACK_ID, 1, gadgetA.getId());
        entityInstanceService.writeAttributeValue(histA2, cost, AttributeValue.ofNumber(new BigDecimal("11.00")));
        entityInstanceService.writeAttributeValue(histA2, period, AttributeValue.ofString("2026-02"));

        EntityInstance histB1 = entityInstanceService.createInstance(history, PACK_ID, 1, gadgetB.getId());
        entityInstanceService.writeAttributeValue(histB1, cost, AttributeValue.ofNumber(new BigDecimal("12.00")));
        entityInstanceService.writeAttributeValue(histB1, period, AttributeValue.ofString("2026-01"));

        EntityInstance reloadedA1 = entityInstanceRepository.findById(histA1.getId()).orElseThrow();
        EntityInstance reloadedA2 = entityInstanceRepository.findById(histA2.getId()).orElseThrow();
        EntityInstance reloadedB1 = entityInstanceRepository.findById(histB1.getId()).orElseThrow();

        assertThat(reloadedA1.getAnchorInstance().getId()).isEqualTo(gadgetA.getId());
        assertThat(reloadedA1.getBusinessKey()).isEqualTo("GDG-A|2026-01");
        assertThat(reloadedA2.getBusinessKey()).isEqualTo("GDG-A|2026-02");
        assertThat(reloadedB1.getBusinessKey()).isEqualTo("GDG-B|2026-01");

        // Same anchor, different period -> no collision.
        assertThat(reloadedA1.getBusinessKey()).isNotEqualTo(reloadedA2.getBusinessKey());
        // Different anchor, same period -> no collision.
        assertThat(reloadedA1.getBusinessKey()).isNotEqualTo(reloadedB1.getBusinessKey());
    }

    @Test
    void creatingAnExtensionInstanceWithoutAnAnchorInstanceIdFailsClearly() {
        packLoaderService.loadPack(PACK_YAML);
        EntityDefinition history =
                entityDefinitionRepository.findByCodeWithAttributes("GadgetCostHistory").orElseThrow();

        assertThatThrownBy(() -> entityInstanceService.createInstance(history, PACK_ID, 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GadgetCostHistory")
                .hasMessageContaining("anchor");
    }

    private static AttributeDefinition attrByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
