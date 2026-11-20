package com.cpq.core.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link DagNode} is pure data (ADR-009 mục 2): a graph of these is a
 * static structure, never mutated during execution. These tests pin that
 * down — a caller handing in a mutable map must not be able to reach back
 * in and change the node after construction.
 */
class DagNodeTest {

    @Test
    void copiesConfigAndInputsDefensively() {
        Map<String, Object> config = new HashMap<>();
        config.put("nearest", "1");
        Map<String, String> inputs = new HashMap<>();
        inputs.put("value", "otherNode");

        DagNode node = new DagNode("n1", "round", config, inputs);
        config.put("nearest", "999");
        inputs.put("value", "mutatedAway");

        assertThat(node.config()).containsEntry("nearest", "1");
        assertThat(node.inputs()).containsEntry("value", "otherNode");
    }

    @Test
    void configAndInputsAreUnmodifiable() {
        DagNode node = new DagNode("n1", "output", Map.of(), Map.of("x", "n0"));

        assertThatThrownBy(() -> node.inputs().put("y", "n2"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> node.config().put("y", "n2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullConfigAndInputsDefaultToEmpty() {
        DagNode node = new DagNode("n1", "output", null, null);

        assertThat(node.config()).isEmpty();
        assertThat(node.inputs()).isEmpty();
    }

    @Test
    void requiresIdAndType() {
        assertThatThrownBy(() -> new DagNode(null, "output", Map.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DagNode("n1", null, Map.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
