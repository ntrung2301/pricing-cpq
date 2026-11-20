package com.cpq.core.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the {@code round} node type (ADR-009 mục 2, S3-T2): rounds
 * {@code value} UP (CEILING) to the nearest multiple of config {@code
 * nearest}.
 */
class RoundNodeExecutorTest {

    private final RoundNodeExecutor executor = new RoundNodeExecutor();

    @Test
    void roundsUpToNearestWholeUnit() {
        DagNode node =
                new DagNode("rounded", "round", Map.of("nearest", new BigDecimal("1")), Map.of("value", "margin"));

        Object result = executor.execute(node, Map.of("value", new BigDecimal("13.32")));

        assertThat(result).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result).isEqualByComparingTo("14");
    }

    @Test
    void exactMultipleStaysUnchanged() {
        DagNode node =
                new DagNode("rounded", "round", Map.of("nearest", new BigDecimal("5")), Map.of("value", "margin"));

        Object result = executor.execute(node, Map.of("value", new BigDecimal("20")));

        assertThat((BigDecimal) result).isEqualByComparingTo("20");
    }

    @Test
    void roundsUpToNearestFive() {
        DagNode node =
                new DagNode("rounded", "round", Map.of("nearest", new BigDecimal("5")), Map.of("value", "margin"));

        Object result = executor.execute(node, Map.of("value", new BigDecimal("21")));

        assertThat((BigDecimal) result).isEqualByComparingTo("25");
    }

    @Test
    void nonPositiveNearestThrowsClearError() {
        DagNode node =
                new DagNode("rounded", "round", Map.of("nearest", BigDecimal.ZERO), Map.of("value", "margin"));

        assertThatThrownBy(() -> executor.execute(node, Map.of("value", new BigDecimal("1"))))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("nearest");
    }
}
