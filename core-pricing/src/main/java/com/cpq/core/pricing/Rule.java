package com.cpq.core.pricing;

import java.util.Objects;

/**
 * One row of the {@code rule} table (ADR-002/D-014), as loaded by the
 * caller (app layer) for {@link RuleEngine#run}. {@code definition} is
 * either a JSON DSL document (see {@code com.cpq.core.pricing.dsl}) or raw
 * Groovy source (see {@code com.cpq.core.pricing.script}), depending on
 * {@code ruleType} — opaque text to the engine until the matching executor
 * parses/compiles it.
 */
public record Rule(Long id, PricingStage stage, int priority, RuleType ruleType, String definition) {

    public Rule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(ruleType, "ruleType");
        Objects.requireNonNull(definition, "definition");
    }
}
