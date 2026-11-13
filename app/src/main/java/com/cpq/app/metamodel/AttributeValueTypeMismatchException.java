package com.cpq.app.metamodel;

import com.cpq.core.metamodel.AttributeDataType;

/**
 * Thrown by {@link EntityInstanceService#writeAttributeValue} when the
 * {@code AttributeValue} being written does not match the attribute
 * definition's declared {@code data_type} (ADR-001: "validate theo
 * attribute_def.data_type lúc viết").
 */
public class AttributeValueTypeMismatchException extends RuntimeException {

    public AttributeValueTypeMismatchException(String attributeCode, AttributeDataType expected) {
        super(
                "Value for attribute '"
                        + attributeCode
                        + "' does not match its declared data_type "
                        + expected);
    }
}
