package com.cpq.core.pricing.dag;

import java.util.Map;

/**
 * The finite, production node type registry (ADR-009 mục 2, S3-T2):
 * {@code formula}, {@code weighted-average}, {@code round}, {@code
 * output}, {@code collect} (S3-T7, see {@link CollectNodeExecutor}).
 * Deliberately NOT {@code query}/{@code measure-ref} — those read
 * entities (S3-T3) or transclude a named subgraph (S3-T5), both separate
 * tasks that build on this engine, not part of it.
 *
 * <p>{@link DagEngine} does not depend on this class — it only depends on
 * {@code Map<String, DagNodeExecutor>}, handed to it by the caller. This
 * class is just the one production wiring of that map; tests (and later,
 * S3-T3+) are free to pass a different/larger map (e.g. this map plus a
 * {@code query} entry) without touching the engine.
 */
public final class DagNodeExecutors {

    private DagNodeExecutors() {}

    public static Map<String, DagNodeExecutor> standard() {
        return Map.of(
                "formula", new FormulaNodeExecutor(),
                "weighted-average", new WeightedAverageNodeExecutor(),
                "round", new RoundNodeExecutor(),
                "output", new OutputNodeExecutor(),
                "collect", new CollectNodeExecutor());
    }
}
