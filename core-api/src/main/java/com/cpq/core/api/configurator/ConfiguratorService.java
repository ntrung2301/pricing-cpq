package com.cpq.core.api.configurator;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Port (in the hexagonal sense) that {@link ConfiguratorController} depends
 * on for the configurator engine (S2-T8, E2-01) — same
 * interface-in-core-api/implementation-in-app split as
 * {@code com.cpq.core.api.entities.EntityDefinitionQueryService} (S1-T8)
 * and {@code com.cpq.core.api.pricing.PricingCalculationService} (S2-T10),
 * for the same ADR-005 reason: the real implementation needs
 * {@code core-metamodel}/JPA repositories that live in {@code app}.
 *
 * <p>{@code @PreAuthorize} is declared here, on the interface method, for
 * the same reason documented on {@code EntityDefinitionQueryService}
 * (works regardless of JDK-dynamic vs CGLIB method-security proxying).
 * Both operations are read-only configurator actions available to any of
 * the 3 core roles (not pricing/approval-sensitive), same scope decision
 * as {@code GET /api/entities} and {@code POST /api/pricing/calculate}.
 */
public interface ConfiguratorService {

    /**
     * Validates a set of selected {@code entity_instance} ids against every
     * {@code constraint_instance} whose two endpoints are both present in
     * {@code request.entityInstanceIds()} (D-024: a constraint_instance row
     * is an assertion between two specific instances, not a rule applying
     * to many pairs). A selection with fewer than 2 ids, or with no
     * relevant constraint_instance at all, is valid.
     */
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'PRICING_ADMIN')")
    ValidateSelectionResponse validateSelection(ValidateSelectionRequest request);

    /**
     * Lists every existing {@code entity_instance} of
     * {@code targetEntityDefCode} that has a passing
     * {@code "compatibility"}-typed {@code constraint_instance} with
     * {@code anchorInstanceId} (on either side of the constraint_instance's
     * x/y pair). Pure lookup over constraint_instance rows that already
     * exist — it does not synthesize new compatibility assertions.
     */
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'PRICING_ADMIN')")
    List<CompatibleInstanceSummary> listCompatibleInstances(Long anchorInstanceId, String targetEntityDefCode);
}
