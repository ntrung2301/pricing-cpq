package com.cpq.core.api.pricelist;

import java.util.Map;

/**
 * Generic REST projection of {@code com.cpq.core.pricing.dag.DagNode}
 * (S3-T6) — {@code core-api} never returns/accepts the {@code core-pricing}
 * value type directly, same pattern as {@code WaterfallStepSummary} for
 * {@code com.cpq.core.pricing.WaterfallStep} (S2-T10/E3-02). The caller
 * ({@code PriceListServiceImpl}, {@code app}) converts between this DTO
 * and a real {@code DagNode} — the shape is deliberately identical
 * (plain data: id/type/config/inputs), since a DAG node IS pure data by
 * design (ADR-009 mục 2).
 */
public record DagNodeDto(String id, String type, Map<String, Object> config, Map<String, String> inputs) {}
