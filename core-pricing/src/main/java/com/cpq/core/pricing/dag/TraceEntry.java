package com.cpq.core.pricing.dag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One entry of a Calculation Trace (ADR-009 mục 2) — the DAG engine's
 * generalisation of ADR-004's price waterfall: every node that ran, in
 * execution order, with exactly the inputs it resolved and the output it
 * produced. Nothing is summarised away — a Measure (S3-T5) transcludes
 * these entries verbatim into the caller's trace, so it is "never a black
 * box" even when reused across graphs.
 */
public record TraceEntry(String nodeId, String type, Map<String, Object> resolvedInputs, Object output) {

    public TraceEntry(String nodeId, String type, Map<String, Object> resolvedInputs, Object output) {
        this.nodeId = Objects.requireNonNull(nodeId);
        this.type = Objects.requireNonNull(type);
        this.resolvedInputs = unmodifiableCopy(resolvedInputs);
        this.output = output;
    }

    private static Map<String, Object> unmodifiableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
