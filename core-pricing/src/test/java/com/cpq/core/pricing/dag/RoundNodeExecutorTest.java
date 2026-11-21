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

    // S3-T8 (docs/decisions.md D-049): a `round` node built from a REAL
    // `POST /api/price-lists-v2` JSON request body (Measure Canvas UI, not
    // constructed directly in Java like every other test here) arrives
    // with config "nearest" as whatever plain Integer/Double Jackson
    // decodes a bare JSON number to inside a `Map<String, Object>` — never
    // a BigDecimal, since there is no per-field type information to guide
    // it. Before this fix, every such Price List's `addItem` 500'd
    // (DagValidationException) the moment it hit a `round` node — this
    // was never caught earlier because every existing test seeds a
    // DagNode with a real Java `BigDecimal` literal directly, bypassing
    // JSON entirely.
    @Test
    void acceptsAnIntegerNearestConfig_asArrivesFromJsonOverHttp() {
        DagNode node = new DagNode("rounded", "round", Map.of("nearest", 1), Map.of("value", "margin"));

        Object result = executor.execute(node, Map.of("value", new BigDecimal("13.32")));

        assertThat((BigDecimal) result).isEqualByComparingTo("14");
    }

    @Test
    void acceptsADoubleNearestConfig_asArrivesFromJsonOverHttp() {
        DagNode node = new DagNode("rounded", "round", Map.of("nearest", 0.5), Map.of("value", "margin"));

        Object result = executor.execute(node, Map.of("value", new BigDecimal("13.1")));

        assertThat((BigDecimal) result).isEqualByComparingTo("13.5");
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
