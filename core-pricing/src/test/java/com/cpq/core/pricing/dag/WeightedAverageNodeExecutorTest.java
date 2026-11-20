package com.cpq.core.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the {@code weighted-average} node type (ADR-009 mục 2,
 * S3-T2): takes a single input that is a list of "rows" (each a
 * {@code Map<String,Object>}), and a {@code valueField}/{@code
 * weightField} config pair naming which fields in each row to use.
 */
class WeightedAverageNodeExecutorTest {

    private final WeightedAverageNodeExecutor executor = new WeightedAverageNodeExecutor();

    @Test
    void computesWeightedAverageAcrossRows() {
        DagNode node =
                new DagNode(
                        "avgCost",
                        "weighted-average",
                        Map.of("valueField", "cost", "weightField", "qty"),
                        Map.of("rows", "costHistory"));
        List<Map<String, Object>> rows =
                List.of(
                        Map.of("cost", new BigDecimal("10.00"), "qty", new BigDecimal("2")),
                        Map.of("cost", new BigDecimal("12.00"), "qty", new BigDecimal("3")),
                        Map.of("cost", new BigDecimal("11.00"), "qty", new BigDecimal("5")));

        Object result = executor.execute(node, Map.of("rows", rows));

        // (10*2 + 12*3 + 11*5) / (2+3+5) = 111/10 = 11.1
        assertThat(result).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result).isEqualByComparingTo("11.1");
    }

    @Test
    void zeroTotalWeightThrowsClearError() {
        DagNode node =
                new DagNode(
                        "avgCost",
                        "weighted-average",
                        Map.of("valueField", "cost", "weightField", "qty"),
                        Map.of("rows", "costHistory"));
        List<Map<String, Object>> rows =
                List.of(Map.of("cost", new BigDecimal("10.00"), "qty", BigDecimal.ZERO));

        assertThatThrownBy(() -> executor.execute(node, Map.of("rows", rows)))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("zero total weight");
    }

    @Test
    void requiresExactlyOneInput() {
        DagNode node =
                new DagNode(
                        "avgCost",
                        "weighted-average",
                        Map.of("valueField", "cost", "weightField", "qty"),
                        Map.of("rows", "costHistory"));

        assertThatThrownBy(() -> executor.execute(node, Map.of()))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("exactly one input");
    }
}
