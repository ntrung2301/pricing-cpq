package com.cpq.app.pricing.dag;

import com.cpq.core.pricing.dag.DagEngine;
import com.cpq.core.pricing.dag.DagExecutionResult;
import com.cpq.core.pricing.dag.DagNode;
import com.cpq.core.pricing.dag.TraceEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Entry point for running a top-level graph that may contain {@code
 * measure-ref} nodes (S3-T5) — a thin wrapper, NOT a change to {@link
 * DagEngine} itself (out of scope for this task; see docs/decisions.md
 * D-045).
 *
 * <p>{@link DagEngine} has no concept of "a node's executor contributed
 * extra trace entries" — its own {@code trace} list is built privately from
 * each {@link com.cpq.core.pricing.dag.DagNodeExecutor#execute} call's
 * single return value. {@link MeasureRefNodeExecutor} therefore cannot hand
 * the subgraph's {@link TraceEntry} list back through the normal return
 * path (that path IS the node's output value, consumed by downstream
 * nodes — it must stay a plain scalar for composability, e.g. feeding a
 * {@code round} node). Instead it stashes those entries, keyed by its own
 * node id, in a {@code ThreadLocal} buffer (see its Javadoc) that only this
 * class touches:
 *
 * <ol>
 *   <li>Reset the buffer (a previous, unrelated run on this thread must
 *       never leak in).
 *   <li>Run the graph through an ordinary, unmodified {@link DagEngine}.
 *   <li>Drain the buffer, and splice each call site's subgraph trace
 *       immediately after that call site's own entry in the returned
 *       trace — "transcluded", per ADR-009 mục 2, not a black box.
 * </ol>
 */
@Component
public class MeasureAwareDagEngine {

    private final AppDagNodeExecutors appDagNodeExecutors;
    private final MeasureRefNodeExecutor measureRefNodeExecutor;

    public MeasureAwareDagEngine(AppDagNodeExecutors appDagNodeExecutors, MeasureRefNodeExecutor measureRefNodeExecutor) {
        this.appDagNodeExecutors = appDagNodeExecutors;
        this.measureRefNodeExecutor = measureRefNodeExecutor;
    }

    public DagExecutionResult execute(List<DagNode> nodes) {
        measureRefNodeExecutor.resetTranscludedTrace();
        DagEngine engine = new DagEngine(appDagNodeExecutors.withQueryAndMeasure());
        DagExecutionResult result = engine.execute(nodes);
        Map<String, List<TraceEntry>> transcludedByCallSite = measureRefNodeExecutor.drainTranscludedTrace();

        if (transcludedByCallSite.isEmpty()) {
            return result;
        }

        List<TraceEntry> merged = new ArrayList<>(result.trace().size());
        for (TraceEntry entry : result.trace()) {
            merged.add(entry);
            List<TraceEntry> transcluded = transcludedByCallSite.get(entry.nodeId());
            if (transcluded != null) {
                merged.addAll(transcluded);
            }
        }
        return new DagExecutionResult(result.results(), merged);
    }
}
