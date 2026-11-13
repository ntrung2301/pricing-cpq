package com.cpq.core.metamodel;

/**
 * Generic cardinality taxonomy for {@link RelationshipDefinition} (ADR-001).
 * Core only knows these three shapes — a domain pack's relationship, such
 * as a self-referencing "supersedes" or a "contains" between two different
 * entity definitions (ADR-003 examples), is just a row picking one of
 * these; core has no concept of what either relationship means.
 */
public enum RelationshipCardinality {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_MANY
}
