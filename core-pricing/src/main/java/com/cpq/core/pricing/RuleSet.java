package com.cpq.core.pricing;

import java.util.List;
import java.util.Objects;

/**
 * A versioned {@code rule_set} (ADR-004: snapshotted onto
 * {@code quote_version} for replay) plus the {@link Rule} rows loaded for
 * it. {@link RuleEngine} does not trust the order {@code rules} is handed
 * in — it always re-sorts by {@code (stage, priority, id)} itself.
 */
public record RuleSet(Long id, String code, int version, List<Rule> rules) {

    public RuleSet(Long id, String code, int version, List<Rule> rules) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.version = version;
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }
}
