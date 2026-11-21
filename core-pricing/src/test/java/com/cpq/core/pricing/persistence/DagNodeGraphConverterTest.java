package com.cpq.core.pricing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.core.pricing.dag.DagNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link DagNodeGraphConverter} (S3-T5, backs {@code
 * measure_definition.graph}): a {@code List<DagNode>} must round-trip
 * through the JSON text stored in the {@code jsonb} column back into an
 * equal {@code List<DagNode>} — including node types whose {@code config}
 * holds a non-trivial object, not just plain scalars.
 *
 * <p>{@code formula} nodes are the one case needing special care:
 * {@code FormulaNodeExecutor} requires {@code config.get("expression")} to
 * be a {@code com.fasterxml.jackson.databind.JsonNode} instance (see
 * {@code FormulaNodeExecutorTest}) — Jackson's generic {@code
 * Map<String,Object>} deserialization would otherwise hand back a plain
 * {@code LinkedHashMap}/scalar, silently breaking any {@code formula} node
 * loaded from a persisted graph. This converter normalizes exactly that
 * one config entry back into a real {@code JsonNode} on read.
 */
class DagNodeGraphConverterTest {

    private final DagNodeGraphConverter converter = new DagNodeGraphConverter();

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
    void nullGraphRoundTripsToNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTripsASimpleQueryNodeGraphUnchanged() {
        List<DagNode> graph =
                List.of(
                        new DagNode("q1", "query", Map.of("bindingAttributeCode", "unitCost"), Map.of()),
                        new DagNode("out", "output", Map.of(), Map.of("cost", "q1")));

        String json = converter.convertToDatabaseColumn(graph);
        assertThat(json).contains("bindingAttributeCode").contains("unitCost");

        List<DagNode> roundTripped = converter.convertToEntityAttribute(json);

        assertThat(roundTripped).isEqualTo(graph);
    }

    @Test
    void roundTripsARoundNodesBigDecimalNearestConfig() {
        List<DagNode> graph =
                List.of(new DagNode("r1", "round", Map.of("nearest", new BigDecimal("0.01")), Map.of("value", "x")));

        String json = converter.convertToDatabaseColumn(graph);
        List<DagNode> roundTripped = converter.convertToEntityAttribute(json);

        assertThat(roundTripped).hasSize(1);
        Object nearest = roundTripped.get(0).config().get("nearest");
        assertThat(nearest).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) nearest).isEqualByComparingTo("0.01");
    }

    @Test
    void roundTripsAFormulaNodesExpressionBackIntoARealJsonNodeNotAPlainMap() {
        JsonNode expression = json("{\"*\": [{\"var\": \"value\"}, 1.2]}");
        List<DagNode> graph =
                List.of(new DagNode("f1", "formula", Map.of("expression", expression), Map.of("value", "q1")));

        String storedJson = converter.convertToDatabaseColumn(graph);
        List<DagNode> roundTripped = converter.convertToEntityAttribute(storedJson);

        Object roundTrippedExpression = roundTripped.get(0).config().get("expression");
        assertThat(roundTrippedExpression)
                .as("formula node's \"expression\" must deserialize back into a JsonNode, not a plain Map, "
                        + "or FormulaNodeExecutor's instanceof check breaks for any formula node loaded from "
                        + "a persisted graph (e.g. inside a Measure)")
                .isInstanceOf(JsonNode.class);
        assertThat((JsonNode) roundTrippedExpression).isEqualTo(expression);
    }
}
