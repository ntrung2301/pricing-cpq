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
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One attribute's value for one {@link EntityInstance} (S2-T1, ADR-001's
 * typed EAV): exactly one {@code value*} column is populated, matching the
 * attribute's {@link AttributeDataType}. Single-valued per
 * (instance, attribute) — unique DB constraint — multi-valued attributes
 * are out of scope for S2-T1.
 */
@Entity
@Table(name = "entity_instance_attribute_value")
public class EntityInstanceAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_instance_id", nullable = false)
    private EntityInstance entityInstance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_def_id", nullable = false)
    private AttributeDefinition attributeDefinition;

    @Column(name = "value_string")
    private String valueString;

    @Column(name = "value_number", precision = 20, scale = 6)
    private BigDecimal valueNumber;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_ref_instance_id")
    private Long valueRefInstanceId;

    protected EntityInstanceAttributeValue() {
        // JPA
    }

    public EntityInstanceAttributeValue(
            EntityInstance entityInstance, AttributeDefinition attributeDefinition, AttributeValue value) {
        this.entityInstance = entityInstance;
        this.attributeDefinition = attributeDefinition;
        applyValue(value);
    }

    /** Overwrites this row's typed value (used when updating an existing value). */
    public void applyValue(AttributeValue value) {
        this.valueString = value.stringValue();
        this.valueNumber = value.numberValue();
        this.valueDate = value.dateValue();
        this.valueBoolean = value.booleanValue();
        this.valueRefInstanceId = value.referenceInstanceId();
    }

    public Long getId() {
        return id;
    }

    public EntityInstance getEntityInstance() {
        return entityInstance;
    }

    public AttributeDefinition getAttributeDefinition() {
        return attributeDefinition;
    }

    public String getValueString() {
        return valueString;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public Boolean getValueBoolean() {
        return valueBoolean;
    }

    public Long getValueRefInstanceId() {
        return valueRefInstanceId;
    }

    /**
     * Canonical string form of whichever typed {@code value*} column is
     * populated — same convention as {@link AttributeValue#asDisplayString()}
     * (D-019), reading back from the already-persisted row rather than a
     * fresh {@link AttributeValue}. Needed by {@code EntityInstanceService}
     * (S3-T1, D-039) to re-read an already-written dimension attribute
     * value when assembling an {@code EntityRole.EXTENSION} instance's
     * composite business key.
     */
    public String asDisplayString() {
        if (valueString != null) {
            return valueString;
        }
        if (valueNumber != null) {
            return valueNumber.toPlainString();
        }
        if (valueDate != null) {
            return valueDate.toString();
        }
        if (valueBoolean != null) {
            return valueBoolean.toString();
        }
        if (valueRefInstanceId != null) {
            return valueRefInstanceId.toString();
        }
        throw new IllegalStateException("EntityInstanceAttributeValue has no value set");
    }
}
