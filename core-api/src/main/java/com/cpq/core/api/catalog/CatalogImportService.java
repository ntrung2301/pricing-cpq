package com.cpq.core.api.catalog;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Port that {@link ImportController} depends on for {@code POST
 * /api/catalog/{entityDefCode}/import} (S2-T5, E1-04). Declared as an
 * interface here in {@code core-api}, implemented in {@code app} (
 * {@code com.cpq.app.catalog.CatalogImportServiceImpl}) — same split, for
 * the same ADR-005 reason, as {@code EntityDefinitionQueryService}: the
 * real implementation needs {@code EntityDefinitionRepository} /
 * {@code ImportJobRepository} / {@code EntityInstanceService}, all of
 * which live in {@code app} (the composition root that owns DB-facing
 * wiring).
 *
 * <p>{@code @PreAuthorize} is declared here, on the interface method, for
 * the same JDK-dynamic-proxy reason documented on
 * {@code EntityDefinitionQueryService}. Per this task's scope decision
 * (ADR-006, docs/decisions.md): importing catalog data mutates master
 * data, so only {@code PRICING_ADMIN} may call this — unlike {@code
 * EntityDefinitionQueryService}'s read-only "any of the 3 roles".
 */
public interface CatalogImportService {

    @PreAuthorize("hasRole('PRICING_ADMIN')")
    ImportResultDto importCsv(String entityDefCode, String fileName, String csvContent);
}
