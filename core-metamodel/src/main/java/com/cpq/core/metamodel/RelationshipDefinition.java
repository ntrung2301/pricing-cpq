package com.cpq.core.metamodel;

import jakarta.persistence.Column;
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
 * A named relationship between two {@link EntityDefinition}s (ADR-001).
 * Core only knows "from entity def X to entity def Y with cardinality Z" —
 * it has no idea what the relationship represents. ADR-003 gives two
 * domain-pack examples that fit this generic shape without any core
 * change: a self-referencing {@code "supersedes"} (from and to the same
 * entity def) and a {@code "contains"} for kit/bundle composition.
 */
@Entity
@Table(name = "relationship_def")
public class RelationshipDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_entity_def_id", nullable = false)
    private EntityDefinition fromEntityDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_entity_def_id", nullable = false)
    private EntityDefinition toEntityDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RelationshipCardinality cardinality;

    protected RelationshipDefinition() {
        // JPA
    }

    public RelationshipDefinition(
            String code,
            EntityDefinition fromEntityDefinition,
            EntityDefinition toEntityDefinition,
            RelationshipCardinality cardinality) {
        this.code = code;
        this.fromEntityDefinition = fromEntityDefinition;
        this.toEntityDefinition = toEntityDefinition;
        this.cardinality = cardinality;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public EntityDefinition getFromEntityDefinition() {
        return fromEntityDefinition;
    }

    public EntityDefinition getToEntityDefinition() {
        return toEntityDefinition;
    }

    public RelationshipCardinality getCardinality() {
        return cardinality;
    }
}
