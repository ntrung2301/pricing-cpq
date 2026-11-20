package com.cpq.core.pricing.dag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single node of a pricing calculation DAG (ADR-009 mục 2): pure data,
 * never a Java class per calculation ("node type X nhận input gì, trả
 * output gì" — engine is generic, meaning is config/data, not code).
 *
 * <ul>
 *   <li>{@code id} — unique within the graph it belongs to.
 *   <li>{@code type} — looked up in a {@link DagNodeExecutor} registry
 *       (finite production set: {@link DagNodeExecutors#standard()}).
 *   <li>{@code config} — node-type-specific settings (e.g. a {@code
 *       formula} node's expression, a {@code round} node's {@code
 *       nearest}). Opaque to the engine itself.
 *   <li>{@code inputs} — maps an input name (meaningful to the node type,
 *       e.g. {@code value}) to the id of another node in the same graph
 *       that supplies it. This is the only place edges are expressed —
 *       the engine derives execution order from it (topological sort),
 *       never from list position.
 * </ul>
 *
 * <p>Immutable by construction — {@code config}/{@code inputs} are
 * defensively copied into unmodifiable maps, so a graph is a static
 * structure throughout execution (ADR-009: "không loop, không biến mutable").
 */
public record DagNode(String id, String type, Map<String, Object> config, Map<String, String> inputs) {

    public DagNode(String id, String type, Map<String, Object> config, Map<String, String> inputs) {
        this.id = Objects.requireNonNull(id, "DagNode id must not be null");
        this.type = Objects.requireNonNull(type, "DagNode type must not be null");
        this.config = unmodifiableCopy(config);
        this.inputs = unmodifiableCopy(inputs);
    }

    private static <K, V> Map<K, V> unmodifiableCopy(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
