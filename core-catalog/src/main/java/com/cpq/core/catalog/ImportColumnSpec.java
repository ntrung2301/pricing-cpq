package com.cpq.core.catalog;

/**
 * Describes one column a catalog CSV import expects to see in its header
 * (S2-T5, E1-04). Deliberately decoupled from {@code core-metamodel}'s
 * {@code AttributeDefinition} JPA entity — this module stays a standalone,
 * Spring-free parsing framework (per its own build.gradle.kts comment);
 * the {@code app}-side orchestration converts an entity definition's
 * attributes into a list of these before calling {@link CatalogCsvParser}.
 *
 * <p>{@code code} must match the CSV header cell *exactly* (D-005 — no UI
 * mapping, no case-insensitive/fuzzy matching).
 */
public record ImportColumnSpec(String code, boolean required) {}
