package com.cpq.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.api.catalog.EntityInstancePageDto;
import com.cpq.core.api.catalog.EntityInstanceQueryService;
import com.cpq.core.api.catalog.EntityInstanceSummary;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import java.math.BigDecimal;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T9-prereq (sprint-2.md, see docs/decisions.md D-030): {@code GET
 * /api/catalog/{entityDefCode}/instances} must list real {@code
 * entity_instance} rows (not entity_def metadata, unlike {@code GET
 * /api/entities} S1-T8), paginated, with their full attribute value map.
 * Exercised here at the service layer against a real Testcontainers
 * Postgres — same pattern as {@code ConfiguratorServiceImplIT} (S2-T8).
 * Deliberately uses a made-up, non-automotive entity ("Gadget") for the
 * unit-shaped assertions — {@link EntityInstanceQueryServiceImpl} must have
 * no idea whether an entity_def is "Vehicle"/"Product" or anything else.
 * One additional test reads the real automotive {@code Vehicle} data seeded
 * by {@code AutomotiveFitmentSeedRunner} (S2-T7) end-to-end.
 */
@Testcontainers
@SpringBootTest
class EntityInstanceQueryServiceImplIT {

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
    @Autowired private EntityInstanceQueryService entityInstanceQueryService;

    private EntityDefinition defineGadget(String namePrefix) {
        String uniqueCode = namePrefix + "_" + UUID.randomUUID();
        AttributeDefinition sku =
                new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        AttributeDefinition weight =
                new AttributeDefinition("weight", "Weight", AttributeDataType.DECIMAL, false, null);
        EntityDefinition entityDefinition = new EntityDefinition(uniqueCode, namePrefix, 1);
        entityDefinition.addAttribute(sku);
        entityDefinition.addAttribute(weight);
        entityDefinition.designateKeyAttribute(sku);
        entityDefinition.designateLabelAttribute(sku);
        return entityDefinitionRepository.save(entityDefinition);
    }

    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private EntityInstance createGadgetInstance(
            EntityDefinition definition, AttributeDefinition skuAttribute, AttributeDefinition weightAttribute,
            String sku, String weight) {
        EntityInstance instance = entityInstanceService.createInstance(definition, "test-pack", 1);
        entityInstanceService.writeAttributeValue(instance, skuAttribute, AttributeValue.ofString(sku));
        entityInstanceService.writeAttributeValue(
                instance, weightAttribute, AttributeValue.ofNumber(new BigDecimal(weight)));
        return instance;
    }

    @Test
    void listsAllInstancesOfOneEntityDefWithBusinessKeyDisplayLabelAndAttributeMap() {
        EntityDefinition gadgetDef = defineGadget("Gadget");
        AttributeDefinition sku = findByCode(gadgetDef, "sku");
        AttributeDefinition weight = findByCode(gadgetDef, "weight");
        createGadgetInstance(gadgetDef, sku, weight, "GAD-001", "1.5");
        createGadgetInstance(gadgetDef, sku, weight, "GAD-002", "2.5");

        EntityInstancePageDto page = entityInstanceQueryService.listInstances(gadgetDef.getCode(), 0, 50, null);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).hasSize(2);
        EntityInstanceSummary first = page.content().get(0);
        assertThat(first.businessKey()).isEqualTo("GAD-001");
        assertThat(first.displayLabel()).isEqualTo("GAD-001");
        assertThat(first.attributes()).containsEntry("sku", "GAD-001");
        assertThat((BigDecimal) first.attributes().get("weight"))
                .isEqualByComparingTo(new BigDecimal("1.5"));
    }

    @Test
    void pagingReturnsRemainingItemsOnSecondPage() {
        EntityDefinition gadgetDef = defineGadget("PagedGadget");
        AttributeDefinition sku = findByCode(gadgetDef, "sku");
        AttributeDefinition weight = findByCode(gadgetDef, "weight");
        for (int i = 0; i < 5; i++) {
            createGadgetInstance(gadgetDef, sku, weight, "PG-" + i, "1.0");
        }

        EntityInstancePageDto firstPage =
                entityInstanceQueryService.listInstances(gadgetDef.getCode(), 0, 3, null);
        EntityInstancePageDto secondPage =
                entityInstanceQueryService.listInstances(gadgetDef.getCode(), 1, 3, null);

        assertThat(firstPage.content()).hasSize(3);
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.content()).hasSize(2);
        assertThat(secondPage.page()).isEqualTo(1);
    }

    @Test
    void searchFiltersByBusinessKeyOrDisplayLabelCaseInsensitive() {
        EntityDefinition gadgetDef = defineGadget("SearchGadget");
        AttributeDefinition sku = findByCode(gadgetDef, "sku");
        AttributeDefinition weight = findByCode(gadgetDef, "weight");
        createGadgetInstance(gadgetDef, sku, weight, "BRAKE-PAD-1", "1.0");
        createGadgetInstance(gadgetDef, sku, weight, "ENGINE-OIL-1", "1.0");

        EntityInstancePageDto result =
                entityInstanceQueryService.listInstances(gadgetDef.getCode(), 0, 50, "brake");

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).businessKey()).isEqualTo("BRAKE-PAD-1");
    }

    @Test
    void unknownEntityDefCodeIsRejectedWith404() {
        assertThatThrownBy(
                        () -> entityInstanceQueryService.listInstances("NoSuchEntityDef", 0, 50, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NoSuchEntityDef");
    }

    @Test
    void listsRealSeededVehicleInstancesEndToEnd() {
        EntityInstancePageDto page = entityInstanceQueryService.listInstances("Vehicle", 0, 50, null);

        assertThat(page.totalElements()).isEqualTo(5);
        List<String> labels = page.content().stream().map(EntityInstanceSummary::displayLabel).toList();
        assertThat(labels).isNotEmpty();
        assertThat(page.content().get(0).attributes()).containsKey("make");
    }
}
