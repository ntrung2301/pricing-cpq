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
 * One concrete assertion of a {@link ConstraintDefinition} between two
 * {@link EntityInstance}s, holding the condition JSON to evaluate
 * (E1-02, S2-T2). For a {@code "compatibility"}-typed definition, this
 * row means "entity instance X is compatible with entity instance Y if
 * {@code condition} holds" — core only knows generic aliases "x"/"y"
 * (see {@code com.cpq.core.constraintengine.EvaluationContext}), never
 * what kind of entity X or Y actually are (e.g. "fitment" between a
 * "Part" and a "Vehicle", per ADR-003, is just one domain's use of this
 * same generic row shape).
 *
 * <p>{@code condition} uses the JSON grammar shared with rule conditions
 * (ADR-002: {@code ==, !=, >, >=, <, <=, in, and, or, not}, leaf values
 * via {@code {"var": "x.<attributeCode>"}}/{@code {"var": "y.<attributeCode>"}}
 * or JSON literals) — evaluated by
 * {@code com.cpq.core.constraintengine.ConstraintEngine}, not by this
 * class itself (this is a plain JPA entity, no evaluation logic).
 */
@Entity
@Table(name = "constraint_instance")
public class ConstraintInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "constraint_def_id", nullable = false)
    private ConstraintDefinition constraintDefinition;

    @Column(name = "condition", nullable = false, columnDefinition = "TEXT")
    private String condition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_instance_x_id", nullable = false)
    private EntityInstance entityInstanceX;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_instance_y_id", nullable = false)
    private EntityInstance entityInstanceY;

    @Column(name = "pack_id", length = 100)
    private String packId;

    @Column(name = "pack_version")
    private Integer packVersion;

    protected ConstraintInstance() {
        // JPA
    }

    public ConstraintInstance(
            ConstraintDefinition constraintDefinition,
            String condition,
            EntityInstance entityInstanceX,
            EntityInstance entityInstanceY) {
        this.constraintDefinition = constraintDefinition;
        this.condition = condition;
        this.entityInstanceX = entityInstanceX;
        this.entityInstanceY = entityInstanceY;
    }

    /** Records which domain pack (and version) this instance came from (ADR-001). */
    public void assignPack(String packId, int packVersion) {
        this.packId = packId;
        this.packVersion = packVersion;
    }

    public Long getId() {
        return id;
    }

    public ConstraintDefinition getConstraintDefinition() {
        return constraintDefinition;
    }

    public String getCondition() {
        return condition;
    }

    public EntityInstance getEntityInstanceX() {
        return entityInstanceX;
    }

    public EntityInstance getEntityInstanceY() {
        return entityInstanceY;
    }

    public String getPackId() {
        return packId;
    }

    public Integer getPackVersion() {
        return packVersion;
    }
}
