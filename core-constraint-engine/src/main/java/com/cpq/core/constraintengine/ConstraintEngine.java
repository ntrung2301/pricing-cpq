package com.cpq.core.constraintengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Generic evaluator for the JSON condition grammar shared with rule
 * conditions (ADR-002: a JsonLogic-flavoured grammar — {@code ==, !=, >,
 * >=, <, <=, in, and, or, not}, leaf values via {@code {"var": "<alias>.
 * <attributeCode>"}} or JSON literals). Used by constraint type
 * "compatibility" (E1-02, S2-T2): "entity instance X compatible with
 * entity instance Y if condition Z", but the engine itself has no idea
 * what a "constraint", an "entity instance", a "Part", or a "Vehicle"
 * is — it only evaluates a boolean-valued JSON tree against named
 * {@link AttributeBag}s supplied by the caller. Deterministic and
 * side-effect-free: same condition + same context always produces the
 * same {@link ConstraintEvaluationResult} (CLAUDE.md mục 7).
 */
public final class ConstraintEngine {

    private final ObjectMapper objectMapper;

    public ConstraintEngine() {
        this(new ObjectMapper());
    }

    public ConstraintEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses {@code conditionJson} (the ADR-002 grammar) and evaluates it
     * against {@code context}. Throws {@link ConditionEvaluationException}
     * for malformed JSON, unknown operators/aliases, wrong arity, or
     * incomparable operand types — never silently returns an ambiguous
     * result.
     */
    public ConstraintEvaluationResult evaluate(String conditionJson, EvaluationContext context) {
        JsonNode root;
        try {
            root = objectMapper.readTree(conditionJson);
        } catch (JsonProcessingException e) {
            throw new ConditionEvaluationException("Malformed condition JSON: " + e.getMessage(), e);
        }
        return new ConstraintEvaluationResult(evaluateCondition(root, context));
    }

    private boolean evaluateCondition(JsonNode node, EvaluationContext context) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isObject() && node.size() == 1) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            Map.Entry<String, JsonNode> entry = fields.next();
            String operator = entry.getKey();
            JsonNode args = entry.getValue();
            return switch (operator) {
                case "and" -> evaluateAll(requireArray(args, operator), context, true);
                case "or" -> evaluateAll(requireArray(args, operator), context, false);
                case "not" -> !evaluateCondition(singleArg(args, operator), context);
                case "==" -> evaluateEquality(requireArity(args, 2, operator), context, true);
                case "!=" -> evaluateEquality(requireArity(args, 2, operator), context, false);
                case ">" -> compareOperands(requireArity(args, 2, operator), context) > 0;
                case ">=" -> compareOperands(requireArity(args, 2, operator), context) >= 0;
                case "<" -> compareOperands(requireArity(args, 2, operator), context) < 0;
                case "<=" -> compareOperands(requireArity(args, 2, operator), context) <= 0;
                case "in" -> evaluateIn(requireArity(args, 2, operator), context);
                case "var" -> asBoolean(context.resolveVar(args.asText()));
                default -> throw new ConditionEvaluationException("Unknown operator: " + operator);
            };
        }
        throw new ConditionEvaluationException("Invalid condition node: " + node);
    }

    private boolean evaluateAll(JsonNode arrayNode, EvaluationContext context, boolean requireAll) {
        boolean result = requireAll;
        for (JsonNode child : arrayNode) {
            boolean childResult = evaluateCondition(child, context);
            result = requireAll ? (result && childResult) : (result || childResult);
        }
        return result;
    }

    private JsonNode singleArg(JsonNode args, String operator) {
        if (args.isArray()) {
            if (args.size() != 1) {
                throw new ConditionEvaluationException(
                        "'" + operator + "' expects exactly 1 argument, got " + args.size());
            }
            return args.get(0);
        }
        return args;
    }

    private JsonNode requireArray(JsonNode args, String operator) {
        if (!args.isArray()) {
            throw new ConditionEvaluationException("'" + operator + "' expects a JSON array of conditions");
        }
        return args;
    }

    private java.util.List<JsonNode> requireArity(JsonNode args, int arity, String operator) {
        if (!args.isArray() || args.size() != arity) {
            throw new ConditionEvaluationException(
                    "'" + operator + "' expects exactly " + arity + " arguments");
        }
        java.util.List<JsonNode> result = new java.util.ArrayList<>(arity);
        args.forEach(result::add);
        return result;
    }

    private boolean evaluateEquality(java.util.List<JsonNode> args, EvaluationContext context, boolean wantEqual) {
        Object a = resolveOperand(args.get(0), context);
        Object b = resolveOperand(args.get(1), context);
        boolean equal = valuesEqual(a, b);
        return wantEqual == equal;
    }

    private boolean evaluateIn(java.util.List<JsonNode> args, EvaluationContext context) {
        Object needle = resolveOperand(args.get(0), context);
        JsonNode haystackNode = args.get(1);
        if (!haystackNode.isArray()) {
            throw new ConditionEvaluationException("'in' expects its second argument to be a JSON array literal");
        }
        for (JsonNode element : haystackNode) {
            if (valuesEqual(needle, resolveOperand(element, context))) {
                return true;
            }
        }
        return false;
    }

    private Object resolveOperand(JsonNode node, EvaluationContext context) {
        if (node.isObject() && node.has("var")) {
            return context.resolveVar(node.get("var").asText());
        }
        return literal(node);
    }

    private Object literal(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) {
            return null;
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
        throw new ConditionEvaluationException("Unsupported literal value: " + node);
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new ConditionEvaluationException("Expected a boolean value but got: " + value);
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (isNumeric(a) || isNumeric(b)) {
            return toBigDecimal(a).compareTo(toBigDecimal(b)) == 0;
        }
        if (a instanceof LocalDate || b instanceof LocalDate) {
            return toLocalDate(a).isEqual(toLocalDate(b));
        }
        return Objects.equals(a, b);
    }

    private int compareOperands(java.util.List<JsonNode> args, EvaluationContext context) {
        Object a = resolveOperand(args.get(0), context);
        Object b = resolveOperand(args.get(1), context);
        if (a == null || b == null) {
            throw new ConditionEvaluationException("Cannot order-compare a null value");
        }
        if (isNumeric(a) || isNumeric(b)) {
            return toBigDecimal(a).compareTo(toBigDecimal(b));
        }
        if (a instanceof LocalDate || b instanceof LocalDate) {
            return toLocalDate(a).compareTo(toLocalDate(b));
        }
        if (a instanceof String sa && b instanceof String sb) {
            return sa.compareTo(sb);
        }
        throw new ConditionEvaluationException("Cannot order-compare values: " + a + ", " + b);
    }

    private boolean isNumeric(Object value) {
        return value instanceof BigDecimal || value instanceof Long || value instanceof Integer;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        throw new ConditionEvaluationException("Not a numeric value: " + value);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof String s) {
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                throw new ConditionEvaluationException("Not a valid ISO date: " + s, e);
            }
        }
        throw new ConditionEvaluationException("Not a date value: " + value);
    }
}
