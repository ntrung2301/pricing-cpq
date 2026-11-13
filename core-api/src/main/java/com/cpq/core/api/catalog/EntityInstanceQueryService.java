package com.cpq.core.api.catalog;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Port (in the hexagonal sense) that {@link CatalogInstanceController}
 * depends on for {@code GET /api/catalog/{entityDefCode}/instances}
 * (S2-T9-prereq — see docs/decisions.md D-030). Same
 * interface-in-core-api/implementation-in-app split, for the same ADR-005
 * reason, as {@code com.cpq.core.api.entities.EntityDefinitionQueryService}
 * (S1-T8) and {@code RelationshipInstanceQueryService} (S2-T7): the real
 * implementation needs {@code EntityDefinitionRepository}/{@code
 * EntityInstanceRepository}/{@code EntityInstanceAttributeValueRepository},
 * all of which live in {@code app} (the composition root that owns
 * DB-facing wiring).
 *
 * <p>{@code @PreAuthorize} is declared here, on the interface method, for
 * the same JDK-dynamic-proxy reason documented on {@code
 * EntityDefinitionQueryService}.
 *
 * <p>Per this task's scope decision (docs/decisions.md D-030): this is
 * read-only catalog data, same RBAC level as {@code GET /api/entities}
 * (D-018 mục 1) — any of the 3 core roles may call this, unlike {@code
 * CatalogImportService}'s {@code PRICING_ADMIN}-only write action.
 */
public interface EntityInstanceQueryService {

    /**
     * Lists {@code entity_instance} rows belonging to the entity_def named
     * {@code entityDefCode}, paginated ({@code page} 0-based, {@code size}
     * items per page), optionally filtered by a case-insensitive substring
     * match over {@code businessKey}/{@code displayLabel} ({@code search},
     * may be {@code null}/blank for no filter). Throws a 404 {@code
     * ResponseStatusException} when {@code entityDefCode} names no
     * existing entity_def.
     */
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'PRICING_ADMIN')")
    EntityInstancePageDto listInstances(String entityDefCode, int page, int size, String search);
}
