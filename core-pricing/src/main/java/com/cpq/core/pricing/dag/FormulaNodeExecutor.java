package com.cpq.core.pricing.dag;

import com.cpq.core.pricing.dsl.JsonLogicEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;

/**
 * {@code formula} node (ADR-009 mục 4, S3-T2): computes a numeric value
 * from a JsonLogic-ish {@code expression} (config), where {@code {"var":
 * "name"}} resolves against this node's {@code resolvedInputs} — i.e. the
 * expression's variable names are exactly this node's declared input
 * names. Delegates all parsing/arithmetic to {@link JsonLogicEvaluator}
 * (core-pricing/dsl, extended — not rewritten — for this).
 */
final class FormulaNodeExecutor implements DagNodeExecutor {

    @Override
    public Object execute(DagNode node, Map<String, Object> resolvedInputs) {
        Object expressionConfig = node.config().get("expression");
        if (!(expressionConfig instanceof JsonNode expression)) {
            throw new DagValidationException(
                    "formula node '" + node.id() + "' requires config \"expression\" as a JSON expression");
        }
        return JsonLogicEvaluator.evaluateNumeric(expression, resolvedInputs);
    }
}
