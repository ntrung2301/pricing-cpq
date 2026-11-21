package com.cpq.app.pricing.dag;

import com.cpq.core.pricing.dag.DagEngine;
import com.cpq.core.pricing.dag.DagNodeExecutor;
import com.cpq.core.pricing.dag.DagNodeExecutors;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code Map<String, DagNodeExecutor>} a caller passes to
 * {@code new DagEngine(...)} when it needs the {@code query} node type
 * (S3-T3) in addition to core-pricing's finite production set ({@code
 * DagNodeExecutors.standard()}: {@code formula}/{@code weighted-average}/
 * {@code round}/{@code output}).
 *
 * <p>{@code query} is deliberately kept out of {@code
 * DagNodeExecutors.standard()} itself (core-pricing, D-039) because it
 * needs JPA repositories and {@code core-pricing} must stay
 * framework/DB-free — this class is the {@code app}-side glue that merges
 * the two, same pattern as the task description's guidance to "tự gộp vào
 * 1 Map&lt;String,DagNodeExecutor&gt; riêng khi dùng".
 */
@Component
public class AppDagNodeExecutors {

    private final QueryNodeExecutor queryNodeExecutor;
    private final MeasureRefNodeExecutor measureRefNodeExecutor;

    public AppDagNodeExecutors(QueryNodeExecutor queryNodeExecutor, MeasureRefNodeExecutor measureRefNodeExecutor) {
        this.queryNodeExecutor = queryNodeExecutor;
        this.measureRefNodeExecutor = measureRefNodeExecutor;
    }

    /** The registry to construct a {@link DagEngine} with when a graph uses {@code query} nodes. */
    public Map<String, DagNodeExecutor> withQuery() {
        Map<String, DagNodeExecutor> executors = new LinkedHashMap<>(DagNodeExecutors.standard());
        executors.put("query", queryNodeExecutor);
        return executors;
    }

    /**
     * {@code withQuery()} plus {@code measure-ref} (S3-T5) — the registry
     * for a TOP-LEVEL graph that may reference a Measure. Deliberately not
     * depended on by {@link MeasureRefNodeExecutor} itself for running a
     * measure's own subgraph (that would be a constructor cycle: this
     * class would need the executor, which would need this class) —
     * {@code MeasureRefNodeExecutor} builds its own minimal {@code
     * query}+standard registry directly instead (nested measure-ref isn't
     * supported yet anyway, so its subgraph registry never needs {@code
     * measure-ref}).
     */
    public Map<String, DagNodeExecutor> withQueryAndMeasure() {
        Map<String, DagNodeExecutor> executors = new LinkedHashMap<>(withQuery());
        executors.put("measure-ref", measureRefNodeExecutor);
        return executors;
    }
}
