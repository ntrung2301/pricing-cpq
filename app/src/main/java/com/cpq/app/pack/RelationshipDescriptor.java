package com.cpq.app.pack;

import com.cpq.core.metamodel.RelationshipCardinality;

/**
 * One {@code relationships[]} entry of {@code pack.yaml} (ADR-001).
 * {@code fromEntity}/{@code toEntity} are entity codes (may reference an
 * entity declared earlier in the same pack, or one from another pack
 * already loaded). Deserialized from YAML (snake_case keys), e.g.:
 *
 * <pre>
 * - code: supersedes
 *   from_entity: Product
 *   to_entity: Product
 *   cardinality: ONE_TO_MANY
 * </pre>
 */
public record RelationshipDescriptor(
        String code, String fromEntity, String toEntity, RelationshipCardinality cardinality) {}
