package com.cpq.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Core {@link RuleEngine} behaviour: always emits a {@code base} step from
 * {@link PricingContext#basePrice()}, applies matching rules in strict
 * {@code (stage, priority, rule.id)} order (ADR-004), skips non-matching
 * rules silently (no waterfall step), and always appends one final
 * {@code rounded=true} step so the step deltas sum exactly to the final
 * amount (E3-02 round-trip requirement).
 */
class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void emitsBaseStepThenFinalRoundedStepWhenNoRuleMatches() {
        PricingContext context = new PricingContext(new BigDecimal("100.00"), BigDecimal.ONE, Map.of());
        RuleSet ruleSet = new RuleSet(1L, "empty", 1, List.of());

        List<WaterfallStep> steps = engine.run(context, ruleSet);

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).stage()).isEqualTo(PricingStage.BASE);
        assertThat(steps.get(0).ruleId()).isNull();
        assertThat(steps.get(0).deltaAmount()).isEqualByComparingTo("100.00");
        assertThat(steps.get(0).resultingAmount()).isEqualByComparingTo("100.00");
        assertThat(steps.get(0).rounded()).isFalse();

        WaterfallStep finalStep = steps.get(1);
        assertThat(finalStep.rounded()).isTrue();
        assertThat(finalStep.resultingAmount()).isEqualByComparingTo("100.00");
        assertThat(finalStep.resultingAmount().scale()).isEqualTo(2);
    }

    @Test
    void appliesMatchingDslPercentDiscountRuleAndSumsToFinalAmount() {
        PricingContext context = new PricingContext(new BigDecimal("100.00"), new BigDecimal("20"), Map.of());
        String definition =
                """
                {
                  "conditions": {">": [{"var": "quantity"}, 10]},
                  "action": {"type": "percent-discount", "value": -10, "label": "Volume discount"}
                }
                """;
        Rule rule = new Rule(42L, PricingStage.DISCOUNT, 1, RuleType.DSL, definition);
        RuleSet ruleSet = new RuleSet(1L, "rs", 1, List.of(rule));

        List<WaterfallStep> steps = engine.run(context, ruleSet);

        assertThat(steps).hasSize(3);
        WaterfallStep discountStep = steps.get(1);
        assertThat(discountStep.stage()).isEqualTo(PricingStage.DISCOUNT);
        assertThat(discountStep.ruleId()).isEqualTo(42L);
        assertThat(discountStep.label()).isEqualTo("Volume discount");
        assertThat(discountStep.deltaAmount()).isEqualByComparingTo("-10.000000");
        assertThat(discountStep.resultingAmount()).isEqualByComparingTo("90.000000");

        WaterfallStep finalStep = steps.get(2);
        assertThat(finalStep.resultingAmount()).isEqualByComparingTo("90.00");

        BigDecimal sumOfDeltas =
                steps.stream().map(WaterfallStep::deltaAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfDeltas).isEqualByComparingTo(finalStep.resultingAmount());
    }

    @Test
    void skipsRuleWhenConditionDoesNotMatchProducingNoStep() {
        PricingContext context = new PricingContext(new BigDecimal("100.00"), new BigDecimal("5"), Map.of());
        String definition =
                """
                {
                  "conditions": {">": [{"var": "quantity"}, 10]},
                  "action": {"type": "percent-discount", "value": -10}
                }
                """;
        Rule rule = new Rule(42L, PricingStage.DISCOUNT, 1, RuleType.DSL, definition);
        RuleSet ruleSet = new RuleSet(1L, "rs", 1, List.of(rule));

        List<WaterfallStep> steps = engine.run(context, ruleSet);

        assertThat(steps).hasSize(2);
        assertThat(steps).noneMatch(step -> step.ruleId() != null);
    }

    @Test
    void appliesRulesInStageThenPriorityThenIdOrderRegardlessOfListOrder() {
        PricingContext context = new PricingContext(new BigDecimal("100.00"), BigDecimal.ONE, Map.of());
        Rule guardrailRule =
                new Rule(
                        3L,
                        PricingStage.GUARDRAIL,
                        1,
                        RuleType.DSL,
                        "{\"action\": {\"type\": \"fixed-amount\", \"value\": -1, \"label\": \"guardrail\"}}");
        Rule discountLowPriority =
                new Rule(
                        2L,
                        PricingStage.DISCOUNT,
                        10,
                        RuleType.DSL,
                        "{\"action\": {\"type\": \"fixed-amount\", \"value\": -2, \"label\": \"discount-prio-10\"}}");
        Rule discountHighPriority =
                new Rule(
                        1L,
                        PricingStage.DISCOUNT,
                        1,
                        RuleType.DSL,
                        "{\"action\": {\"type\": \"fixed-amount\", \"value\": -5, \"label\": \"discount-prio-1\"}}");
        // Deliberately out of pipeline order in the list to prove the engine
        // sorts by (stage, priority, id), not by list/DB read order.
        RuleSet ruleSet =
                new RuleSet(1L, "rs", 1, List.of(guardrailRule, discountLowPriority, discountHighPriority));

        List<WaterfallStep> steps = engine.run(context, ruleSet);

        assertThat(steps).extracting(WaterfallStep::label)
                .containsExactly("List price", "discount-prio-1", "discount-prio-10", "guardrail", "Final price");
    }

    @Test
    void sameInputAlwaysProducesSameOutputAcrossTenRuns() {
        PricingContext context = new PricingContext(new BigDecimal("199.99"), new BigDecimal("3"), Map.of());
        Rule rule =
                new Rule(
                        7L,
                        PricingStage.DISCOUNT,
                        1,
                        RuleType.DSL,
                        "{\"action\": {\"type\": \"percent-discount\", \"value\": -7.5, \"label\": \"loyalty\"}}");
        RuleSet ruleSet = new RuleSet(1L, "rs", 1, List.of(rule));

        List<WaterfallStep> first = engine.run(context, ruleSet);
        for (int i = 0; i < 10; i++) {
            List<WaterfallStep> next = engine.run(context, ruleSet);
            assertThat(next).isEqualTo(first);
        }
    }
}
