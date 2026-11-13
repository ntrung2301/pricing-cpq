package com.cpq.core.metamodel;

/**
 * Generic, domain-agnostic attribute value types for the typed EAV
 * metamodel (ADR-001). Core only knows these 8 types — it has no idea what
 * a "part number" or a "VIN" is; a domain pack picks one of these when it
 * declares an {@link AttributeDefinition}.
 */
public enum AttributeDataType {
    STRING,
    NUMBER,
    DECIMAL,
    BOOLEAN,
    DATE,
    ENUM,
    REFERENCE,
    JSON
}
