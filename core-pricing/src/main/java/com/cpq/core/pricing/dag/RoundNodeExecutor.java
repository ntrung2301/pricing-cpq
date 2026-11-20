package com.cpq.core.pricing.dag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * {@code round} node (ADR-009 mục 2, S3-T2): rounds input {@code value} UP
 * (CEILING) to the nearest multiple of config {@code nearest} — e.g.
 * {@code nearest=1} rounds up to the nearest whole unit, {@code
 * nearest=0.05} to the nearest 5 cents.
 */
final class RoundNodeExecutor implements DagNodeExecutor {

    @Override
    public Object execute(DagNode node, Map<String, Object> resolvedInputs) {
        BigDecimal value = requireBigDecimalInput(node, resolvedInputs, "value");
        BigDecimal nearest = requireBigDecimalConfig(node, "nearest");
        if (nearest.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DagValidationException(
                    "round node '" + node.id() + "' config \"nearest\" must be positive, got " + nearest);
        }
        BigDecimal multiples = value.divide(nearest, 0, RoundingMode.CEILING);
        return multiples.multiply(nearest);
    }

    private static BigDecimal requireBigDecimalInput(DagNode node, Map<String, Object> resolvedInputs, String name) {
        Object value = resolvedInputs.get(name);
        if (!(value instanceof BigDecimal bigDecimal)) {
            throw new DagValidationException(
                    "round node '" + node.id() + "' requires input \"" + name + "\" as a BigDecimal, got " + value);
        }
        return bigDecimal;
    }

    private static BigDecimal requireBigDecimalConfig(DagNode node, String field) {
        Object value = node.config().get(field);
        if (!(value instanceof BigDecimal bigDecimal)) {
            throw new DagValidationException(
                    "round node '" + node.id() + "' requires config \"" + field + "\" as a BigDecimal, got " + value);
        }
        return bigDecimal;
    }
}
