package com.cpq.core.api.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response body for {@code POST /api/pricing/calculate}: the full
 * waterfall plus the final amount for convenience (E3-02 round-trip:
 * summing {@code steps[*].deltaAmount} must equal {@code finalAmount}).
 */
public record PriceCalculationResponse(List<WaterfallStepSummary> steps, BigDecimal finalAmount) {}
