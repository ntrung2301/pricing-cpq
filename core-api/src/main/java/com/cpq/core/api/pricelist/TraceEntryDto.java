package com.cpq.core.api.pricelist;

import java.util.Map;

/**
 * Generic REST projection of {@code com.cpq.core.pricing.dag.TraceEntry}
 * (S3-T6) — same "core-api never returns the core-pricing value type
 * directly" convention as {@link DagNodeDto}/{@code WaterfallStepSummary}.
 * One entry of a {@link PriceListItemDto#waterfallSteps()} full
 * Calculation Trace.
 */
public record TraceEntryDto(String nodeId, String type, Map<String, Object> resolvedInputs, Object output) {}
