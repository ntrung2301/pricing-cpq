package com.cpq.core.pricing.dag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * {@code weighted-average} node (ADR-009 mục 2, S3-T2): takes exactly one
 * input — a {@code List<Map<String, Object>>} of "rows" (e.g. 12 months
 * of {@code CostHistory}, per the Level 4 pricing example ADR-009 cites)
 * — and config {@code valueField}/{@code weightField} naming which field
 * in each row holds the value and which holds the weight. Returns
 * {@code sum(value*weight) / sum(weight)} as a {@link BigDecimal}.
 */
final class WeightedAverageNodeExecutor implements DagNodeExecutor {

    private static final int RESULT_SCALE = 10;

    @Override
    public Object execute(DagNode node, Map<String, Object> resolvedInputs) {
        String valueField = requireStringConfig(node, "valueField");
        String weightField = requireStringConfig(node, "weightField");
        List<Map<String, Object>> rows = singleListInput(node, resolvedInputs);

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal value = requireBigDecimal(node, row.get(valueField), valueField);
            BigDecimal weight = requireBigDecimal(node, row.get(weightField), weightField);
            weightedSum = weightedSum.add(value.multiply(weight));
            weightSum = weightSum.add(weight);
        }
        if (weightSum.compareTo(BigDecimal.ZERO) == 0) {
            throw new DagValidationException(
                    "weighted-average node '" + node.id() + "' has zero total weight, cannot divide");
        }
        return weightedSum.divide(weightSum, RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private static String requireStringConfig(DagNode node, String field) {
        Object value = node.config().get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new DagValidationException(
                    "weighted-average node '" + node.id() + "' requires config \"" + field + "\" as a non-blank string");
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> singleListInput(DagNode node, Map<String, Object> resolvedInputs) {
        if (resolvedInputs.size() != 1) {
            throw new DagValidationException(
                    "weighted-average node '" + node.id() + "' requires exactly one input, got " + resolvedInputs.keySet());
        }
        Object only = resolvedInputs.values().iterator().next();
        if (!(only instanceof List<?> list)) {
            throw new DagValidationException(
                    "weighted-average node '" + node.id() + "' input must be a list of rows, got " + only);
        }
        return (List<Map<String, Object>>) list;
    }

    private static BigDecimal requireBigDecimal(DagNode node, Object value, String field) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        throw new DagValidationException(
                "weighted-average node '" + node.id() + "' row field \"" + field + "\" is not a BigDecimal: " + value);
    }
}
