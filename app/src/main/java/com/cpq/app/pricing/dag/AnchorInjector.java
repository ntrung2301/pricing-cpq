package com.cpq.app.pricing.dag;

import com.cpq.core.pricing.dag.DagNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared "bake {@code anchorInstanceId} into nodes missing one" algorithm
 * (D-042's chosen default (a), generalized by D-045 for {@code
 * measure-ref}, generalized again here for S3-T6 — see
 * docs/decisions.md D-046): given a list of {@link DagNode}, returns a new
 * list where every node whose {@code type} is in {@code
 * nodeTypesNeedingAnchor} AND whose {@code config} does not already carry
 * a non-null {@code anchorInstanceId} gets a NEW {@link DagNode} (records
 * are immutable — the input list/nodes are never mutated) with that key
 * merged into its config. A node that already has a concrete anchor baked
 * in is left untouched.
 *
 * <p>Extracted out of {@code MeasureRefNodeExecutor} (which used this
 * exact algorithm, hardcoded to the single node type {@code "query"}, to
 * parameterize a Measure's subgraph by its caller's anchor — see its
 * Javadoc and D-045) rather than duplicated, per this task's "tự quyết"
 * choice: a Price List's top-level {@code calculation_graph} can contain
 * BOTH {@code query} nodes (S3-T3) and {@code measure-ref} nodes (S3-T5)
 * needing the item's {@code entity_instance_id} injected as their anchor
 * — one caller, two node types, same mechanism — so the two call sites
 * (here, and {@code MeasureRefNodeExecutor}) now both delegate to this one
 * implementation instead of each maintaining their own copy.
 */
public final class AnchorInjector {

    public static final String CONFIG_ANCHOR_INSTANCE_ID = "anchorInstanceId";

    private AnchorInjector() {}

    public static List<DagNode> injectAnchorIntoNodesMissingOne(
            List<DagNode> nodes, Object anchorInstanceId, Set<String> nodeTypesNeedingAnchor) {
        List<DagNode> result = new ArrayList<>(nodes.size());
        for (DagNode node : nodes) {
            if (nodeTypesNeedingAnchor.contains(node.type())
                    && node.config().get(CONFIG_ANCHOR_INSTANCE_ID) == null) {
                Map<String, Object> mergedConfig = new LinkedHashMap<>(node.config());
                mergedConfig.put(CONFIG_ANCHOR_INSTANCE_ID, anchorInstanceId);
                result.add(new DagNode(node.id(), node.type(), mergedConfig, node.inputs()));
            } else {
                result.add(node);
            }
        }
        return result;
    }
}
