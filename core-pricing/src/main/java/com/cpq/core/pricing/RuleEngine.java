package com.cpq.core.pricing;

import com.cpq.core.pricing.dsl.DslRuleExecutor;
import com.cpq.core.pricing.script.GroovyRuleExecutor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generic rule engine (ADR-002/ADR-004/D-014): runs a {@link RuleSet}
 * against a {@link PricingContext} and produces a deterministic list of
 * {@link WaterfallStep}s whose deltas sum exactly to the final amount
 * (E3-02 round-trip requirement). Not forward-chaining — a single
 * sequential pass over {@link PricingStage#values()} in declaration
 * order, and within each stage, matching rules in {@code (priority,
 * rule.id)} order. This order is fixed regardless of the order
 * {@code ruleSet.rules()} is handed in (e.g. DB read order).
 *
 * <p>Dispatches each {@link Rule} to the {@link RuleExecutor} matching its
 * {@link RuleType} — {@link DslRuleExecutor} or
 * {@link GroovyRuleExecutor} — both producing the exact same
 * {@link WaterfallStep} shape, so the pipeline logic here never needs to
 * know which kind of rule it is running (ADR-002: "thứ tự xác định không
 * phụ thuộc rule là DSL hay Script").
 */
public final class RuleEngine {

    /** ADR-004: internal running-total scale, rounded only at the final step. */
    private static final int INTERNAL_SCALE = 6;

    private static final int FINAL_SCALE = 2;

    private final Map<RuleType, RuleExecutor> executors;

    public RuleEngine() {
        this(new DslRuleExecutor(), new GroovyRuleExecutor());
    }

    public RuleEngine(RuleExecutor dslExecutor, RuleExecutor scriptExecutor) {
        this.executors = Map.of(RuleType.DSL, dslExecutor, RuleType.SCRIPT, scriptExecutor);
    }

    public List<WaterfallStep> run(PricingContext context, RuleSet ruleSet) {
        List<WaterfallStep> steps = new ArrayList<>();
        int order = 1;

        BigDecimal current = context.basePrice().setScale(INTERNAL_SCALE, RoundingMode.HALF_UP);
        steps.add(new WaterfallStep(order++, PricingStage.BASE, null, "List price", current, current, false));

        List<Rule> sortedRules = sortRules(ruleSet.rules());
        for (Rule rule : sortedRules) {
            RuleExecutor executor = executorFor(rule);
            Optional<RuleApplication> application = executor.apply(rule, context, current);
            if (application.isEmpty()) {
                continue;
            }
            RuleApplication result = application.get();
            BigDecimal delta = result.delta().setScale(INTERNAL_SCALE, RoundingMode.HALF_UP);
            current = current.add(delta);
            steps.add(new WaterfallStep(order++, rule.stage(), rule.id(), result.label(), delta, current, false));
        }

        BigDecimal rounded = current.setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal roundingDelta = rounded.subtract(current);
        steps.add(new WaterfallStep(order, PricingStage.GUARDRAIL, null, "Final price", roundingDelta, rounded, true));

        return List.copyOf(steps);
    }

    private RuleExecutor executorFor(Rule rule) {
        RuleExecutor executor = executors.get(rule.ruleType());
        if (executor == null) {
            throw new RuleDefinitionRejectedException("No executor registered for rule type " + rule.ruleType());
        }
        return executor;
    }

    private static List<Rule> sortRules(List<Rule> rules) {
        return rules.stream()
                .sorted(
                        Comparator.comparing((Rule rule) -> rule.stage().ordinal())
                                .thenComparing(Rule::priority)
                                .thenComparing(Rule::id))
                .toList();
    }
}
