package com.cpq.core.api.catalog;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Port (in the hexagonal sense) that {@link CatalogRelationshipController}
 * depends on (S2-T7) — same interface-in-core-api/implementation-in-app
 * split as {@code com.cpq.core.api.configurator.ConfiguratorService}
 * (S2-T8), for the same ADR-005 reason: the real implementation needs
 * {@code core-metamodel}/JPA repositories that live in {@code app}.
 *
 * <p>{@code @PreAuthorize} is declared here for the same reason documented
 * on {@code ConfiguratorService} (works regardless of JDK-dynamic vs
 * CGLIB method-security proxying).
 */
public interface RelationshipInstanceQueryService {

    /**
     * Every {@code entity_instance} that supersedes/replaces
     * {@code instanceId}, found via a {@code "supersedes"}-typed
     * {@code relationship_instance} whose "to" side is {@code
     * instanceId} (S2-T7, ADR-003). An instance with no such row
     * (never superseded, or not a "Product"-like entity at all) simply
     * returns an empty list — this is a pure lookup, not a validation.
     */
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'PRICING_ADMIN')")
    List<SupersedingInstanceSummary> findSupersededBy(Long instanceId);
}
