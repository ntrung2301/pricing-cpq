package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.PricingStage;
import com.cpq.core.pricing.Rule;
import com.cpq.core.pricing.RuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA row for {@code rule} (ADR-002/D-014). {@code stage} persists via
 * {@link PricingStageConverter} (ADR-004 wire codes, e.g.
 * {@code "segment-adjustment"}), not {@link PricingStage#name()}.
 * {@code ruleType} persists as {@link RuleType#name()} directly (matches
 * the {@code rule_type} CHECK constraint, {@code 'DSL'}/{@code
 * 'SCRIPT'}). {@link #toEngineRule()} projects this row to the pure
 * {@link Rule} record {@link com.cpq.core.pricing.RuleEngine} consumes —
 * the engine never sees the JPA entity itself.
 */
@Entity
@Table(name = "rule")
public class RuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSetEntity ruleSet;

    @Convert(converter = PricingStageConverter.class)
    @Column(nullable = false, length = 30)
    private PricingStage stage;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 10)
    private RuleType ruleType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String definition;

    protected RuleEntity() {
        // JPA
    }

    public RuleEntity(PricingStage stage, int priority, RuleType ruleType, String definition) {
        this.stage = stage;
        this.priority = priority;
        this.ruleType = ruleType;
        this.definition = definition;
    }

    void assignRuleSet(RuleSetEntity ruleSet) {
        this.ruleSet = ruleSet;
    }

    /** Projects this persisted row to the engine's pure {@link Rule} value type. */
    public Rule toEngineRule() {
        return new Rule(id, stage, priority, ruleType, definition);
    }

    public Long getId() {
        return id;
    }

    public RuleSetEntity getRuleSet() {
        return ruleSet;
    }

    public PricingStage getStage() {
        return stage;
    }

    public int getPriority() {
        return priority;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public String getDefinition() {
        return definition;
    }
}
