package com.cpq.app.demo;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.pricing.PriceListItemRepository;
import com.cpq.app.pricing.PriceListRepository;
import com.cpq.app.pricing.RuleSetRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a real {@code price_list} + {@code rule_set} for automotive demo
 * pricing (S2-T13-prereq, sprint-2.md) at application startup, so {@code
 * POST /api/pricing/calculate} has something real to find against the 30
 * {@code Product} entity_instances {@link DemoDataSeedRunner} (S2-T6)
 * already seeds.
 *
 * <p><b>Why this task exists:</b> {@code PricingCalculationServiceIT}/
 * {@code TierBasedPricingServiceIT} (S2-T10/S2-T12) each construct their
 * own {@code price_list}/{@code rule_set} rows directly via {@code
 * RuleSetRepository}/{@code PriceListRepository} — but those rows only
 * ever exist inside a test's transaction (rolled back at the end), never
 * in a real running app. Without this runner, the app's own {@code
 * POST /api/pricing/calculate} endpoint (same one S2-T13's UI will call)
 * would 404 with {@code PricingDataNotFoundException} the first time
 * anyone tried it against real data — this is the gap this task closes,
 * found before handing S2-T13 (UI) to frontend-dev.
 *
 * <ol>
 *   <li>{@code price_list} code {@value #PRICE_LIST_CODE}: one {@code
 *       price_list_item} per already-seeded {@code Product} instance
 *       (instance-specific rows, not one shared segment-level row —
 *       ADR-003 — so the demo waterfall UI can show a different price
 *       per SKU). Amount is a simple deterministic formula ({@code
 *       50.00 + 5.00 * CSV row index}), not a real price — good enough
 *       for a demo, always &gt; 0, always different across SKUs.
 *   <li>{@code rule_set} code {@value #RULE_SET_CODE}: 4 JSON DSL rules
 *       at stage {@code segment-adjustment}, one per {@code Customer}
 *       tier value the automotive {@code pack.yaml} defines (OEM/
 *       DISTRIBUTOR/DEALER/WORKSHOP — same {@code attributes.tier}
 *       condition pattern {@code TierBasedPricingServiceIT} (S2-T12/
 *       D-028) already used), plus 1 Groovy SCRIPT rule at stage {@code
 *       override} reading {@code attributes.contractOverridePrice} —
 *       same mandatory-SCRIPT reasoning D-028 recorded (DSL's {@code
 *       set-price} only accepts a literal, not a per-request variable).
 * </ol>
 *
 * <p><b>Currency:</b> {@code USD}, matching every other demo {@code
 * price_list} created in tests so far ({@code PricingCalculationServiceIT}/
 * {@code TierBasedPricingServiceIT}) — no real-money/VND requirement
 * exists yet for this demo, so staying consistent with existing test
 * fixtures was preferred over introducing a second currency convention
 * (NEEDS-PO-REVIEW if a later task wants VND).
 *
 * <p><b>Ordering:</b> same strategy {@code AutomotiveFitmentSeedRunner}
 * (D-029) already chose — rather than rely on Spring Boot's unspecified
 * invocation order between {@code ApplicationRunner} beans, this runner
 * calls {@link DemoDataSeedRunner#run} itself first (idempotent, so a
 * harmless no-op if Spring already ran it). This runner only needs
 * {@code Product} entity_instances (S2-T6), not the fitment/supersession
 * data {@code AutomotiveFitmentSeedRunner} adds, so it depends on {@code
 * DemoDataSeedRunner} directly rather than chaining through that runner
 * too.
 *
 * <p><b>Idempotency on restart:</b> same "any row present = fully
 * seeded" trade-off {@code DemoDataSeedRunner} (D-026 point 4) and {@code
 * AutomotiveFitmentSeedRunner} (D-029) already accepted — if the {@code
 * price_list} (respectively {@code rule_set}) code already exists, that
 * whole seeding step is skipped.
 */
@Component
public class AutomotivePricingSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AutomotivePricingSeedRunner.class);

    static final String PRICE_LIST_CODE = "AUTOMOTIVE-DEFAULT";
    static final String RULE_SET_CODE = "AUTOMOTIVE-STANDARD";

    private static final String PRICE_LIST_CURRENCY = "USD";
    private static final BigDecimal BASE_AMOUNT = new BigDecimal("50.00");
    private static final BigDecimal STEP_AMOUNT = new BigDecimal("5.00");

    private final DemoDataSeedRunner demoDataSeedRunner;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final EntityInstanceRepository entityInstanceRepository;
    private final PriceListRepository priceListRepository;
    private final PriceListItemRepository priceListItemRepository;
    private final RuleSetRepository ruleSetRepository;

    public AutomotivePricingSeedRunner(
            DemoDataSeedRunner demoDataSeedRunner,
            EntityDefinitionRepository entityDefinitionRepository,
            EntityInstanceRepository entityInstanceRepository,
            PriceListRepository priceListRepository,
            PriceListItemRepository priceListItemRepository,
            RuleSetRepository ruleSetRepository) {
        this.demoDataSeedRunner = demoDataSeedRunner;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.entityInstanceRepository = entityInstanceRepository;
        this.priceListRepository = priceListRepository;
        this.priceListItemRepository = priceListItemRepository;
        this.ruleSetRepository = ruleSetRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        demoDataSeedRunner.run(args);

        seedPriceListIfAbsent();
        seedRuleSetIfAbsent();
    }

    private void seedPriceListIfAbsent() {
        if (priceListRepository.findByCode(PRICE_LIST_CODE).isPresent()) {
            log.info(
                    "price_list '{}' already present — skipping seed (idempotent restart, S2-T13-prereq)",
                    PRICE_LIST_CODE);
            return;
        }

        EntityDefinition productDefinition =
                entityDefinitionRepository
                        .findByCodeWithAttributes(DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "entity_def '"
                                                        + DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE
                                                        + "' not found — DemoDataSeedRunner should have seeded it"
                                                        + " already (S2-T6)"));
        List<EntityInstance> products =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc(
                        DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE);

        PriceListEntity priceList =
                priceListRepository.save(
                        new PriceListEntity(PRICE_LIST_CODE, "Automotive Default Price List", PRICE_LIST_CURRENCY));

        int index = 0;
        for (EntityInstance product : products) {
            BigDecimal amount = BASE_AMOUNT.add(STEP_AMOUNT.multiply(BigDecimal.valueOf(index)));
            priceListItemRepository.save(
                    new PriceListItemEntity(priceList, productDefinition.getId(), product.getId(), amount, null, null));
            index++;
        }
        log.info("Seeded price_list '{}' with {} price_list_item row(s)", PRICE_LIST_CODE, products.size());
    }

    private void seedRuleSetIfAbsent() {
        if (ruleSetRepository.findByCodeWithRules(RULE_SET_CODE).isPresent()) {
            log.info(
                    "rule_set '{}' already present — skipping seed (idempotent restart, S2-T13-prereq)",
                    RULE_SET_CODE);
            return;
        }

        RuleSetEntity ruleSet = new RuleSetEntity(RULE_SET_CODE, 1);
        ruleSet.addRule(tierDiscountRule("OEM", -15));
        ruleSet.addRule(tierDiscountRule("DISTRIBUTOR", -10));
        ruleSet.addRule(tierDiscountRule("DEALER", -5));
        ruleSet.addRule(tierDiscountRule("WORKSHOP", 0));
        ruleSet.addRule(contractOverrideRule());
        ruleSetRepository.save(ruleSet);
        log.info("Seeded rule_set '{}' with {} rule(s)", RULE_SET_CODE, ruleSet.getRules().size());
    }

    /**
     * One {@code segment-adjustment} DSL rule per {@code Customer.tier}
     * value defined in the automotive {@code pack.yaml} (OEM/DISTRIBUTOR/
     * DEALER/WORKSHOP) — same {@code {"var": "tier"}} condition shape
     * {@code TierBasedPricingServiceIT} (D-028) used, just with 4 tiers
     * instead of 3 and real percentages for this demo (OEM -15%,
     * DISTRIBUTOR -10%, DEALER -5%, WORKSHOP list price).
     */
    private static RuleEntity tierDiscountRule(String tier, int percent) {
        String label = tier + " tier discount";
        String definition =
                """
                {
                  "conditions": {"==": [{"var": "tier"}, "%s"]},
                  "action": {"type": "percent-discount", "value": %d, "label": "%s"}
                }
                """
                        .formatted(tier, percent, label);
        return new RuleEntity(PricingStage.SEGMENT_ADJUSTMENT, 1, RuleType.DSL, definition);
    }

    /**
     * {@code override}-stage SCRIPT rule reading the dynamic {@code
     * attributes.contractOverridePrice} request value (S2-T12/D-028) —
     * must be Groovy, not DSL: {@code DslRuleExecutor}'s {@code
     * set-price} action only accepts a numeric JSON literal, it cannot
     * reference a per-request runtime variable. Label contains "contract
     * override" (lowercase), same assertion contract {@code
     * TierBasedPricingServiceIT} already established.
     */
    private static RuleEntity contractOverrideRule() {
        String script =
                "def target = attributes.contractOverridePrice\n"
                        + "if (target != null) {\n"
                        + "  return [delta: target - currentAmount, label: 'Contract override price (contract"
                        + " override)']\n"
                        + "}\n"
                        + "return null";
        return new RuleEntity(PricingStage.OVERRIDE, 1, RuleType.SCRIPT, script);
    }
}
