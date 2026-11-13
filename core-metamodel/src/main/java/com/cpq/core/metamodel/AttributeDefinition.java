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
import jakarta.persistence.UniqueConstraint;

/**
 * A field belonging to an {@link EntityDefinition} (ADR-001). Core only
 * knows a finite, generic set of value types ({@link AttributeDataType});
 * it has no idea what "part number" or "engine size" mean — that is
 * domain-pack data living in {@code code}/{@code label}.
 *
 * <p>Per ADR-003 (D-005): {@code code} is the immutable key used as the
 * exact CSV/Excel import header to match against, kept separate from
 * {@code label} (display name, can change/be localized independently).
 * {@code externalCodeHint} is a nullable free-text note reserved for a
 * future ACES/PIES-style external mapping (Q-001) — no real mapping logic
 * exists yet in MVP, this is purely a column for a human-readable note.
 */
@Entity
@Table(
        name = "attribute_def",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"entity_def_id", "code"}))
public class AttributeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_def_id", nullable = false)
    private EntityDefinition entityDefinition;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private AttributeDataType dataType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "external_code_hint")
    private String externalCodeHint;

    @Column(name = "pack_id", length = 100)
    private String packId;

    @Column(name = "pack_version")
    private Integer packVersion;

    protected AttributeDefinition() {
        // JPA
    }

    public AttributeDefinition(
            String code,
            String label,
            AttributeDataType dataType,
            boolean required,
            String externalCodeHint) {
        this.code = code;
        this.label = label;
        this.dataType = dataType;
        this.required = required;
        this.externalCodeHint = externalCodeHint;
    }

    /** Package-private: the owning side is set via {@link EntityDefinition#addAttribute}. */
    void assignEntityDefinition(EntityDefinition entityDefinition) {
        this.entityDefinition = entityDefinition;
    }

    /** Records which domain pack (and version) this definition came from (ADR-001). */
    public void assignPack(String packId, int packVersion) {
        this.packId = packId;
        this.packVersion = packVersion;
    }

    public String getPackId() {
        return packId;
    }

    public Integer getPackVersion() {
        return packVersion;
    }

    public Long getId() {
        return id;
    }

    public EntityDefinition getEntityDefinition() {
        return entityDefinition;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public AttributeDataType getDataType() {
        return dataType;
    }

    public boolean isRequired() {
        return required;
    }

    public String getExternalCodeHint() {
        return externalCodeHint;
    }
}
