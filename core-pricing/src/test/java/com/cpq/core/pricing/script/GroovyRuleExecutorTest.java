package com.cpq.core.pricing.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.core.pricing.PricingContext;
import com.cpq.core.pricing.PricingStage;
import com.cpq.core.pricing.Rule;
import com.cpq.core.pricing.RuleApplication;
import com.cpq.core.pricing.RuleDefinitionRejectedException;
import com.cpq.core.pricing.RuleExecutionException;
import com.cpq.core.pricing.RuleType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GroovyRuleExecutorTest {

    private final GroovyRuleExecutor executor = new GroovyRuleExecutor();

    private static Rule ruleOf(String script) {
        return new Rule(99L, PricingStage.DISCOUNT, 1, RuleType.SCRIPT, script);
    }

    @Test
    void scriptReturningDeltaMapProducesRuleApplication() {
        Rule rule =
                ruleOf("if (attributes.tier == 'GOLD') { return [delta: currentAmount * new BigDecimal('-0.05'), label: 'gold'] }\nreturn null");
        PricingContext context =
                new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of("tier", "GOLD"));

        Optional<RuleApplication> result = executor.apply(rule, context, new BigDecimal("200.00"));

        assertThat(result).isPresent();
        assertThat(result.get().delta()).isEqualByComparingTo("-10.00");
        assertThat(result.get().label()).isEqualTo("gold");
    }

    @Test
    void scriptReturningNullMeansSkipped() {
        Rule rule = ruleOf("return null");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        assertThat(executor.apply(rule, context, BigDecimal.TEN)).isEmpty();
    }

    @Test
    void scriptCanReadQuantityAndBasePriceBindings() {
        Rule rule = ruleOf("return [delta: basePrice.multiply(quantity).subtract(currentAmount), label: 'recompute']");
        PricingContext context = new PricingContext(new BigDecimal("10.00"), new BigDecimal("3"), Map.of());

        RuleApplication result = executor.apply(rule, context, new BigDecimal("0.00")).orElseThrow();

        assertThat(result.delta()).isEqualByComparingTo("30.00");
    }

    @Test
    void maliciousScriptIsRejectedAtApplyTimeWithoutExecuting() {
        Rule rule = ruleOf("System.exit(1)\nreturn null");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        // If this ever actually ran, the JVM running this test would exit.
        assertThatThrownBy(() -> executor.apply(rule, context, BigDecimal.TEN))
                .isInstanceOf(RuleDefinitionRejectedException.class);
    }

    @Test
    void infiniteLoopScriptIsBoundedByTimeoutNotLeftHanging() {
        Rule rule = ruleOf("long x = 0\nwhile (true) { x = x + 1 }\nreturn [delta: x, label: 'never']");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        long start = System.nanoTime();
        assertThatThrownBy(() -> executor.apply(rule, context, BigDecimal.TEN))
                .isInstanceOf(RuleExecutionException.class);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(5_000);
    }

    @Test
    void scriptNotReturningDeltaMapIsRuntimeError() {
        Rule rule = ruleOf("return 'not a map'");
        PricingContext context = new PricingContext(BigDecimal.TEN, BigDecimal.ONE, Map.of());

        assertThatThrownBy(() -> executor.apply(rule, context, BigDecimal.TEN))
                .isInstanceOf(RuleExecutionException.class);
    }
}
