package com.cpq.core.metamodel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A single typed value to write into an {@link EntityInstanceAttributeValue}
 * row (ADR-001's typed EAV). Exactly one of the typed fields is populated,
 * matching one {@link AttributeDataType}; core only knows these five
 * generic value shapes, never what the attribute actually represents.
 *
 * <p>Immutable value holder — built via the {@code of*} factory methods,
 * one per {@link AttributeDataType} family:
 * {@link AttributeDataType#STRING}/{@link AttributeDataType#ENUM}/
 * {@link AttributeDataType#JSON} share {@link #ofString(String)};
 * {@link AttributeDataType#NUMBER}/{@link AttributeDataType#DECIMAL} share
 * {@link #ofNumber(BigDecimal)}; {@link AttributeDataType#DATE} uses
 * {@link #ofDate(LocalDate)}; {@link AttributeDataType#BOOLEAN} uses
 * {@link #ofBoolean(boolean)}; {@link AttributeDataType#REFERENCE} uses
 * {@link #ofReferenceInstanceId(Long)}.
 */
public final class AttributeValue {

    private final String stringValue;
    private final BigDecimal numberValue;
    private final LocalDate dateValue;
    private final Boolean booleanValue;
    private final Long referenceInstanceId;

    private AttributeValue(
            String stringValue,
            BigDecimal numberValue,
            LocalDate dateValue,
            Boolean booleanValue,
            Long referenceInstanceId) {
        this.stringValue = stringValue;
        this.numberValue = numberValue;
        this.dateValue = dateValue;
        this.booleanValue = booleanValue;
        this.referenceInstanceId = referenceInstanceId;
    }

    public static AttributeValue ofString(String value) {
        return new AttributeValue(Objects.requireNonNull(value), null, null, null, null);
    }

    public static AttributeValue ofNumber(BigDecimal value) {
        return new AttributeValue(null, Objects.requireNonNull(value), null, null, null);
    }

    public static AttributeValue ofDate(LocalDate value) {
        return new AttributeValue(null, null, Objects.requireNonNull(value), null, null);
    }

    public static AttributeValue ofBoolean(boolean value) {
        return new AttributeValue(null, null, null, value, null);
    }

    public static AttributeValue ofReferenceInstanceId(Long instanceId) {
        return new AttributeValue(null, null, null, null, Objects.requireNonNull(instanceId));
    }

    /**
     * Whether this value matches the shape expected for {@code dataType}
     * (ADR-001: "validate theo attribute_def.data_type lúc viết").
     */
    public boolean matches(AttributeDataType dataType) {
        return switch (dataType) {
            case STRING, ENUM, JSON -> stringValue != null;
            case NUMBER, DECIMAL -> numberValue != null;
            case DATE -> dateValue != null;
            case BOOLEAN -> booleanValue != null;
            case REFERENCE -> referenceInstanceId != null;
        };
    }

    /**
     * Canonical string form used to populate the denormalized
     * {@code business_key}/{@code display_label} columns (D-019) — the
     * generic key/label mechanism works regardless of which typed column
     * actually holds the value.
     */
    public String asDisplayString() {
        if (stringValue != null) {
            return stringValue;
        }
        if (numberValue != null) {
            return numberValue.toPlainString();
        }
        if (dateValue != null) {
            return dateValue.toString();
        }
        if (booleanValue != null) {
            return booleanValue.toString();
        }
        if (referenceInstanceId != null) {
            return referenceInstanceId.toString();
        }
        throw new IllegalStateException("AttributeValue has no value set");
    }

    public String stringValue() {
        return stringValue;
    }

    public BigDecimal numberValue() {
        return numberValue;
    }

    public LocalDate dateValue() {
        return dateValue;
    }

    public Boolean booleanValue() {
        return booleanValue;
    }

    public Long referenceInstanceId() {
        return referenceInstanceId;
    }
}
