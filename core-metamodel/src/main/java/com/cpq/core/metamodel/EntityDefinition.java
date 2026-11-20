package com.cpq.core.metamodel;

import jakarta.persistence.CascadeType;
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
 * <p>Entity *instance* data lives in the typed-EAV runtime tables
 * {@code entity_instance} / {@code entity_instance_attribute_value}
 * (S2-T1, E1-01) — see {@link EntityInstance} /
 * {@link EntityInstanceAttributeValue}.
 *
 * <p>{@code packId}/{@code packVersion} (ADR-001) record which domain
 * pack's {@code pack.yaml} this definition came from, so multiple packs
 * can coexist and the origin of a definition is always known. Both are
 * nullable: a definition created directly through the service layer
 * (not via {@code PackLoaderService}) has no pack of origin.
 *
 * <p>{@code keyAttributeDefinition}/{@code labelAttributeDefinition}
 * (D-019) are optional pointers to one of this entity definition's own
 * {@link AttributeDefinition}s, marking it as the generic "business key"
 * (unique per {@code entity_def_id + pack_id}) or "display label"
 * attribute — core only knows the generic key/label concept, never what
 * the attribute actually represents (SKU, customer id, ...).
 *
 * <p>{@code role}/{@code extendsEntityDefinition} (ADR-009 mục 1,
 * D-037/D-038/D-039, S3-T1) are the entity taxonomy v2 fields — see
 * {@link EntityRole}. Both are nullable: an entity definition created
 * before this mechanism existed, or whose domain pack simply doesn't
 * declare a role, keeps its old key/label-only behaviour unchanged.
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

    @Column(name = "pack_id", length = 100)
    private String packId;

    @Column(name = "pack_version")
    private Integer packVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_attribute_def_id")
    private AttributeDefinition keyAttributeDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_attribute_def_id")
    private AttributeDefinition labelAttributeDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private EntityRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extends_entity_def_id")
    private EntityDefinition extendsEntityDefinition;

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

    /** Records which domain pack (and version) this definition came from (ADR-001). */
    public void assignPack(String packId, int packVersion) {
        this.packId = packId;
        this.packVersion = packVersion;
    }

    /**
     * Marks {@code attribute} as this entity definition's generic
     * business-key attribute (D-019). {@code attribute} must already
     * belong to this entity definition (added via {@link #addAttribute}).
     */
    public void designateKeyAttribute(AttributeDefinition attribute) {
        requireOwnAttribute(attribute);
        this.keyAttributeDefinition = attribute;
    }

    /**
     * Marks {@code attribute} as this entity definition's generic
     * display-label attribute (D-019). {@code attribute} must already
     * belong to this entity definition (added via {@link #addAttribute}).
     */
    public void designateLabelAttribute(AttributeDefinition attribute) {
        requireOwnAttribute(attribute);
        this.labelAttributeDefinition = attribute;
    }

    /**
     * Assigns this entity definition's taxonomy role (ADR-009 mục 1,
     * D-039). Does not validate anything about {@code extendsEntityDefinition}
     * by itself — callers (e.g. {@code PackLoaderService}) are responsible
     * for pairing {@link EntityRole#EXTENSION} with
     * {@link #assignExtendsEntityDefinition}.
     */
    public void assignRole(EntityRole role) {
        this.role = role;
    }

    /**
     * Records which other entity definition this one extends (ADR-009
     * mục 1, D-039) — only meaningful when {@code role ==
     * EntityRole.EXTENSION}, but not enforced here (core doesn't police
     * that pairing at the model level; {@code PackLoaderService} does).
     */
    public void assignExtendsEntityDefinition(EntityDefinition extendsEntityDefinition) {
        this.extendsEntityDefinition = extendsEntityDefinition;
    }

    private void requireOwnAttribute(AttributeDefinition attribute) {
        if (attribute.getEntityDefinition() != this) {
            throw new IllegalArgumentException(
                    "Attribute '"
                            + attribute.getCode()
                            + "' does not belong to entity definition '"
                            + this.code
                            + "' — designate it as key/label only after addAttribute()");
        }
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

    public String getPackId() {
        return packId;
    }

    public Integer getPackVersion() {
        return packVersion;
    }

    public AttributeDefinition getKeyAttributeDefinition() {
        return keyAttributeDefinition;
    }

    public AttributeDefinition getLabelAttributeDefinition() {
        return labelAttributeDefinition;
    }

    public EntityRole getRole() {
        return role;
    }

    public EntityDefinition getExtendsEntityDefinition() {
        return extendsEntityDefinition;
    }

    public List<AttributeDefinition> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }
}
