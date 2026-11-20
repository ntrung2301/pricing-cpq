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
 * One ordered "dimension" attribute of an {@link EntityRole#EXTENSION}
 * {@link EntityDefinition} (ADR-009 mục 1, D-037/D-038/D-039, S3-T1) — e.g.
 * {@code period} for {@code CostHistory}, or {@code city} for a
 * channel-price extension. Together with the extended (anchor) instance's
 * own {@code businessKey}, the ordered list of dimension attribute values
 * assembles an {@link EntityRole#EXTENSION} instance's composite
 * {@code businessKey} (see {@code EntityInstanceService}).
 *
 * <p>{@code position} orders multiple dimension attributes deterministically
 * (e.g. {@code period} before {@code city}) — core only knows "ordered list
 * of attribute defs", never which one is more "important" business-wise.
 */
@Entity
@Table(name = "entity_def_dimension_attribute")
public class EntityDefinitionDimensionAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_def_id", nullable = false)
    private EntityDefinition entityDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_def_id", nullable = false)
    private AttributeDefinition attributeDefinition;

    @Column(nullable = false)
    private int position;

    protected EntityDefinitionDimensionAttribute() {
        // JPA
    }

    public EntityDefinitionDimensionAttribute(
            EntityDefinition entityDefinition, AttributeDefinition attributeDefinition, int position) {
        this.entityDefinition = entityDefinition;
        this.attributeDefinition = attributeDefinition;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public EntityDefinition getEntityDefinition() {
        return entityDefinition;
    }

    public AttributeDefinition getAttributeDefinition() {
        return attributeDefinition;
    }

    public int getPosition() {
        return position;
    }
}
