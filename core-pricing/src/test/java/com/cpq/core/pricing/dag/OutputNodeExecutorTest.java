package com.cpq.core.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the {@code output} node type (ADR-009 mục 2, S3-T2):
 * passes through all of its named inputs as a {@code Map<String, Object>}
 * — supports multiple named outputs from one graph (e.g. {@code price} +
 * {@code marginRiskLevel}), not just a single fixed {@code finalPrice}.
 */
class OutputNodeExecutorTest {

    private final OutputNodeExecutor executor = new OutputNodeExecutor();

    @Test
    void singleInputPassesThroughUnchanged() {
        DagNode node = new DagNode("out", "output", Map.of(), Map.of("finalPrice", "rounded"));

        Object result = executor.execute(node, Map.of("finalPrice", new BigDecimal("14")));

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap).hasSize(1);
        assertThat((BigDecimal) resultMap.get("finalPrice")).isEqualByComparingTo("14");
    }

    @Test
    void multipleNamedInputsAllAppearInOutputMap() {
        DagNode node =
                new DagNode(
                        "out",
                        "output",
                        Map.of(),
                        Map.of("price", "rounded", "riskLevel", "marginCheck"));

        Object result =
                executor.execute(node, Map.of("price", new BigDecimal("14"), "riskLevel", "YELLOW"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap).hasSize(2);
        assertThat((BigDecimal) resultMap.get("price")).isEqualByComparingTo("14");
        assertThat(resultMap.get("riskLevel")).isEqualTo("YELLOW");
    }
}
