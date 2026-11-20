package com.cpq.core.pricing.dag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of {@link DagEngine#execute}: every node's output keyed by node
 * id ({@code results}), plus the full, ordered Calculation Trace ({@code
 * trace}) — ADR-009 mục 2.
 */
public record DagExecutionResult(Map<String, Object> results, List<TraceEntry> trace) {

    public DagExecutionResult(Map<String, Object> results, List<TraceEntry> trace) {
        this.results = unmodifiableMapCopy(results);
        this.trace = List.copyOf(Objects.requireNonNull(trace));
    }

    private static Map<String, Object> unmodifiableMapCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
