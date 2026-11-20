package com.cpq.app.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceAttributeValueRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S3-T4 (sprint-3.md, backlog E8-02) Definition of Done, exercised against
 * *real* seeded automotive demo data (same framing {@code
 * AutomotiveFitmentSeedRunnerIT} already used for S2-T7 — seed data/config,
 * not engine code, so using real automotive SKUs/periods here does not
 * violate the core/domain boundary): {@code CostHistory} (role {@code
 * EXTENSION} of {@code Product}) has >= 3 rows for one real SKU with a
 * correctly-composed business key, {@code MarginThreshold} (role {@code
 * PARAMETER}) has all 4 risk levels, and {@code Product.country_of_manufacture}
 * has a value for at least 2 demo SKUs.
 *
 * <p>{@link PricingTaxonomyDemoSeedRunner} is a genuine {@code
 * ApplicationRunner} bean, so Spring Boot's real context startup invokes it
 * automatically (D-041: {@code ApplicationRunner} beans run under {@code
 * @SpringBootTest} too) — this test never calls any seeding method directly
 * except to simulate a restart.
 */
@Testcontainers
@SpringBootTest
class PricingTaxonomyDemoSeedRunnerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private PricingTaxonomyDemoSeedRunner pricingTaxonomyDemoSeedRunner;
    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceRepository entityInstanceRepository;
    @Autowired private EntityInstanceAttributeValueRepository entityInstanceAttributeValueRepository;

    private static final String TARGET_SKU = "BRK-PAD-NEW-001";
    private static final String OTHER_COUNTRY_SKU = "OIL-FLT-NEW-010";

    @Test
    void seedsAtLeastThreeCostHistoryInstancesForTargetSkuWithComposedBusinessKey() {
        EntityInstance product =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey("Product", TARGET_SKU)
                        .orElseThrow();

        List<EntityInstance> costHistoryRows =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("CostHistory").stream()
                        .filter(row -> row.getAnchorInstance() != null)
                        .filter(row -> row.getAnchorInstance().getId().equals(product.getId()))
                        .toList();

        assertThat(costHistoryRows).hasSizeGreaterThanOrEqualTo(3);
        assertThat(costHistoryRows)
                .allSatisfy(
                        row -> {
                            assertThat(row.getAnchorInstance().getId()).isEqualTo(product.getId());
                            assertThat(row.getBusinessKey()).startsWith(TARGET_SKU + "|");
                        });

        List<String> businessKeys = costHistoryRows.stream().map(EntityInstance::getBusinessKey).toList();
        assertThat(businessKeys).doesNotHaveDuplicates();
    }

    @Test
    void costHistoryRowsCarryPositiveCostAndQuantityValues() {
        EntityInstance product =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey("Product", TARGET_SKU)
                        .orElseThrow();
        List<EntityInstance> costHistoryRows =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("CostHistory").stream()
                        .filter(row -> row.getAnchorInstance() != null)
                        .filter(row -> row.getAnchorInstance().getId().equals(product.getId()))
                        .toList();

        for (EntityInstance row : costHistoryRows) {
            Map<String, EntityInstanceAttributeValue> valuesByAttributeCode =
                    valuesByAttributeCode("CostHistory", row);
            BigDecimal cost = valuesByAttributeCode.get("cost").getValueNumber();
            BigDecimal quantity = valuesByAttributeCode.get("quantity").getValueNumber();
            assertThat(cost).isGreaterThan(BigDecimal.ZERO);
            assertThat(quantity).isGreaterThan(BigDecimal.ZERO);
            assertThat(valuesByAttributeCode.get("period").getValueString()).isNotBlank();
        }
    }

    @Test
    void seedsFourMarginThresholdLevelsWithExpectedRanges() {
        List<EntityInstance> levels =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("MarginThreshold");
        assertThat(levels).hasSize(4);

        Map<String, EntityInstance> byLevel =
                levels.stream().collect(Collectors.toMap(EntityInstance::getBusinessKey, Function.identity()));
        assertThat(byLevel.keySet()).containsExactlyInAnyOrder("CRITICAL", "RED", "YELLOW", "GREEN");

        assertThat(numberOf("MarginThreshold", byLevel.get("CRITICAL"), "min_margin_pct"))
                .isEqualByComparingTo("0");
        assertThat(numberOf("MarginThreshold", byLevel.get("CRITICAL"), "max_margin_pct"))
                .isEqualByComparingTo("15");

        assertThat(numberOf("MarginThreshold", byLevel.get("RED"), "min_margin_pct"))
                .isEqualByComparingTo("15");
        assertThat(numberOf("MarginThreshold", byLevel.get("RED"), "max_margin_pct"))
                .isEqualByComparingTo("20");

        assertThat(numberOf("MarginThreshold", byLevel.get("YELLOW"), "min_margin_pct"))
                .isEqualByComparingTo("20");
        assertThat(numberOf("MarginThreshold", byLevel.get("YELLOW"), "max_margin_pct"))
                .isEqualByComparingTo("30");

        assertThat(numberOf("MarginThreshold", byLevel.get("GREEN"), "min_margin_pct"))
                .isEqualByComparingTo("30");
        assertThat(valuesByAttributeCode("MarginThreshold", byLevel.get("GREEN")).get("max_margin_pct"))
                .isNull();
    }

    @Test
    void countryOfManufactureHasValueForAtLeastTwoDemoSkus() {
        EntityInstance brakePad =
                entityInstanceRepository.findByEntityDefinition_CodeAndBusinessKey("Product", TARGET_SKU).orElseThrow();
        EntityInstance oilFilter =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey("Product", OTHER_COUNTRY_SKU)
                        .orElseThrow();

        assertThat(valuesByAttributeCode("Product", brakePad).get("country_of_manufacture").getValueString())
                .isEqualTo("Vietnam");
        assertThat(valuesByAttributeCode("Product", oilFilter).get("country_of_manufacture").getValueString())
                .isEqualTo("Taiwan");
    }

    @Test
    void runningSeedAgainSimulatingRestartIsIdempotentAndDoesNotCrash() {
        int costHistoryBefore =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("CostHistory").size();
        int marginThresholdBefore =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("MarginThreshold").size();

        assertThatCode(() -> pricingTaxonomyDemoSeedRunner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        assertThat(entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("CostHistory"))
                .hasSize(costHistoryBefore);
        assertThat(entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("MarginThreshold"))
                .hasSize(marginThresholdBefore);
    }

    /**
     * Maps attribute code -> value for {@code instance}, resolving each
     * row's {@code attribute_def_id} against a fresh, eagerly-fetched
     * {@code entityDefCode} definition rather than calling {@code
     * getAttributeDefinition().getCode()} directly on the repository
     * result — {@code AttributeDefinition} is a {@code LAZY} association
     * there, and each Spring Data repository call runs in its own
     * transaction/session (no open-session-in-view in this test), so
     * touching anything but the already-known foreign key id on that
     * proxy throws {@code LazyInitializationException} once the session
     * that loaded it has closed.
     */
    private Map<String, EntityInstanceAttributeValue> valuesByAttributeCode(
            String entityDefCode, EntityInstance instance) {
        EntityDefinition entityDefinition =
                entityDefinitionRepository.findByCodeWithAttributes(entityDefCode).orElseThrow();
        Map<Long, String> codeByAttributeId =
                entityDefinition.getAttributes().stream()
                        .collect(Collectors.toMap(AttributeDefinition::getId, AttributeDefinition::getCode));

        return entityInstanceAttributeValueRepository.findByEntityInstance(instance).stream()
                .collect(
                        Collectors.toMap(
                                value -> codeByAttributeId.get(value.getAttributeDefinition().getId()),
                                Function.identity()));
    }

    private BigDecimal numberOf(String entityDefCode, EntityInstance instance, String attributeCode) {
        return valuesByAttributeCode(entityDefCode, instance).get(attributeCode).getValueNumber();
    }
}
