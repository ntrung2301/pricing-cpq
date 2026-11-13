package com.cpq.core.pricing.dsl;

import com.cpq.core.pricing.RuleDefinitionRejectedException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

/**
 * Minimal evaluator for the JsonLogic-flavoured condition grammar ADR-002
 * specifies for {@code rule_type: DSL}: {@code ==, !=, >, >=, <, <=, in,
 * and, or, not}, plus {@code {"var": "name"}} variable lookup. Three
 * reserved variable names resolve to {@link com.cpq.core.pricing.PricingContext}
 * fields directly ({@code quantity}, {@code basePrice}, {@code
 * currentAmount}); any other name is looked up in {@code attributes}.
 *
 * <p>Deliberately NOT a general JsonLogic implementation (no {@code map},
 * {@code reduce}, string concatenation, arithmetic operators, etc.) — only
 * what a pricing rule condition needs, per ADR-002's choice of "vay mượn
 * ngữ pháp operator của JsonLogic cho phần condition" rather than adopting
 * the full spec/library.
 */
final class JsonLogicEvaluator {

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
            default -> throw new RuleDefinitionRejectedException("Unsupported DSL operator: " + operator);
        };
    }

    private static JsonNode singleArg(JsonNode args) {
        return args.isArray() ? args.get(0) : args;
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
