package com.cpq.core.api.pricing;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request body for {@code POST /api/pricing/calculate} (S2-T10, E3-02,
 * minimal scope — no customer tier yet, that is S2-T12). Generic by
 * design: {@code attributes} is an arbitrary bag of values the caller
 * (e.g. a future configurator screen) has already resolved — core-api
 * has no idea what a "Customer" or "tier" is.
 *
 * @param ruleSetCode {@code rule_set.code} to run.
 * @param priceListCode {@code price_list.code} to look up the starting
 *     list price from.
 * @param entityDefId {@code entity_def.id} the price list item is
 *     looked up under (ADR-003).
 * @param entityInstanceId optional {@code entity_instance.id} for an
 *     instance-specific price list item; {@code null} falls back to the
 *     segment-level item for {@code entityDefId} (ADR-003: "nullable
 *     nghĩa là áp theo điều kiện/segment").
 * @param quantity quantity being priced.
 * @param attributes extra context values the DSL/Script rules may read
 *     (e.g. a tier attribute) — plain JSON values (string/number/boolean).
 */
public record PriceCalculationRequest(
        String ruleSetCode,
        String priceListCode,
        Long entityDefId,
        Long entityInstanceId,
        BigDecimal quantity,
        Map<String, Object> attributes) {}
