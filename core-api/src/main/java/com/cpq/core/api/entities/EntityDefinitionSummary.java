package com.cpq.core.api.entities;

import java.util.List;

/**
 * Generic, domain-agnostic REST projection of {@code EntityDefinition}
 * (core-metamodel) for {@code GET /api/entities} (S1-T8). Core has no idea
 * whether a given row means "Part", "Vehicle", "SampleWidget", or anything
 * else (ADR-001) — this DTO carries only the generic fields, never the JPA
 * entity itself.
 */
public record EntityDefinitionSummary(
        Long id, String code, String label, int version, List<AttributeSummary> attributes) {
}
