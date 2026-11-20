package com.cpq.core.pricing.dsl;

import com.cpq.core.pricing.RuleDefinitionRejectedException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Minimal evaluator for the JsonLogic-flavoured grammar ADR-002 specifies
 * for {@code rule_type: DSL} conditions: {@code ==, !=, >, >=, <, <=, in,
 * and, or, not}, plus {@code {"var": "name"}} variable lookup. Three
 * reserved variable names resolve to {@link com.cpq.core.pricing.PricingContext}
 * fields directly ({@code quantity}, {@code basePrice}, {@code
 * currentAmount}); any other name is looked up in {@code attributes}.
 *
 * <p>ADR-009 mục 4 (S3-T2) extends this same parser/dispatch — not a
 * rewrite — to also compute a numeric ({@link BigDecimal}) result via the
 * arithmetic operators JsonLogic already defines ({@code + - * /}), for use
 * by the DAG engine's {@code formula} node. {@link #test} (boolean,
 * conditions) and {@link #evaluateNumeric} (BigDecimal, values) both go
 * through the same {@code evaluate}/{@code applyOperator} dispatch below —
 * only the operator set and the expected result type differ.
 *
 * <p>Deliberately NOT a general JsonLogic implementation (no {@code map},
 * {@code reduce}, string concatenation, etc.) — only what a pricing rule
 * condition or a DAG formula node needs, per ADR-002/ADR-009's choice of
 * "vay mượn ngữ pháp operator của JsonLogic" rather than adopting the full
 * spec/library.
 */
public final class JsonLogicEvaluator {

    /**
     * Internal scale for {@code "/"} division results (ADR-009 mục 4 does
     * not mandate a specific scale for generic DAG arithmetic — unlike
     * {@code DslRuleExecutor}'s rule-action waterfall, a formula node's
     * output commonly feeds into further nodes, e.g. {@code round}, which
     * do their own scale/rounding, so this only needs to be "enough
     * precision to not lose information before the next node", not a
     * final display scale). Chosen as a reasonable default (NEEDS-PO-REVIEW
     * if a different convention emerges once Price List formulas are built
     * for real in S3-T6/T7) — see docs/decisions.md D-039.
     */
    private static final int ARITHMETIC_SCALE = 10;

    private JsonLogicEvaluator() {}

    /** Evaluates a condition node to a boolean. A null/missing node means "always true". */
    static boolean test(JsonNode conditions, Map<String, Object> variables) {
        if (conditions == null || conditions.isNull() || conditions.isMissingNode()) {
            return true;
        }
        Object result = evaluate(conditions, variables);
        if (result instanceof Boolean bool) {
            return bool;
        }
        throw new RuleDefinitionRejectedException(
                "DSL condition did not evaluate to a boolean: " + conditions);
    }

    /**
     * Evaluates an expression node to a {@link BigDecimal} (ADR-009 mục 4,
     * S3-T2 DAG {@code formula} node). Same parser/dispatch as {@link
     * #test} — only the operator set is wider ({@code + - * /} in addition
     * to the existing comparison/boolean operators) and the result is
     * coerced to {@link BigDecimal} instead of {@code boolean}.
     */
    public static BigDecimal evaluateNumeric(JsonNode expression, Map<String, Object> variables) {
        Object result = evaluate(expression, variables);
        return toBigDecimal(result);
    }

    private static Object evaluate(JsonNode node, Map<String, Object> variables) {
        if (node.isObject()) {
            if (node.size() != 1) {
                throw new RuleDefinitionRejectedException(
                        "DSL condition object must have exactly one operator key: " + node);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            Map.Entry<String, JsonNode> entry = fields.next();
            return applyOperator(entry.getKey(), entry.getValue(), variables);
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isNull()) {
            return null;
        }
        throw new RuleDefinitionRejectedException("Unsupported DSL condition node: " + node);
    }

    private static Object applyOperator(String operator, JsonNode args, Map<String, Object> variables) {
        return switch (operator) {
            case "var" -> resolveVariable(args, variables);
            case "==" -> compareEquals(args, variables);
            case "!=" -> !compareEquals(args, variables);
            case ">" -> compareNumeric(args, variables) > 0;
            case ">=" -> compareNumeric(args, variables) >= 0;
            case "<" -> compareNumeric(args, variables) < 0;
            case "<=" -> compareNumeric(args, variables) <= 0;
            case "and" -> evaluateAll(args, variables);
            case "or" -> evaluateAny(args, variables);
            case "not", "!" -> !asBoolean(evaluate(singleArg(args), variables));
            case "in" -> evaluateIn(args, variables);
            case "+" -> sumArgs(args, variables);
            case "-" -> subtractArgs(args, variables);
            case "*" -> multiplyArgs(args, variables);
            case "/" -> divideArgs(args, variables);
            default -> throw new RuleDefinitionRejectedException("Unsupported DSL operator: " + operator);
        };
    }

    private static JsonNode singleArg(JsonNode args) {
        return args.isArray() ? args.get(0) : args;
    }

    private static List<JsonNode> argsArray(JsonNode args) {
        List<JsonNode> list = new ArrayList<>();
        if (args.isArray()) {
            args.forEach(list::add);
        } else {
            list.add(args);
        }
        return list;
    }

    private static BigDecimal sumArgs(JsonNode args, Map<String, Object> variables) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode arg : argsArray(args)) {
            sum = sum.add(toBigDecimal(evaluate(arg, variables)));
        }
        return sum;
    }

    private static BigDecimal subtractArgs(JsonNode args, Map<String, Object> variables) {
        List<JsonNode> list = argsArray(args);
        if (list.isEmpty()) {
            throw new RuleDefinitionRejectedException("\"-\" requires at least 1 argument");
        }
        BigDecimal result = toBigDecimal(evaluate(list.get(0), variables));
        if (list.size() == 1) {
            return result.negate();
        }
        for (int i = 1; i < list.size(); i++) {
            result = result.subtract(toBigDecimal(evaluate(list.get(i), variables)));
        }
        return result;
    }

    private static BigDecimal multiplyArgs(JsonNode args, Map<String, Object> variables) {
        BigDecimal product = BigDecimal.ONE;
        for (JsonNode arg : argsArray(args)) {
            product = product.multiply(toBigDecimal(evaluate(arg, variables)));
        }
        return product;
    }

    private static BigDecimal divideArgs(JsonNode args, Map<String, Object> variables) {
        List<JsonNode> list = argsArray(args);
        if (list.size() != 2) {
            throw new RuleDefinitionRejectedException("\"/\" requires exactly 2 arguments");
        }
        BigDecimal dividend = toBigDecimal(evaluate(list.get(0), variables));
        BigDecimal divisor = toBigDecimal(evaluate(list.get(1), variables));
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new RuleDefinitionRejectedException("Division by zero in DSL arithmetic expression");
        }
        return dividend.divide(divisor, ARITHMETIC_SCALE, RoundingMode.HALF_UP);
    }

    private static Object resolveVariable(JsonNode args, Map<String, Object> variables) {
        String name = args.isArray() ? args.get(0).asText() : args.asText();
        if (!variables.containsKey(name)) {
            // Absent attribute resolves to null, same as JsonLogic's `var`
            // default-less behaviour — comparisons against null are simply
            // false/unequal rather than a hard error, so a rule referencing
            // an attribute that happens not to be present for this context
            // just doesn't match instead of blowing up the whole pipeline.
            return args.isArray() && args.size() > 1 ? toJavaValue(args.get(1)) : null;
        }
        return variables.get(name);
    }

    private static Object toJavaValue(JsonNode node) {
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return null;
    }

    private static boolean compareEquals(JsonNode args, Map<String, Object> variables) {
        Object left = evaluate(args.get(0), variables);
        Object right = evaluate(args.get(1), variables);
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof BigDecimal || right instanceof BigDecimal) {
            return toBigDecimal(left).compareTo(toBigDecimal(right)) == 0;
        }
        return left.equals(right);
    }

    private static int compareNumeric(JsonNode args, Map<String, Object> variables) {
        Object left = evaluate(args.get(0), variables);
        Object right = evaluate(args.get(1), variables);
        return toBigDecimal(left).compareTo(toBigDecimal(right));
    }

    private static boolean evaluateAll(JsonNode args, Map<String, Object> variables) {
        for (JsonNode condition : args) {
            if (!asBoolean(evaluate(condition, variables))) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateAny(JsonNode args, Map<String, Object> variables) {
        for (JsonNode condition : args) {
            if (asBoolean(evaluate(condition, variables))) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateIn(JsonNode args, Map<String, Object> variables) {
        Object needle = evaluate(args.get(0), variables);
        JsonNode haystack = args.get(1);
        for (JsonNode candidate : haystack) {
            Object candidateValue = evaluate(candidate, variables);
            if (needle == null ? candidateValue == null : needle.equals(candidateValue)) {
                return true;
            }
        }
        return false;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new RuleDefinitionRejectedException("Expected boolean DSL condition, got: " + value);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw new RuleDefinitionRejectedException("Cannot compare non-numeric value: " + text, e);
            }
        }
        throw new RuleDefinitionRejectedException("Cannot compare non-numeric value: " + value);
    }
}
