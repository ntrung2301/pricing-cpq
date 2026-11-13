package com.cpq.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mandatory S2-T10 acceptance test (docs/sprints/sprint-2.md): one DSL
 * rule and one Script rule in the same {@link RuleSet}, run against the
 * same {@link PricingContext}, must produce the waterfall in strict
 * pipeline order (ADR-004) regardless of rule type, the step deltas must
 * sum to the final amount (E3-02 round-trip), and running it 10 more
 * times must produce bit-for-bit identical results (determinism, per
 * D-014/ADR-002 "cả hai cùng sinh waterfall step... thứ tự xác định
 * không phụ thuộc loại rule").
 */
class HybridRuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void dslSegmentAdjustmentThenScriptDiscountProduceCorrectOrderAndRoundTrip() {
        // DSL rule: GOLD tier customers get a +5% segment markup.
        Rule tierMarkupDsl =
                new Rule(
                        10L,
                        PricingStage.SEGMENT_ADJUSTMENT,
                        1,
                        RuleType.DSL,
                        """
                        {
                          "conditions": {"==": [{"var": "tier"}, "GOLD"]},
                          "action": {"type": "percent-discount", "value": 5, "label": "Gold tier markup"}
                        }
                        """);
        // Script rule: quantity >= 10 gets a flat $20 volume discount.
        Rule volumeDiscountScript =
                new Rule(
                        20L,
                        PricingStage.QUANTITY_ADJUSTMENT,
                        1,
                        RuleType.SCRIPT,
                        """
                        if (quantity >= new BigDecimal('10')) {
                          return [delta: new BigDecimal('-20.00'), label: 'Volume discount (script)']
                        }
                        return null
                        """);
        RuleSet ruleSet = new RuleSet(1L, "hybrid", 1, List.of(volumeDiscountScript, tierMarkupDsl));
        PricingContext context =
                new PricingContext(new BigDecimal("100.00"), new BigDecimal("10"), Map.of("tier", "GOLD"));

        List<WaterfallStep> steps = engine.run(context, ruleSet);

        assertThat(steps).extracting(WaterfallStep::label)
                .containsExactly(
                        "List price", "Gold tier markup", "Volume discount (script)", "Final price");
        assertThat(steps.get(1).stage()).isEqualTo(PricingStage.SEGMENT_ADJUSTMENT);
        assertThat(steps.get(1).ruleId()).isEqualTo(10L);
        assertThat(steps.get(2).stage()).isEqualTo(PricingStage.QUANTITY_ADJUSTMENT);
        assertThat(steps.get(2).ruleId()).isEqualTo(20L);

        // 100 + 5% = 105, then -20 = 85.
        WaterfallStep finalStep = steps.get(steps.size() - 1);
        assertThat(finalStep.resultingAmount()).isEqualByComparingTo("85.00");
        assertThat(finalStep.rounded()).isTrue();

        BigDecimal sumOfDeltas =
                steps.stream().map(WaterfallStep::deltaAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfDeltas).isEqualByComparingTo(finalStep.resultingAmount());
    }

    @Test
    void sameHybridRuleSetAndContextProduceIdenticalResultAcrossTenReruns() {
        Rule dslRule =
                new Rule(
                        1L,
                        PricingStage.DISCOUNT,
                        1,
                        RuleType.DSL,
                        "{\"action\": {\"type\": \"percent-discount\", \"value\": -12.5, \"label\": \"dsl-discount\"}}");
        Rule scriptRule =
                new Rule(
                        2L,
                        PricingStage.OVERRIDE,
                        1,
                        RuleType.SCRIPT,
                        "return [delta: currentAmount.multiply(new BigDecimal('0.01')), label: 'script-override']");
        RuleSet ruleSet = new RuleSet(1L, "hybrid-determinism", 1, List.of(dslRule, scriptRule));
        PricingContext context =
                new PricingContext(new BigDecimal("349.99"), new BigDecimal("7"), Map.of("tier", "SILVER"));

        List<WaterfallStep> first = engine.run(context, ruleSet);
        for (int i = 0; i < 10; i++) {
            assertThat(engine.run(context, ruleSet)).isEqualTo(first);
        }
    }
}
