package com.cpq.core.api.pricing;

import java.math.BigDecimal;

/**
 * Generic REST projection of {@code com.cpq.core.pricing.WaterfallStep}
 * (E3-02: "price waterfall giải thích được") — core-api never returns the
 * core-pricing value type directly, same pattern as
 * {@code EntityDefinitionSummary} for core-metamodel.
 */
public record WaterfallStepSummary(
        int stepOrder,
        String stage,
        Long ruleId,
        String label,
        BigDecimal deltaAmount,
        BigDecimal resultingAmount,
        boolean rounded) {}
