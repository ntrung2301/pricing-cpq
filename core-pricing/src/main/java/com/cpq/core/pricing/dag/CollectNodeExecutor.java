package com.cpq.core.pricing.dag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code collect} node (S3-T7, ADR-009 mục 2's "mở rộng dần theo nhu cầu
 * thật, không làm hết 1 lần"): takes N named inputs — any resolved value,
 * typically each a {@code Map<String, Object>} "row" built by an {@code
 * output} node — and returns them as a single ordered {@code List<Object>}.
 * The "zip into a list" counterpart to {@code output} ("zip into a map").
 *
 * <p><b>Why this was added</b> (see docs/decisions.md D-047): a {@code
 * query} node (S3-T3, app) always collapses its {@code EXTENSION}/{@code
 * PARAMETER} candidates to exactly ONE value (filter -&gt; sort -&gt;
 * take-first, ADR-009 mục 3) — by design, it can never itself hand {@code
 * weighted-average} the {@code List<Map<String, Object>>} of rows the
 * latter needs, once those rows come from several *real* Calculation
 * Binding lookups (e.g. N periods of a real {@code CostHistory} read
 * through N separate {@code query} nodes, one per period). {@code
 * DagEngineTest}'s S3-T2 proof-of-concept sidestepped this by feeding
 * {@code weighted-average} a single literal/stand-in list directly — this
 * node is the missing, fully generic bridge for when the rows instead come
 * from the real entity-reading pipeline. Knows nothing about "cost"/
 * "period"/rows' business meaning, exactly like every other node type.
 */
final class CollectNodeExecutor implements DagNodeExecutor {

    @Override
    public Object execute(DagNode node, Map<String, Object> resolvedInputs) {
        return new ArrayList<>(resolvedInputs.values());
    }
}
