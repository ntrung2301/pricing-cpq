package com.cpq.app.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.api.pricing.PriceCalculationRequest;
import com.cpq.core.api.pricing.PriceCalculationResponse;
import com.cpq.core.api.pricing.WaterfallStepSummary;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
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
 * S2-T12/E3-04 Definition of Done (sprint-2.md): "Cùng Product, Customer
 * khác tier → giá khác đúng theo rule; Customer có hợp đồng riêng → giá
 * hợp đồng override tier, thấy đúng nhãn trong waterfall." Mirrors
 * {@code PricingCalculationServiceIT}'s Testcontainers pattern, adding a
 * made-up "PIT_CUSTOMER_*" entity definition with a {@code tier} attribute
 * (deliberately not named/tagged "Customer" from E1-05's real
 * {@code pack.yaml} — the point of this test is the generic tier-reading
 * glue in {@link PricingCalculationServiceImpl}, independent of which
 * entity_def happens to own a "tier" attribute).
 */
@Testcontainers
@SpringBootTest
class TierBasedPricingServiceIT {

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
    @Autowired private RuleSetRepository ruleSetRepository;
    @Autowired private PriceListRepository priceListRepository;
    @Autowired private PriceListItemRepository priceListItemRepository;
    @Autowired private PricingCalculationServiceImpl pricingCalculationService;

    private EntityDefinition defineProductEntity() {
        EntityDefinition definition = new EntityDefinition("PIT_PRODUCT_" + UUID.randomUUID(), "PIT Product", 1);
        return entityDefinitionRepository.save(definition);
    }

    private EntityDefinition defineCustomerEntityWithTier() {
        EntityDefinition definition =
                new EntityDefinition("PIT_CUSTOMER_" + UUID.randomUUID(), "PIT Customer", 1);
        definition.addAttribute(new AttributeDefinition("tier", "Tier", AttributeDataType.ENUM, true, null));
        return entityDefinitionRepository.save(definition);
    }

    private static AttributeDefinition tierAttribute(EntityDefinition customerDef) {
        return customerDef.getAttributes().stream()
                .filter(attribute -> "tier".equals(attribute.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private EntityInstance createCustomer(EntityDefinition customerDef, String tierValue) {
        EntityInstance customer = entityInstanceService.createInstance(customerDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(
                customer, tierAttribute(customerDef), AttributeValue.ofString(tierValue));
        return customer;
    }

    /**
     * Tier discount rules at {@code segment-adjustment} (E3-04: "OEM giảm
     * X%, Distributor giảm Y%, Dealer/Workshop giá niêm yết") plus a
     * {@code override}-stage SCRIPT rule reading a dynamic
     * {@code contractOverridePrice} attribute. The override rule must be
     * SCRIPT, not DSL: {@code DslRuleExecutor}'s {@code set-price} action
     * only accepts a numeric JSON literal for {@code value}
     * (see {@code decimalField}), it cannot reference a variable — so a
     * per-request override amount can only be expressed with a Groovy rule
     * that reads {@code attributes.contractOverridePrice} at runtime
     * (documented as D-028 in docs/decisions.md).
     */
    private RuleSetEntity defineTierAndContractRuleSet() {
        RuleSetEntity ruleSet = new RuleSetEntity("PIT_TIER_RULESET_" + UUID.randomUUID(), 1);
        ruleSet.addRule(
                new RuleEntity(
                        PricingStage.SEGMENT_ADJUSTMENT,
                        1,
                        RuleType.DSL,
                        """
                        {
                          "conditions": {"==": [{"var": "tier"}, "OEM"]},
                          "action": {"type": "percent-discount", "value": -15, "label": "OEM tier discount"}
                        }
                        """));
        ruleSet.addRule(
                new RuleEntity(
                        PricingStage.SEGMENT_ADJUSTMENT,
                        1,
                        RuleType.DSL,
                        """
                        {
                          "conditions": {"==": [{"var": "tier"}, "DISTRIBUTOR"]},
                          "action": {"type": "percent-discount", "value": -10, "label": "Distributor tier discount"}
                        }
                        """));
        ruleSet.addRule(
                new RuleEntity(
                        PricingStage.SEGMENT_ADJUSTMENT,
                        1,
                        RuleType.DSL,
                        """
                        {
                          "conditions": {"==": [{"var": "tier"}, "DEALER"]},
                          "action": {"type": "percent-discount", "value": 0, "label": "Dealer/Workshop list price"}
                        }
                        """));
        ruleSet.addRule(
                new RuleEntity(
                        PricingStage.OVERRIDE,
                        1,
                        RuleType.SCRIPT,
                        "def target = attributes.contractOverridePrice\n"
                                + "if (target != null) {\n"
                                + "  return [delta: target - currentAmount, label: 'Contract override price (contract override)']\n"
                                + "}\n"
                                + "return null"));
        return ruleSetRepository.save(ruleSet);
    }

    private PriceListEntity definePriceList() {
        return priceListRepository.save(
                new PriceListEntity("PIT_TIER_PRICELIST_" + UUID.randomUUID(), "PIT Tier Price List", "USD"));
    }

    @Test
    void sameProductDifferentCustomerTierProducesDifferentFinalPrice() {
        EntityDefinition productDef = defineProductEntity();
        EntityDefinition customerDef = defineCustomerEntityWithTier();
        EntityInstance oemCustomer = createCustomer(customerDef, "OEM");
        EntityInstance dealerCustomer = createCustomer(customerDef, "DEALER");
        RuleSetEntity ruleSet = defineTierAndContractRuleSet();
        PriceListEntity priceList = definePriceList();
        priceListItemRepository.save(
                new PriceListItemEntity(priceList, productDef.getId(), null, new BigDecimal("100.00"), null, null));

        PriceCalculationResponse oemResponse =
                pricingCalculationService.calculate(
                        new PriceCalculationRequest(
                                ruleSet.getCode(),
                                priceList.getCode(),
                                productDef.getId(),
                                null,
                                BigDecimal.ONE,
                                Map.of(),
                                oemCustomer.getId(),
                                null));
        PriceCalculationResponse dealerResponse =
                pricingCalculationService.calculate(
                        new PriceCalculationRequest(
                                ruleSet.getCode(),
                                priceList.getCode(),
                                productDef.getId(),
                                null,
                                BigDecimal.ONE,
                                Map.of(),
                                dealerCustomer.getId(),
                                null));

        assertThat(oemResponse.finalAmount()).isEqualByComparingTo("85.00");
        assertThat(dealerResponse.finalAmount()).isEqualByComparingTo("100.00");
        assertThat(oemResponse.finalAmount()).isNotEqualByComparingTo(dealerResponse.finalAmount());
    }

    @Test
    void customerWithContractOverridePriceOverridesTierDiscountAndLabelsItInWaterfall() {
        EntityDefinition productDef = defineProductEntity();
        EntityDefinition customerDef = defineCustomerEntityWithTier();
        EntityInstance oemCustomer = createCustomer(customerDef, "OEM");
        RuleSetEntity ruleSet = defineTierAndContractRuleSet();
        PriceListEntity priceList = definePriceList();
        priceListItemRepository.save(
                new PriceListItemEntity(priceList, productDef.getId(), null, new BigDecimal("100.00"), null, null));

        PriceCalculationRequest request =
                new PriceCalculationRequest(
                        ruleSet.getCode(),
                        priceList.getCode(),
                        productDef.getId(),
                        null,
                        BigDecimal.ONE,
                        Map.of(),
                        oemCustomer.getId(),
                        new BigDecimal("77.77"));

        PriceCalculationResponse response = pricingCalculationService.calculate(request);

        // OEM discount (-15%) would otherwise land on 85.00, but the
        // contract override must win: final price is driven entirely by
        // contractOverridePrice, not the tier rule.
        assertThat(response.finalAmount()).isEqualByComparingTo("77.77");

        List<WaterfallStepSummary> steps = response.steps();
        assertThat(steps)
                .anySatisfy(step -> assertThat(step.label()).contains("contract override"));

        BigDecimal sumOfDeltas =
                steps.stream().map(WaterfallStepSummary::deltaAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfDeltas).isEqualByComparingTo(response.finalAmount());
    }
}
