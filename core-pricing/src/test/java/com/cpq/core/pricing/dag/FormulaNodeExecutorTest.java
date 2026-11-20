package com.cpq.core.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the {@code formula} node type (ADR-009 mục 4, S3-T2):
 * computes a numeric value via the extended {@code JsonLogicEvaluator}
 * (core-pricing/dsl), referencing resolved inputs by name.
 */
class FormulaNodeExecutorTest {

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
    void multipliesResolvedInputByConstant() {
        DagNode node =
                new DagNode(
                        "margin",
                        "formula",
                        Map.of("expression", json("{\"*\": [{\"var\": \"value\"}, 1.2]}")),
                        Map.of("value", "avgCost"));
        FormulaNodeExecutor executor = new FormulaNodeExecutor();

        Object result = executor.execute(node, Map.of("value", new BigDecimal("11.1")));

        assertThat(result).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result).isEqualByComparingTo("13.32");
    }

    @Test
    void missingExpressionConfigThrowsClearError() {
        DagNode node = new DagNode("margin", "formula", Map.of(), Map.of());
        FormulaNodeExecutor executor = new FormulaNodeExecutor();

        assertThatThrownBy(() -> executor.execute(node, Map.of()))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("expression");
    }
}
