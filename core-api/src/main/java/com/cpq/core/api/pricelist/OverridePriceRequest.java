package com.cpq.core.api.pricelist;

import java.math.BigDecimal;

/**
 * Request body for {@code PATCH /api/price-lists-v2/items/{itemId}/override}
 * (S3-T6, ADR-009 mục 5) — {@code manualOverridePrice} is the ONLY field
 * any API in this codebase can change on an existing price_list_item_v2
 * row. {@code null} clears a previously set override.
 */
public record OverridePriceRequest(BigDecimal manualOverridePrice) {}
