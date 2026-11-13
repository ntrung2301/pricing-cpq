package com.cpq.core.pricing;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Strategy for applying one {@link Rule} of a given {@link RuleType} to
 * the current {@link PricingContext}/running amount. {@link RuleEngine}
 * dispatches to the executor matching {@code rule.ruleType()} — DSL and
 * Script rules are evaluated by completely separate implementations (see
 * {@code com.cpq.core.pricing.dsl.DslRuleExecutor} and
 * {@code com.cpq.core.pricing.script.GroovyRuleExecutor}) but must honor
 * the same contract: return {@link Optional#empty()} when the rule's
 * condition does not match (no waterfall step produced), or a
 * {@link RuleApplication} when it does.
 */
public interface RuleExecutor {

    Optional<RuleApplication> apply(Rule rule, PricingContext context, BigDecimal currentAmount);
}
