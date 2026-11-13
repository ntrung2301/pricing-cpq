package com.cpq.core.api.entities;

/**
 * Generic, domain-agnostic REST projection of {@code AttributeDefinition}
 * (core-metamodel) for {@code GET /api/entities} (S1-T8). Deliberately a
 * plain DTO, not the JPA entity itself — core-api must never leak JPA
 * types over HTTP (ADR-005 §2: core-api has no business logic/persistence
 * concerns of its own).
 */
public record AttributeSummary(String code, String label, String dataType, boolean required) {
}
