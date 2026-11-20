package com.cpq.core.pricing.dag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic dataflow-graph (DAG) execution engine (ADR-009 mục 2, S3-T2):
 * given a static list of {@link DagNode}, derives execution order purely
 * from {@code inputs} references (Kahn's algorithm — never declaration
 * order), runs each node exactly once via the matching {@link
 * DagNodeExecutor}, and returns every node's output plus a full
 * Calculation Trace.
 *
 * <p>The engine itself never changes when a graph changes — it knows
 * nothing about {@code formula}/{@code round}/etc., only how to look a
 * type up in the {@link DagNodeExecutor} map it was constructed with. No
 * loop, no re-entrant/mutable state: each node is visited exactly once,
 * in topological order.
 */
public final class DagEngine {

    private final Map<String, DagNodeExecutor> executors;

    public DagEngine(Map<String, DagNodeExecutor> executors) {
        this.executors = Map.copyOf(executors);
    }

    public DagExecutionResult execute(List<DagNode> nodes) {
        Map<String, DagNode> byId = indexById(nodes);
        List<DagNode> sorted = topologicalSort(nodes, byId);

        Map<String, Object> results = new LinkedHashMap<>();
        List<TraceEntry> trace = new ArrayList<>(sorted.size());

        for (DagNode node : sorted) {
            Map<String, Object> resolvedInputs = resolveInputs(node, results);
            DagNodeExecutor executor = executors.get(node.type());
            if (executor == null) {
                throw new DagValidationException(
                        "Unknown DAG node type \"" + node.type() + "\" for node '" + node.id() + "'");
            }
            Object output = executor.execute(node, resolvedInputs);
            results.put(node.id(), output);
            trace.add(new TraceEntry(node.id(), node.type(), resolvedInputs, output));
        }

        return new DagExecutionResult(results, trace);
    }

    private static Map<String, DagNode> indexById(List<DagNode> nodes) {
        Map<String, DagNode> byId = new LinkedHashMap<>();
        for (DagNode node : nodes) {
            if (byId.put(node.id(), node) != null) {
                throw new DagValidationException("Duplicate DAG node id: '" + node.id() + "'");
            }
        }
        return byId;
    }

    /** Distinct node ids this node directly depends on, validated to exist in the graph. */
    private static Set<String> dependenciesOf(DagNode node, Map<String, DagNode> byId) {
        Set<String> deps = new LinkedHashSet<>(node.inputs().values());
        for (String dep : deps) {
            if (!byId.containsKey(dep)) {
                throw new DagValidationException(
                        "Node '" + node.id() + "' references unknown input node '" + dep + "'");
            }
        }
        return deps;
    }

    /** Kahn's algorithm: execution order derived purely from input references, never list order. */
    private static List<DagNode> topologicalSort(List<DagNode> nodes, Map<String, DagNode> byId) {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, Set<String>> dependents = new LinkedHashMap<>();
        for (DagNode node : nodes) {
            inDegree.put(node.id(), 0);
            dependents.put(node.id(), new LinkedHashSet<>());
        }
        for (DagNode node : nodes) {
            Set<String> deps = dependenciesOf(node, byId);
            inDegree.put(node.id(), deps.size());
            for (String dep : deps) {
                dependents.get(dep).add(node.id());
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (DagNode node : nodes) {
            if (inDegree.get(node.id()) == 0) {
                queue.add(node.id());
            }
        }

        List<DagNode> sorted = new ArrayList<>(nodes.size());
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(byId.get(id));
            for (String dependent : dependents.get(id)) {
                int remaining = inDegree.get(dependent) - 1;
                inDegree.put(dependent, remaining);
                if (remaining == 0) {
                    queue.add(dependent);
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            throw new DagCycleException("Graph has a cycle, not a valid DAG");
        }
        return sorted;
    }

    private static Map<String, Object> resolveInputs(DagNode node, Map<String, Object> results) {
        Map<String, Object> resolvedInputs = new LinkedHashMap<>();
        for (Map.Entry<String, String> input : node.inputs().entrySet()) {
            resolvedInputs.put(input.getKey(), results.get(input.getValue()));
        }
        return resolvedInputs;
    }
}
