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

    public AppDagNodeExecutors(QueryNodeExecutor queryNodeExecutor) {
        this.queryNodeExecutor = queryNodeExecutor;
    }

    /** The registry to construct a {@link DagEngine} with when a graph uses {@code query} nodes. */
    public Map<String, DagNodeExecutor> withQuery() {
        Map<String, DagNodeExecutor> executors = new LinkedHashMap<>(DagNodeExecutors.standard());
        executors.put("query", queryNodeExecutor);
        return executors;
    }
}
