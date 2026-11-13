package com.cpq.app.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.api.pricing.PriceCalculationRequest;
import com.cpq.core.api.pricing.PriceCalculationResponse;
import com.cpq.core.api.pricing.WaterfallStepSummary;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.pricing.PricingStage;
import com.cpq.core.pricing.RuleType;
import com.cpq.core.pricing.persistence.PriceListEntity;
import com.cpq.core.pricing.persistence.PriceListItemEntity;
import com.cpq.core.pricing.persistence.RuleEntity;
import com.cpq.core.pricing.persistence.RuleSetEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
 * S2-T10/E3-02 Definition of Done: "API tính giá... nhận context +
 * rule_set + price_list → trả danh sách waterfall step, tổng các step =
 * giá cuối" — exercised here against a real Testcontainers Postgres
 * (migration {@code V7__pricing_rule_engine.sql}), with one DSL rule and
 * one SCRIPT rule in the same rule_set (same mandatory combination as
 * {@code com.cpq.core.pricing.HybridRuleEngineTest}, now round-tripped
 * through real persistence + the HTTP-facing DTOs).
 *
 * <p>Calls {@link PricingCalculationServiceImpl} directly (not over
 * HTTP), but {@code @PreAuthorize} on the
 * {@code PricingCalculationService} port still enforces at this layer
 * (ADR-006: "enforce ở method layer") — a {@code SALES} authority is
 * seeded into the {@link SecurityContextHolder} per test, same role
 * {@code PricingEndpointIT} uses over HTTP, so this class is exercising
 * authorization too, not bypassing it.
 */
@Testcontainers
@SpringBootTest
class PricingCalculationServiceIT {

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
    @Autowired private EntityInstanceRepository entityInstanceRepository;
    @Autowired private EntityInstanceService entityInstanceService;
    @Autowired private RuleSetRepository ruleSetRepository;
    @Autowired private PriceListRepository priceListRepository;
    @Autowired private PriceListItemRepository priceListItemRepository;
    @Autowired private PricingCalculationServiceImpl pricingCalculationService;

    private EntityDefinition defineEntity() {
        EntityDefinition definition = new EntityDefinition("PIT_WIDGET_" + UUID.randomUUID(), "PIT Widget", 1);
        return entityDefinitionRepository.save(definition);
    }

    private RuleSetEntity defineHybridRuleSet() {
        RuleSetEntity ruleSet = new RuleSetEntity("PIT_RULESET_" + UUID.randomUUID(), 1);
        ruleSet.addRule(
                new RuleEntity(
                        PricingStage.SEGMENT_ADJUSTMENT,
                        1,
                        RuleType.DSL,
                        """
                        {
                          "conditions": {"==": [{"var": "tier"}, "GOLD"]},
                          "action": {"type": "percent-discount", "value": 5, "label": "Gold tier markup"}
                        }
                        """));
        ruleSet.addRule(
                new RuleEntity(
                        PricingStage.QUANTITY_ADJUSTMENT,
                        1,
                        RuleType.SCRIPT,
                        "if (quantity >= new BigDecimal('10')) {\n"
                                + "  return [delta: new BigDecimal('-20.00'), label: 'Volume discount (script)']\n"
                                + "}\n"
                                + "return null"));
        return ruleSetRepository.save(ruleSet);
    }

    private PriceListEntity definePriceList() {
        return priceListRepository.save(new PriceListEntity("PIT_PRICELIST_" + UUID.randomUUID(), "PIT Price List", "USD"));
    }

    @Test
    void calculatesHybridWaterfallFromSegmentLevelPriceListItemWithRoundTrip() {
        EntityDefinition entityDefinition = defineEntity();
        RuleSetEntity ruleSet = defineHybridRuleSet();
        PriceListEntity priceList = definePriceList();
        priceListItemRepository.save(
                new PriceListItemEntity(
                        priceList, entityDefinition.getId(), null, new BigDecimal("100.00"), null, null));

        PriceCalculationRequest request =
                new PriceCalculationRequest(
                        ruleSet.getCode(),
                        priceList.getCode(),
                        entityDefinition.getId(),
                        null,
                        new BigDecimal("10"),
                        Map.of("tier", "GOLD"));

        PriceCalculationResponse response = pricingCalculationService.calculate(request);

        List<WaterfallStepSummary> steps = response.steps();
        assertThat(steps).extracting(WaterfallStepSummary::label)
                .containsExactly(
                        "List price", "Gold tier markup", "Volume discount (script)", "Final price");
        assertThat(steps.get(1).stage()).isEqualTo("segment-adjustment");
        assertThat(steps.get(2).stage()).isEqualTo("quantity-adjustment");

        // 100 + 5% = 105, then -20 = 85.
        assertThat(response.finalAmount()).isEqualByComparingTo("85.00");

        BigDecimal sumOfDeltas =
                steps.stream().map(WaterfallStepSummary::deltaAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfDeltas).isEqualByComparingTo(response.finalAmount());
    }

    @Test
    void instanceSpecificPriceListItemTakesPriorityOverSegmentLevel() {
        EntityDefinition entityDefinition = defineEntity();
        EntityInstance instance = entityInstanceService.createInstance(entityDefinition, "pit-pack", 1);
        RuleSetEntity ruleSet = defineHybridRuleSetWithNoRules();
        PriceListEntity priceList = definePriceList();
        priceListItemRepository.save(
                new PriceListItemEntity(
                        priceList, entityDefinition.getId(), null, new BigDecimal("100.00"), null, null));
        priceListItemRepository.save(
                new PriceListItemEntity(
                        priceList,
                        entityDefinition.getId(),
                        instance.getId(),
                        new BigDecimal("42.00"),
                        null,
                        null));

        PriceCalculationRequest request =
                new PriceCalculationRequest(
                        ruleSet.getCode(),
                        priceList.getCode(),
                        entityDefinition.getId(),
                        instance.getId(),
                        BigDecimal.ONE,
                        Map.of());

        PriceCalculationResponse response = pricingCalculationService.calculate(request);

        assertThat(response.finalAmount()).isEqualByComparingTo("42.00");
    }

    private RuleSetEntity defineHybridRuleSetWithNoRules() {
        return ruleSetRepository.save(new RuleSetEntity("PIT_EMPTY_RULESET_" + UUID.randomUUID(), 1));
    }

    @Test
    void sameRequestProducesIdenticalResponseAcrossTenCalls() {
        EntityDefinition entityDefinition = defineEntity();
        RuleSetEntity ruleSet = defineHybridRuleSet();
        PriceListEntity priceList = definePriceList();
        priceListItemRepository.save(
                new PriceListItemEntity(
                        priceList, entityDefinition.getId(), null, new BigDecimal("199.99"), null, null));
        PriceCalculationRequest request =
                new PriceCalculationRequest(
                        ruleSet.getCode(),
                        priceList.getCode(),
                        entityDefinition.getId(),
                        null,
                        new BigDecimal("15"),
                        Map.of("tier", "GOLD"));

        PriceCalculationResponse first = pricingCalculationService.calculate(request);
        for (int i = 0; i < 10; i++) {
            assertThat(pricingCalculationService.calculate(request)).isEqualTo(first);
        }
    }

    @Test
    void unknownRuleSetCodeIsReportedAsNotFoundNotA500() {
        EntityDefinition entityDefinition = defineEntity();
        PriceListEntity priceList = definePriceList();
        priceListItemRepository.save(
                new PriceListItemEntity(
                        priceList, entityDefinition.getId(), null, new BigDecimal("10.00"), null, null));

        PriceCalculationRequest request =
                new PriceCalculationRequest(
                        "does-not-exist", priceList.getCode(), entityDefinition.getId(), null, BigDecimal.ONE, Map.of());

        assertThatThrownBy(() -> pricingCalculationService.calculate(request))
                .isInstanceOf(PricingDataNotFoundException.class);
    }
}
