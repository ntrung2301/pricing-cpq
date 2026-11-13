package com.cpq.core.api.pricing;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request body for {@code POST /api/pricing/calculate} (S2-T10/E3-02,
 * extended by S2-T12/E3-04 with customer-tier + contract-override
 * support). Generic by design: {@code attributes} is an arbitrary bag of
 * values the caller (e.g. a future configurator screen) has already
 * resolved — core-api has no idea what a "Customer" or "tier" is.
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
 * @param customerInstanceId optional {@code entity_instance.id} of a
 *     Customer-like instance (S2-T12/E3-04). When present, the {@code app}
 *     layer looks up that instance's {@code tier} attribute value and
 *     injects it into {@code attributes} under the key {@code "tier"}
 *     before running the rule engine — {@code core-api}/{@code
 *     core-pricing} never resolve this themselves, they only carry the id
 *     through. {@code null} means no customer context (same behavior as
 *     S2-T10: rules reading {@code tier} simply won't match).
 * @param contractOverridePrice optional per-request contract price
 *     (S2-T12/E3-04) that should override whatever the tier rule would
 *     otherwise produce. Injected into {@code attributes} under the key
 *     {@code "contractOverridePrice"} the same way as {@code tier} — a
 *     deliberate simplification of E3-04's "giá hợp đồng riêng" over
 *     reading a dedicated Customer attribute: it avoids needing a new
 *     {@code pack.yaml} attribute (and the matching entity-instance setup)
 *     just to exercise the override stage in MVP. See D-028
 *     (docs/decisions.md).
 */
public record PriceCalculationRequest(
        String ruleSetCode,
        String priceListCode,
        Long entityDefId,
        Long entityInstanceId,
        BigDecimal quantity,
        Map<String, Object> attributes,
        Long customerInstanceId,
        BigDecimal contractOverridePrice) {

    /**
     * Backward-compatible constructor for the S2-T10 shape (no tier/
     * contract-override fields) — keeps existing callers/tests compiling
     * unchanged; {@code customerInstanceId}/{@code contractOverridePrice}
     * default to {@code null} (same as "not provided").
     */
    public PriceCalculationRequest(
            String ruleSetCode,
            String priceListCode,
            Long entityDefId,
            Long entityInstanceId,
            BigDecimal quantity,
            Map<String, Object> attributes) {
        this(ruleSetCode, priceListCode, entityDefId, entityInstanceId, quantity, attributes, null, null);
    }
}
