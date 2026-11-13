package com.cpq.core.metamodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One concrete edge of a {@link RelationshipDefinition} between two
 * {@link EntityInstance}s (E1-02/ADR-003, S2-T7). Core only knows generic
 * "from"/"to" (see {@code com.cpq.core.metamodel.RelationshipDefinition}'s
 * own javadoc) — it has no idea what the edge represents. A
 * {@code relationship_def} of code {@code "supersedes"} uses this row to
 * mean "{@code fromInstance} supersedes/replaces {@code toInstance}"; one
 * of code {@code "contains"} uses it for kit/bundle composition — both are
 * domain-pack data (automotive's {@code pack.yaml}), never a core concept.
 *
 * <p>{@code attributes} is a nullable, free-form JSON text blob (e.g. an
 * {@code effective_date} for when a supersession takes effect) — this
 * class performs no parsing of it itself, same "plain JPA entity, no
 * business logic" shape as {@link ConstraintInstance}.
 */
@Entity
@Table(name = "relationship_instance")
public class RelationshipInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relationship_def_id", nullable = false)
    private RelationshipDefinition relationshipDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_instance_id", nullable = false)
    private EntityInstance fromInstance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_instance_id", nullable = false)
    private EntityInstance toInstance;

    @Column(name = "attributes", columnDefinition = "TEXT")
    private String attributes;

    protected RelationshipInstance() {
        // JPA
    }

    public RelationshipInstance(
            RelationshipDefinition relationshipDefinition,
            EntityInstance fromInstance,
            EntityInstance toInstance,
            String attributes) {
        this.relationshipDefinition = relationshipDefinition;
        this.fromInstance = fromInstance;
        this.toInstance = toInstance;
        this.attributes = attributes;
    }

    public Long getId() {
        return id;
    }

    public RelationshipDefinition getRelationshipDefinition() {
        return relationshipDefinition;
    }

    public EntityInstance getFromInstance() {
        return fromInstance;
    }

    public EntityInstance getToInstance() {
        return toInstance;
    }

    public String getAttributes() {
        return attributes;
    }
}
