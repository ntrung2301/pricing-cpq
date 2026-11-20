package com.cpq.core.pricing.dag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code output} node (ADR-009 mục 2, S3-T2): passes through all of its
 * named inputs verbatim as a {@code Map<String, Object>}. Supports any
 * number of named outputs from one graph (e.g. {@code price} +
 * {@code marginRiskLevel} from the same Price List calculation, per
 * backlog E8-03) — not limited to one fixed {@code finalPrice} field.
 */
final class OutputNodeExecutor implements DagNodeExecutor {

    @Override
    public Object execute(DagNode node, Map<String, Object> resolvedInputs) {
        return new LinkedHashMap<>(resolvedInputs);
    }
}
