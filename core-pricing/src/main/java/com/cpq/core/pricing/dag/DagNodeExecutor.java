package com.cpq.core.pricing.dag;

import java.util.Map;

/**
 * Behaviour for one DAG node type (ADR-009 mục 2). Implementations are
 * registered by type name in a {@code Map<String, DagNodeExecutor>} (see
 * {@link DagNodeExecutors#standard()} for the finite production set) —
 * {@link DagEngine} itself knows nothing about any specific type, only how
 * to look one up and call it in topological order.
 *
 * <p>An executor must be a pure function of {@code node} and {@code
 * resolvedInputs} — no I/O, no hidden state, no randomness/current time —
 * so that running the same graph twice always produces the same output
 * and trace (CLAUDE.md mục 7 / ADR-009 determinism requirement).
 */
public interface DagNodeExecutor {

    /**
     * @param node the node being executed (its {@code config} is the only
     *     node-type-specific input besides {@code resolvedInputs})
     * @param resolvedInputs for each entry of {@code node.inputs()}, the
     *     already-computed output of the referenced node, keyed by the
     *     *input name* (not the source node id)
     * @return this node's output value — becomes available to downstream
     *     nodes under this node's id, and is recorded verbatim in the
     *     Calculation Trace
     */
    Object execute(DagNode node, Map<String, Object> resolvedInputs);
}
