package com.cpq.app.pricing.dag;

import com.cpq.app.pricing.MeasureDefinitionRepository;
import com.cpq.core.pricing.dag.DagEngine;
import com.cpq.core.pricing.dag.DagExecutionResult;
import com.cpq.core.pricing.dag.DagNode;
import com.cpq.core.pricing.dag.DagNodeExecutor;
import com.cpq.core.pricing.dag.DagNodeExecutors;
import com.cpq.core.pricing.dag.DagValidationException;
import com.cpq.core.pricing.dag.TraceEntry;
import com.cpq.core.pricing.persistence.MeasureDefinitionEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * DAG node type {@code measure-ref} (ADR-009 mục 2 "Measure", backlog
 * E8-04, S3-T5): resolves one value by running a named, reusable subgraph
 * ({@link MeasureDefinitionEntity#getGraph()}) and reading its {@code
 * result_node_id}'s output — "Measure" in the DAX sense: defined once,
 * referenced from many top-level graphs, parameterized by whichever anchor
 * instance the *caller* is currently evaluating, never a hardcoded instance
 * baked into the measure's own definition.
 *
 * <p><b>Anchor parameterization (generalizes D-042's chosen default (a) for
 * {@code query}):</b> this node's own config carries {@code
 * anchorInstanceId} — the id the caller baked in when building ITS graph,
 * same convention as a plain {@code query} node. Before running the
 * measure's subgraph, every {@code query} node inside it that does NOT
 * already have its own {@code anchorInstanceId} (absent key, or present
 * with value {@code null}) gets this executor's {@code anchorInstanceId}
 * injected into a *new* {@link DagNode} (records are immutable — the
 * stored definition is never mutated). A {@code query} node that already
 * has a concrete anchor baked in is left untouched (e.g. it was
 * deliberately pinned to one instance by whoever authored the measure) —
 * this is how the *same* {@code measure_definition} row, run twice with
 * two different callers' anchors, produces two different correct results
 * with zero caching/contamination between calls (S3-T5 Done-khi).
 *
 * <p><b>Nested measure-ref not supported yet</b> (explicit task scope cut):
 * if the subgraph itself contains a {@code measure-ref} node ("measure
 * calling another measure"), this throws a clear {@link
 * DagValidationException} rather than recursing — flagged
 * NEEDS-PO-REVIEW in docs/decisions.md D-045 for a future task if a real
 * use case needs it.
 *
 * <p><b>Trace transclusion</b> cannot happen inside this class alone:
 * {@link DagNodeExecutor#execute} returns only this node's own output
 * value — {@link DagEngine} (core-pricing, not touched by this task)
 * builds its own {@code trace} list privately from that return value, with
 * no hook for an executor to inject extra entries into it. This executor
 * instead *records* the subgraph's trace (nodeId-prefixed, so two
 * different call sites referencing the same measure never collide) into a
 * per-execution, {@code ThreadLocal}-scoped buffer; {@link
 * MeasureAwareDagEngine} is the thin wrapper that resets this buffer before
 * a top-level run, calls the (unmodified) {@link DagEngine} normally, then
 * splices the buffer's contents into the result — see its Javadoc for the
 * full rationale and docs/decisions.md D-045.
 */
@Service
public class MeasureRefNodeExecutor implements DagNodeExecutor {

    static final String CONFIG_MEASURE_CODE = "measureCode";
    static final String CONFIG_ANCHOR_INSTANCE_ID = "anchorInstanceId";

    private final MeasureDefinitionRepository measureDefinitionRepository;
    private final QueryNodeExecutor queryNodeExecutor;

    /**
     * Per-thread, per-top-level-run buffer of transcluded trace entries,
     * keyed by the {@code measure-ref} node's own id (so {@link
     * MeasureAwareDagEngine} can splice each call site's subgraph trace
     * right after that call site's own {@link TraceEntry} in the top-level
     * trace). {@code MeasureAwareDagEngine} resets this before every
     * top-level {@code execute}, and drains (reads + clears) it right
     * after — never left dangling across calls.
     */
    private final ThreadLocal<Map<String, List<TraceEntry>>> transcludedTraceByCallSite =
            ThreadLocal.withInitial(LinkedHashMap::new);

    public MeasureRefNodeExecutor(
            MeasureDefinitionRepository measureDefinitionRepository, QueryNodeExecutor queryNodeExecutor) {
        this.measureDefinitionRepository = measureDefinitionRepository;
        this.queryNodeExecutor = queryNodeExecutor;
    }

    @Override
    public Object execute(DagNode node, Map<String, Object> resolvedInputs) {
        String measureCode = requireConfigString(node, CONFIG_MEASURE_CODE);
        Object anchorInstanceId = node.config().get(CONFIG_ANCHOR_INSTANCE_ID);
        if (anchorInstanceId == null) {
            throw new DagValidationException(
                    "measure-ref node '" + node.id() + "' requires config '" + CONFIG_ANCHOR_INSTANCE_ID + "'");
        }

        MeasureDefinitionEntity measure =
                measureDefinitionRepository
                        .findByCode(measureCode)
                        .orElseThrow(
                                () ->
                                        new DagValidationException(
                                                "measure-ref node '"
                                                        + node.id()
                                                        + "' references unknown measure '"
                                                        + measureCode
                                                        + "'"));

        List<DagNode> subgraph = measure.getGraph();
        requireNoNestedMeasureRef(node, measureCode, subgraph);
        List<DagNode> patched = injectAnchorIntoQueryNodesMissingOne(subgraph, anchorInstanceId);

        DagEngine subEngine = new DagEngine(subgraphRegistry());
        DagExecutionResult subResult = subEngine.execute(patched);

        if (!subResult.results().containsKey(measure.getResultNodeId())) {
            throw new DagValidationException(
                    "Measure '"
                            + measureCode
                            + "' result_node_id '"
                            + measure.getResultNodeId()
                            + "' does not exist in its own graph");
        }

        recordTranscludedTrace(node, measureCode, subResult.trace());
        return subResult.results().get(measure.getResultNodeId());
    }

    /**
     * The registry a measure's subgraph runs with: {@code query} (S3-T3) +
     * the finite production set (S3-T2) — deliberately NOT including
     * {@code measure-ref} itself (nested measure-ref is not supported yet,
     * see class Javadoc; {@link #requireNoNestedMeasureRef} already throws
     * a clearer, earlier error for that case, but omitting the registry
     * entry too means a graph that somehow slipped past that check would
     * still fail loudly via {@code DagEngine}'s own "unknown node type"
     * error rather than silently recursing).
     */
    private Map<String, DagNodeExecutor> subgraphRegistry() {
        Map<String, DagNodeExecutor> executors = new LinkedHashMap<>(DagNodeExecutors.standard());
        executors.put("query", queryNodeExecutor);
        return executors;
    }

    private void requireNoNestedMeasureRef(DagNode node, String measureCode, List<DagNode> subgraph) {
        boolean hasNestedMeasureRef = subgraph.stream().anyMatch(inner -> "measure-ref".equals(inner.type()));
        if (hasNestedMeasureRef) {
            throw new DagValidationException(
                    "measure-ref node '"
                            + node.id()
                            + "' references measure '"
                            + measureCode
                            + "', whose own graph contains a measure-ref node — "
                            + "a measure calling another measure (nested measure-ref) is not supported yet "
                            + "(S3-T5 scope cut, see docs/decisions.md)");
        }
    }

    /**
     * New {@link DagNode} per {@code query} node lacking its own {@code
     * anchorInstanceId} (absent or {@code null}) — never mutates the
     * stored definition, never overrides a {@code query} node that already
     * has one baked in.
     */
    private List<DagNode> injectAnchorIntoQueryNodesMissingOne(List<DagNode> nodes, Object anchorInstanceId) {
        List<DagNode> result = new ArrayList<>(nodes.size());
        for (DagNode inner : nodes) {
            if ("query".equals(inner.type()) && inner.config().get(CONFIG_ANCHOR_INSTANCE_ID) == null) {
                Map<String, Object> mergedConfig = new LinkedHashMap<>(inner.config());
                mergedConfig.put(CONFIG_ANCHOR_INSTANCE_ID, anchorInstanceId);
                result.add(new DagNode(inner.id(), inner.type(), mergedConfig, inner.inputs()));
            } else {
                result.add(inner);
            }
        }
        return result;
    }

    private void recordTranscludedTrace(DagNode node, String measureCode, List<TraceEntry> subTrace) {
        List<TraceEntry> prefixed = new ArrayList<>(subTrace.size());
        for (TraceEntry inner : subTrace) {
            String prefixedId = node.id() + ":" + measureCode + ":" + inner.nodeId();
            prefixed.add(new TraceEntry(prefixedId, inner.type(), inner.resolvedInputs(), inner.output()));
        }
        transcludedTraceByCallSite.get().put(node.id(), prefixed);
    }

    private String requireConfigString(DagNode node, String key) {
        Object value = node.config().get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new DagValidationException(
                    "measure-ref node '" + node.id() + "' requires a non-blank string config '" + key + "'");
        }
        return stringValue;
    }

    /** Called by {@link MeasureAwareDagEngine} right before a top-level run, so a previous run's leftovers (if any) never leak in. */
    void resetTranscludedTrace() {
        transcludedTraceByCallSite.remove();
    }

    /** Called by {@link MeasureAwareDagEngine} right after a top-level run: reads and clears this thread's buffer. */
    Map<String, List<TraceEntry>> drainTranscludedTrace() {
        Map<String, List<TraceEntry>> snapshot = transcludedTraceByCallSite.get();
        transcludedTraceByCallSite.remove();
        return snapshot;
    }
}
