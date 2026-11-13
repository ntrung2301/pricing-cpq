package com.cpq.core.pricing.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.core.pricing.PricingContext;
import com.cpq.core.pricing.PricingStage;
import com.cpq.core.pricing.Rule;
import com.cpq.core.pricing.RuleApplication;
import com.cpq.core.pricing.RuleDefinitionRejectedException;
import com.cpq.core.pricing.RuleType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the JSON DSL executor (ADR-002 condition grammar +
 * finite generic action set). Exercised directly (not only through
 * {@code RuleEngineTest}) because the condition/action grammar is the
 * part most likely to need new operators/actions later.
 */
class DslRuleExecutorTest {

    private final DslRuleExecutor executor = new DslRuleExecutor();

    private static Rule ruleOf(String definition) {
        return new Rule(1L, PricingStage.DISCOUNT, 1, RuleType.DSL, definition);
    }

    @Test
    void noConditionsFieldMeansAlwaysApplies() {
        Rule rule = ruleOf("{\"action\": {\"type\": \"fixed-amount\", \"value\": -5}}");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        Optional<RuleApplication> result = executor.apply(rule, context, new BigDecimal("10.000000"));

        assertThat(result).isPresent();
        assertThat(result.get().delta()).isEqualByComparingTo("-5");
    }

    @Test
    void andOrNotInOperatorsEvaluateCorrectly() {
        String definition =
                """
                {
                  "conditions": {
                    "and": [
                      {"or": [{"==": [{"var": "tier"}, "GOLD"]}, {"==": [{"var": "tier"}, "PLATINUM"]}]},
                      {"not": {"in": [{"var": "tier"}, ["BRONZE"]]}}
                    ]
                  },
                  "action": {"type": "fixed-amount", "value": -1, "label": "tier-match"}
                }
                """;
        Rule rule = ruleOf(definition);
        PricingContext matching =
                new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of("tier", "GOLD"));
        PricingContext nonMatching =
                new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of("tier", "SILVER"));

        assertThat(executor.apply(rule, matching, BigDecimal.TEN)).isPresent();
        assertThat(executor.apply(rule, nonMatching, BigDecimal.TEN)).isEmpty();
    }

    @Test
    void percentDiscountActionComputesDeltaFromCurrentAmount() {
        Rule rule = ruleOf("{\"action\": {\"type\": \"percent-discount\", \"value\": -10}}");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        RuleApplication result = executor.apply(rule, context, new BigDecimal("200.000000")).orElseThrow();

        assertThat(result.delta()).isEqualByComparingTo("-20.000000");
    }

    @Test
    void setPriceActionDeltaBringsCurrentAmountToExactTarget() {
        Rule rule = ruleOf("{\"action\": {\"type\": \"set-price\", \"value\": 49.99}}");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        RuleApplication result = executor.apply(rule, context, new BigDecimal("200.000000")).orElseThrow();

        assertThat(result.delta()).isEqualByComparingTo(new BigDecimal("49.99").subtract(new BigDecimal("200.000000")));
    }

    @Test
    void defaultLabelUsedWhenActionHasNoExplicitLabel() {
        Rule rule = ruleOf("{\"action\": {\"type\": \"fixed-amount\", \"value\": -1}}");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        RuleApplication result = executor.apply(rule, context, BigDecimal.TEN).orElseThrow();

        assertThat(result.label()).isNotBlank();
    }

    @Test
    void malformedJsonIsRejectedNotSilentlySkipped() {
        Rule rule = ruleOf("{not valid json");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        assertThatThrownBy(() -> executor.apply(rule, context, BigDecimal.TEN))
                .isInstanceOf(RuleDefinitionRejectedException.class);
    }

    @Test
    void unknownActionTypeIsRejected() {
        Rule rule = ruleOf("{\"action\": {\"type\": \"nonexistent-action\", \"value\": 1}}");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        assertThatThrownBy(() -> executor.apply(rule, context, BigDecimal.TEN))
                .isInstanceOf(RuleDefinitionRejectedException.class);
    }

    @Test
    void numericLiteralsAreParsedAsBigDecimalNotDouble() {
        // 0.1 + 0.2 != 0.3 in double, but must be exact via BigDecimal.
        Rule rule = ruleOf("{\"action\": {\"type\": \"fixed-amount\", \"value\": 0.1}}");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        RuleApplication result = executor.apply(rule, context, new BigDecimal("0.2")).orElseThrow();

        assertThat(result.delta()).isEqualByComparingTo("0.1");
        assertThat(result.delta().add(new BigDecimal("0.2"))).isEqualByComparingTo("0.3");
    }
}
