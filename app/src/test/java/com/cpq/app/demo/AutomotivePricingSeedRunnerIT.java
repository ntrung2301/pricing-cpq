package com.cpq.app.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.pricing.PriceListItemRepository;
import com.cpq.app.pricing.PriceListRepository;
import com.cpq.app.pricing.PricingCalculationServiceImpl;
import com.cpq.app.pricing.RuleSetRepository;
import com.cpq.core.api.pricing.PriceCalculationRequest;
import com.cpq.core.api.pricing.PriceCalculationResponse;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.pricing.persistence.PriceListEntity;
import com.cpq.core.pricing.persistence.RuleSetEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T13-prereq (sprint-2.md) Definition of Done: real {@code price_list}/
 * {@code rule_set} exist at app startup, so {@code POST
 * /api/pricing/calculate} never 404s against a Product seeded by S2-T6 —
 * unlike {@code PricingCalculationServiceIT}/{@code
 * TierBasedPricingServiceIT} (S2-T10/S2-T12), which only ever create
 * {@code price_list}/{@code rule_set} rows *inside* a test transaction
 * (rolled back, never present when the app actually runs).
 *
 * <p>{@link AutomotivePricingSeedRunner} is a genuine {@code
 * ApplicationRunner} bean, so Spring Boot's real context startup invokes
 * it automatically (after {@link DemoDataSeedRunner}, which it calls
 * itself first for the same ordering reason D-026/D-029 already
 * documented) — this test never calls any seeding method directly except
 * to simulate a restart.
 */
@Testcontainers
@SpringBootTest
class AutomotivePricingSeedRunnerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AutomotivePricingSeedRunner automotivePricingSeedRunner;
    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceRepository entityInstanceRepository;
    @Autowired private PriceListRepository priceListRepository;
    @Autowired private PriceListItemRepository priceListItemRepository;
    @Autowired private RuleSetRepository ruleSetRepository;
    @Autowired private PricingCalculationServiceImpl pricingCalculationService;

    @BeforeEach
    void authenticateAsSales() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("test-sales-rep", null, "ROLE_SALES"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void seedsOnePriceListWithAnItemForEveryProductAtDifferentAmounts() {
        PriceListEntity priceList =
                priceListRepository.findByCode(AutomotivePricingSeedRunner.PRICE_LIST_CODE).orElseThrow();
        EntityDefinition productDefinition =
                entityDefinitionRepository
                        .findByCodeWithAttributes(DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE)
                        .orElseThrow();
        List<EntityInstance> products =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc(
                        DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE);
        assertThat(products).hasSize(30);

        Set<BigDecimal> distinctAmounts =
                products.stream()
                        .map(
                                product ->
                                        priceListItemRepository
                                                .findInstanceSpecific(
                                                        priceList.getId(), productDefinition.getId(), product.getId())
                                                .orElseThrow()
                                                .getAmount())
                        .collect(Collectors.toSet());

        // Every seeded Product has its own price_list_item row, and the
        // amounts are not all identical (demo waterfall UI needs to show
        // a difference across SKUs).
        assertThat(distinctAmounts).hasSizeGreaterThan(1);
        distinctAmounts.forEach(amount -> assertThat(amount).isGreaterThan(BigDecimal.ZERO));
    }

    @Test
    void seedsRuleSetAndCalculatingRealProductPriceNeverThrowsNotFound() {
        EntityDefinition productDefinition =
                entityDefinitionRepository
                        .findByCodeWithAttributes(DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE)
                        .orElseThrow();
        EntityInstance brakePad =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey(
                                DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE, "BRK-PAD-NEW-001")
                        .orElseThrow();

        PriceCalculationRequest request =
                new PriceCalculationRequest(
                        AutomotivePricingSeedRunner.RULE_SET_CODE,
                        AutomotivePricingSeedRunner.PRICE_LIST_CODE,
                        productDefinition.getId(),
                        brakePad.getId(),
                        BigDecimal.ONE,
                        Map.of());

        PriceCalculationResponse response = pricingCalculationService.calculate(request);

        assertThat(response.finalAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.steps()).isNotEmpty();
    }

    @Test
    void oemCustomerTierDiscountAppliesOnTopOfRealProductPrice() {
        EntityDefinition productDefinition =
                entityDefinitionRepository
                        .findByCodeWithAttributes(DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE)
                        .orElseThrow();
        EntityInstance brakePad =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey(
                                DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE, "BRK-PAD-NEW-001")
                        .orElseThrow();
        EntityInstance oemCustomer =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey(
                                DemoDataSeedRunner.CUSTOMER_ENTITY_DEF_CODE, "CUST-OEM-001")
                        .orElseThrow();
        PriceListEntity priceList =
                priceListRepository.findByCode(AutomotivePricingSeedRunner.PRICE_LIST_CODE).orElseThrow();
        BigDecimal listPrice =
                priceListItemRepository
                        .findInstanceSpecific(priceList.getId(), productDefinition.getId(), brakePad.getId())
                        .orElseThrow()
                        .getAmount();

        PriceCalculationResponse response =
                pricingCalculationService.calculate(
                        new PriceCalculationRequest(
                                AutomotivePricingSeedRunner.RULE_SET_CODE,
                                AutomotivePricingSeedRunner.PRICE_LIST_CODE,
                                productDefinition.getId(),
                                brakePad.getId(),
                                BigDecimal.ONE,
                                Map.of(),
                                oemCustomer.getId(),
                                null));

        // OEM tier rule is -15%.
        assertThat(response.finalAmount())
                .isEqualByComparingTo(listPrice.multiply(new BigDecimal("0.85")));
    }

    @Test
    void runningSeedAgainSimulatingRestartIsIdempotentAndDoesNotCrash() throws Exception {
        long priceListCountBefore = priceListRepository.count();
        long ruleSetCountBefore = ruleSetRepository.count();
        RuleSetEntity ruleSetBefore =
                ruleSetRepository.findByCodeWithRules(AutomotivePricingSeedRunner.RULE_SET_CODE).orElseThrow();
        int rulesBefore = ruleSetBefore.getRules().size();

        assertThatCode(() -> automotivePricingSeedRunner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        assertThat(priceListRepository.count()).isEqualTo(priceListCountBefore);
        assertThat(ruleSetRepository.count()).isEqualTo(ruleSetCountBefore);
        assertThat(
                        ruleSetRepository
                                .findByCodeWithRules(AutomotivePricingSeedRunner.RULE_SET_CODE)
                                .orElseThrow()
                                .getRules())
                .hasSize(rulesBefore);
    }
}
