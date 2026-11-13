package com.cpq.app.metamodel;

/**
 * Thrown by {@link MetamodelDefinitionService#defineEntity} when two
 * {@code AttributeDefinition}s passed for the same {@code EntityDefinition}
 * share the same {@code code}. The database also enforces this (unique
 * (entity_def_id, code) constraint on {@code attribute_def}, see
 * {@code V2__metamodel_definitions.sql}), but failing fast here gives a
 * clear, generic domain error instead of a raw constraint-violation
 * exception bubbling up from the persistence layer.
 */
public class DuplicateAttributeCodeException extends RuntimeException {

    public DuplicateAttributeCodeException(String code) {
        super("Duplicate attribute code within the same entity definition: " + code);
    }
}
