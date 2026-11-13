package com.cpq.core.metamodel;

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
 * Generic metamodel concept (ADR-001): "a kind of thing a domain pack can
 * define instances of" — core has no idea whether a given row means "Part",
 * "Vehicle", "Widget", or anything else; that meaning lives entirely in the
 * {@code code}/{@code label} data, supplied by a domain pack's
 * {@code pack.yaml} (not by Java code).
 *
 * <p>{@code code} is the immutable business key (stable identifier used by
 * other definitions/relationships/imports); {@code label} is the
 * UI-facing display name and may change independently of {@code code}
 * (ADR-003). {@code version} tracks the definition's own revision, bumped
 * when a domain pack upgrades it (ADR-001's pack upgrade/diff mechanism).
 *
 * <p>Entity *instance* data (the EAV runtime tables {@code entity_instance}
 * / {@code entity_instance_attribute_value}) is explicitly out of scope
 * for S1-T7 — see Sprint 2 (docs/roadmap.md) / E1-01 (docs/backlog.md).
 */
@Entity
@Table(name = "entity_def")
public class EntityDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(nullable = false)
    private int version;

    @OneToMany(mappedBy = "entityDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttributeDefinition> attributes = new ArrayList<>();

    protected EntityDefinition() {
        // JPA
    }

    public EntityDefinition(String code, String label, int version) {
        this.code = code;
        this.label = label;
        this.version = version;
    }

    /**
     * Adds an {@link AttributeDefinition} to this entity definition and
     * wires the owning side of the relationship, so both sides are
     * consistent before the aggregate is persisted (cascade ALL).
     */
    public void addAttribute(AttributeDefinition attribute) {
        attribute.assignEntityDefinition(this);
        attributes.add(attribute);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getVersion() {
        return version;
    }

    public List<AttributeDefinition> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }
}
