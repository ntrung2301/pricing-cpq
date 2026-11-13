package com.cpq.core.pricing.dsl;

import com.cpq.core.pricing.PricingContext;
import com.cpq.core.pricing.Rule;
import com.cpq.core.pricing.RuleApplication;
import com.cpq.core.pricing.RuleDefinitionRejectedException;
import com.cpq.core.pricing.RuleExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Executor for {@code rule_type: DSL} rules (ADR-002/D-014): parses
 * {@code rule.definition()} as
 * {@code {"conditions": <JsonLogic-ish>, "action": {"type": ..., ...}}},
 * evaluates {@code conditions} (absent = always true) via
 * {@link JsonLogicEvaluator}, and if it matches, computes a
 * {@link RuleApplication} from {@code action}.
 *
 * <p>Finite generic action set (deliberately small — core only adds a new
 * action type on a real, reviewed need per ADR-002 Hệ quả, never silently
 * for one pack's convenience):
 * <ul>
 *   <li>{@code percent-discount} — {@code value} is a signed percentage of
 *       {@code currentAmount} (e.g. {@code -10} removes 10%).
 *   <li>{@code fixed-amount} — {@code value} is a signed absolute delta.
 *   <li>{@code set-price} — {@code value} is the exact target amount;
 *       delta is computed so {@code currentAmount + delta == value}.
 * </ul>
 */
public final class DslRuleExecutor implements RuleExecutor {

    private static final int INTERNAL_SCALE = 6;

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    @Override
    public Optional<RuleApplication> apply(Rule rule, PricingContext context, BigDecimal currentAmount) {
        JsonNode root = parse(rule);
        Map<String, Object> variables = buildVariables(context, currentAmount);

        if (!JsonLogicEvaluator.test(root.get("conditions"), variables)) {
            return Optional.empty();
        }

        JsonNode action = root.get("action");
        if (action == null || action.isNull()) {
            throw new RuleDefinitionRejectedException(
                    "DSL rule " + rule.id() + " has no \"action\": " + rule.definition());
        }
        return Optional.of(computeAction(rule, action, currentAmount));
    }

    private JsonNode parse(Rule rule) {
        try {
            return objectMapper.readTree(rule.definition());
        } catch (JsonProcessingException e) {
            throw new RuleDefinitionRejectedException(
                    "DSL rule " + rule.id() + " has malformed JSON definition", e);
        }
    }

    private static Map<String, Object> buildVariables(PricingContext context, BigDecimal currentAmount) {
        Map<String, Object> variables = new HashMap<>(context.attributes());
        variables.put("quantity", context.quantity());
        variables.put("basePrice", context.basePrice());
        variables.put("currentAmount", currentAmount);
        return variables;
    }

    private RuleApplication computeAction(Rule rule, JsonNode action, BigDecimal currentAmount) {
        String type = textField(action, "type", rule);
        BigDecimal value = decimalField(action, "value", rule);
        String label = action.hasNonNull("label") ? action.get("label").asText() : defaultLabel(rule, type);

        BigDecimal delta =
                switch (type) {
                    case "percent-discount" ->
                            currentAmount
                                    .multiply(value)
                                    .divide(BigDecimal.valueOf(100), INTERNAL_SCALE, RoundingMode.HALF_UP);
                    case "fixed-amount" -> value;
                    case "set-price" -> value.subtract(currentAmount);
                    default ->
                            throw new RuleDefinitionRejectedException(
                                    "Unknown DSL action type \"" + type + "\" in rule " + rule.id());
                };
        return new RuleApplication(delta, label);
    }

    private static String defaultLabel(Rule rule, String actionType) {
        return actionType + " (rule " + rule.id() + ")";
    }

    private static String textField(JsonNode action, String field, Rule rule) {
        if (!action.hasNonNull(field)) {
            throw new RuleDefinitionRejectedException(
                    "DSL rule " + rule.id() + " action missing required field \"" + field + "\"");
        }
        return action.get(field).asText();
    }

    private static BigDecimal decimalField(JsonNode action, String field, Rule rule) {
        if (!action.hasNonNull(field)) {
            throw new RuleDefinitionRejectedException(
                    "DSL rule " + rule.id() + " action missing required field \"" + field + "\"");
        }
        JsonNode node = action.get(field);
        if (!node.isNumber()) {
            throw new RuleDefinitionRejectedException(
                    "DSL rule " + rule.id() + " action field \"" + field + "\" must be numeric");
        }
        return node.decimalValue();
    }
}
