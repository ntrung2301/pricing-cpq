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
 * A single instance of an {@link EntityDefinition} (S2-T1, E1-01,
 * ADR-001's typed EAV). Core only knows "an instance of entity def X,
 * created from pack Y version Z" — it has no idea whether the instance is
 * a "Part", a "Customer", or anything else; that meaning lives entirely in
 * the {@link EntityInstanceAttributeValue} rows attached to it.
 *
 * <p>{@code businessKey}/{@code displayLabel} (D-019) are denormalized
 * copies of the EAV value held for {@code entityDefinition}'s
 * {@code keyAttributeDefinition}/{@code labelAttributeDefinition}, kept in
 * sync by the service layer (not a DB trigger) whenever that attribute's
 * value is written. {@code businessKey} is unique per
 * {@code entityDefinition + packId} (DB unique index).
 *
 * <p>{@code anchorInstance} (ADR-009 mục 1, D-037/D-038/D-039, S3-T1) is
 * only meaningful when {@code entityDefinition.getRole() ==
 * EntityRole.EXTENSION} — points at the other {@code EntityInstance} (of
 * {@code entityDefinition.getExtendsEntityDefinition()}) this instance
 * extends. For such instances, {@code businessKey} is assembled by the
 * service layer as the anchor instance's own {@code businessKey} plus the
 * ordered dimension attribute values (see
 * {@link EntityDefinitionDimensionAttribute}), joined by {@code "|"}.
 */
@Entity
@Table(name = "entity_instance")
public class EntityInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_def_id", nullable = false)
    private EntityDefinition entityDefinition;

    @Column(name = "pack_id", nullable = false, length = 100)
    private String packId;

    @Column(name = "pack_version", nullable = false)
    private int packVersion;

    @Column(name = "business_key", length = 255)
    private String businessKey;

    @Column(name = "display_label", length = 255)
    private String displayLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anchor_instance_id")
    private EntityInstance anchorInstance;

    protected EntityInstance() {
        // JPA
    }

    public EntityInstance(EntityDefinition entityDefinition, String packId, int packVersion) {
        this.entityDefinition = entityDefinition;
        this.packId = packId;
        this.packVersion = packVersion;
    }

    /**
     * Synchronizes the denormalized business key (D-019). Intended to be
     * called only by {@code EntityInstanceService} (app) whenever the
     * entity definition's {@code keyAttributeDefinition} value is written
     * — not part of the generic public write API for arbitrary callers.
     */
    public void syncBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    /**
     * Synchronizes the denormalized display label (D-019). Intended to be
     * called only by {@code EntityInstanceService} (app) whenever the
     * entity definition's {@code labelAttributeDefinition} value is
     * written — not part of the generic public write API for arbitrary
     * callers.
     */
    public void syncDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    /**
     * Records which other {@code EntityInstance} this one extends (ADR-009
     * mục 1, D-039) — only meaningful when this instance's entity
     * definition has {@code role == EntityRole.EXTENSION}. Intended to be
     * called only by {@code EntityInstanceService} (app), which enforces
     * that pairing; not part of the generic public write API for arbitrary
     * callers.
     */
    public void assignAnchorInstance(EntityInstance anchorInstance) {
        this.anchorInstance = anchorInstance;
    }

    public EntityInstance getAnchorInstance() {
        return anchorInstance;
    }

    public Long getId() {
        return id;
    }

    public EntityDefinition getEntityDefinition() {
        return entityDefinition;
    }

    public String getPackId() {
        return packId;
    }

    public int getPackVersion() {
        return packVersion;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }
}
