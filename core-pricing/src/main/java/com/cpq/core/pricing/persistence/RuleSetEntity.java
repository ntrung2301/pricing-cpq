package com.cpq.core.pricing.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JPA row for {@code rule_set} (ADR-002/D-014/ADR-004: snapshotted onto
 * {@code quote_version} for replay — the snapshot mechanism itself is
 * S2-T12+ scope, not built here). Plain {@code jakarta.persistence}
 * class, no Hibernate/Spring Data dependency — same ADR-005 split as
 * core-metamodel's {@code EntityDefinition}: {@code app} owns the
 * repository.
 */
@Entity
@Table(name = "rule_set")
public class RuleSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false)
    private int version;

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RuleEntity> rules = new ArrayList<>();

    protected RuleSetEntity() {
        // JPA
    }

    public RuleSetEntity(String code, int version) {
        this.code = code;
        this.version = version;
    }

    public void addRule(RuleEntity rule) {
        rule.assignRuleSet(this);
        rules.add(rule);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getVersion() {
        return version;
    }

    public List<RuleEntity> getRules() {
        return Collections.unmodifiableList(rules);
    }
}
