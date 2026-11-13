package com.cpq.core.api.pricing;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Port {@link PricingController} depends on for
 * {@code POST /api/pricing/calculate} (S2-T10/E3-02) — same
 * interface-in-core-api/implementation-in-app split as
 * {@code EntityDefinitionQueryService} (S1-T8), for the same ADR-005
 * reason: the real implementation needs repositories that live in
 * {@code app}.
 *
 * <p>Any of the 3 core roles may call this (read-only price calculation,
 * no state change) — same scope decision as {@code GET /api/entities}.
 */
public interface PricingCalculationService {

    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'PRICING_ADMIN')")
    PriceCalculationResponse calculate(PriceCalculationRequest request);
}
