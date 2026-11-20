package com.cpq.core.pricing.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.core.pricing.RuleDefinitionRejectedException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the ADR-009 mục 4 extension of {@link JsonLogicEvaluator}: the
 * same JsonLogic-ish parser/dispatch already used for rule conditions
 * (boolean, ADR-002/S2-T10) now also computes numeric values ({@code
 * + - * /}) for DAG {@code formula} nodes (S3-T2) — no new parser, same
 * {@code evaluate}/operator dispatch, just a wider operator set and a
 * {@link BigDecimal} result instead of a {@code boolean} one.
 */
class JsonLogicEvaluatorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    private JsonNode json(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void multipliesVariableByLiteralConstant() {
        JsonNode expression = json("{\"*\": [{\"var\": \"value\"}, 1.2]}");
        Map<String, Object> variables = Map.of("value", new BigDecimal("100"));

        BigDecimal result = JsonLogicEvaluator.evaluateNumeric(expression, variables);

        assertThat(result).isEqualByComparingTo("120.0");
    }

    @Test
    void addsVariadicArguments() {
        BigDecimal result = JsonLogicEvaluator.evaluateNumeric(json("{\"+\": [1, 2, 3]}"), Map.of());

        assertThat(result).isEqualByComparingTo("6");
    }

    @Test
    void subtractsVariadicArgumentsLeftToRight() {
        BigDecimal result = JsonLogicEvaluator.evaluateNumeric(json("{\"-\": [10, 3, 2]}"), Map.of());

        assertThat(result).isEqualByComparingTo("5");
    }

    @Test
    void unarySubtractNegates() {
        BigDecimal result = JsonLogicEvaluator.evaluateNumeric(json("{\"-\": [5]}"), Map.of());

        assertThat(result).isEqualByComparingTo("-5");
    }

    @Test
    void dividesWithFixedInternalScale() {
        BigDecimal result = JsonLogicEvaluator.evaluateNumeric(json("{\"/\": [10, 3]}"), Map.of());

        assertThat(result).isEqualByComparingTo(new BigDecimal("3.3333333333"));
    }

    @Test
    void divisionByZeroThrowsClearError() {
        assertThatThrownBy(
                        () -> JsonLogicEvaluator.evaluateNumeric(json("{\"/\": [10, 0]}"), Map.of()))
                .isInstanceOf(RuleDefinitionRejectedException.class)
                .hasMessageContaining("Division by zero");
    }

    @Test
    void nestedArithmeticAndVariablesResolveCorrectly() {
        JsonNode nested =
                json("{\"+\": [{\"*\": [{\"var\": \"a\"}, {\"var\": \"b\"}]}, {\"var\": \"c\"}]}");
        Map<String, Object> variables =
                Map.of(
                        "a", new BigDecimal("2"),
                        "b", new BigDecimal("3"),
                        "c", new BigDecimal("4"));

        BigDecimal result = JsonLogicEvaluator.evaluateNumeric(nested, variables);

        assertThat(result).isEqualByComparingTo("10");
    }

    @Test
    void existingConditionOperatorsStillWork() {
        JsonNode condition = json("{\">\": [{\"var\": \"quantity\"}, 5]}");

        boolean result = JsonLogicEvaluator.test(condition, Map.of("quantity", new BigDecimal("10")));

        assertThat(result).isTrue();
    }
}
