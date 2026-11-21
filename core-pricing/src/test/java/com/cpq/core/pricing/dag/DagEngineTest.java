package com.cpq.core.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link DagEngine} (ADR-009 mục 2, S3-T2): generic
 * topological-sort execution over a static node graph, no entity/query
 * involvement (that's S3-T3). Rebuilds the exact graph shape verified in
 * the design-session POC: a stand-in "query" node (literal/config data,
 * since the real {@code query} node type is a different task) feeding a
 * {@code weighted-average} → {@code formula} (×1.2) → {@code round}
 * (nearest 1) → {@code output} pipeline.
 *
 * <p>The stand-in node type ({@code literal}) is deliberately NOT part of
 * {@link DagNodeExecutors#standard()} (the finite production registry) —
 * it is registered only for this test, on top of the standard map, to
 * demonstrate that {@link DagEngine} itself is generic over whatever
 * registry it is handed, not hardcoded to 4 node types.
 */
class DagEngineTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    private JsonNode json(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** A query-shaped source node is out of scope for S3-T2; this fakes its output. */
    private static final class LiteralNodeExecutor implements DagNodeExecutor {
        @Override
        public Object execute(DagNode node, Map<String, Object> resolvedInputs) {
            return node.config().get("value");
        }
    }

    private Map<String, DagNodeExecutor> registryWithLiteralStub() {
        Map<String, DagNodeExecutor> executors = new HashMap<>(DagNodeExecutors.standard());
        executors.put("literal", new LiteralNodeExecutor());
        return executors;
    }

    private List<DagNode> costToPriceGraph() {
        List<Map<String, Object>> costRows =
                List.of(
                        Map.of("cost", new BigDecimal("10.00"), "qty", new BigDecimal("2")),
                        Map.of("cost", new BigDecimal("12.00"), "qty", new BigDecimal("3")),
                        Map.of("cost", new BigDecimal("11.00"), "qty", new BigDecimal("5")));

        DagNode costHistory = new DagNode("costHistory", "literal", Map.of("value", costRows), Map.of());
        DagNode avgCost =
                new DagNode(
                        "avgCost",
                        "weighted-average",
                        Map.of("valueField", "cost", "weightField", "qty"),
                        Map.of("rows", "costHistory"));
        DagNode margin =
                new DagNode(
                        "margin",
                        "formula",
                        Map.of("expression", json("{\"*\": [{\"var\": \"value\"}, 1.2]}")),
                        Map.of("value", "avgCost"));
        DagNode rounded =
                new DagNode(
                        "rounded", "round", Map.of("nearest", new BigDecimal("1")), Map.of("value", "margin"));
        DagNode output =
                new DagNode("finalPrice", "output", Map.of(), Map.of("finalPrice", "rounded", "avgCost", "avgCost"));

        // Deliberately out of topological order in the input list — the
        // engine must sort by input reference, not declaration order.
        return List.of(output, margin, costHistory, rounded, avgCost);
    }

    @Test
    void executesFullPipelineInTopologicalOrderWithCorrectResult() {
        DagEngine engine = new DagEngine(registryWithLiteralStub());

        DagExecutionResult result = engine.execute(costToPriceGraph());

        // (10*2+12*3+11*5)/10 = 11.1 ; *1.2 = 13.32 ; ceiling to nearest 1 = 14
        assertThat((BigDecimal) result.results().get("avgCost")).isEqualByComparingTo("11.1");
        assertThat((BigDecimal) result.results().get("margin")).isEqualByComparingTo("13.32");
        assertThat((BigDecimal) result.results().get("rounded")).isEqualByComparingTo("14");

        @SuppressWarnings("unchecked")
        Map<String, Object> finalOutput = (Map<String, Object>) result.results().get("finalPrice");
        assertThat((BigDecimal) finalOutput.get("finalPrice")).isEqualByComparingTo("14");
        assertThat((BigDecimal) finalOutput.get("avgCost")).isEqualByComparingTo("11.1");

        // Trace covers every node, in execution (topological) order.
        assertThat(result.trace()).hasSize(5);
        assertThat(result.trace().stream().map(TraceEntry::nodeId))
                .containsExactly("costHistory", "avgCost", "margin", "rounded", "finalPrice");
        TraceEntry marginTrace = result.trace().get(2);
        assertThat(marginTrace.type()).isEqualTo("formula");
        assertThat((BigDecimal) marginTrace.resolvedInputs().get("value")).isEqualByComparingTo("11.1");
        assertThat((BigDecimal) marginTrace.output()).isEqualByComparingTo("13.32");
    }

    @Test
    void sameGraphAlwaysProducesSameResultAndTrace() {
        DagEngine engine = new DagEngine(registryWithLiteralStub());
        List<DagNode> graph = costToPriceGraph();

        DagExecutionResult first = engine.execute(graph);
        DagExecutionResult second = engine.execute(graph);

        assertThat(second.results()).isEqualTo(first.results());
        assertThat(second.trace()).isEqualTo(first.trace());
    }

    @Test
    void cycleIsDetectedAndRejectedClearly() {
        DagNode a = new DagNode("a", "formula", Map.of("expression", json("{\"var\": \"x\"}")), Map.of("x", "b"));
        DagNode b = new DagNode("b", "formula", Map.of("expression", json("{\"var\": \"x\"}")), Map.of("x", "a"));
        DagEngine engine = new DagEngine(DagNodeExecutors.standard());

        assertThatThrownBy(() -> engine.execute(List.of(a, b)))
                .isInstanceOf(DagCycleException.class)
                .hasMessageContaining("Graph has a cycle, not a valid DAG");
    }

    @Test
    void selfReferencingNodeIsDetectedAsACycle() {
        DagNode a = new DagNode("a", "formula", Map.of("expression", json("{\"var\": \"x\"}")), Map.of("x", "a"));
        DagEngine engine = new DagEngine(DagNodeExecutors.standard());

        assertThatThrownBy(() -> engine.execute(List.of(a)))
                .isInstanceOf(DagCycleException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void referencingAnUnknownNodeIdThrowsClearError() {
        DagNode a =
                new DagNode(
                        "a", "formula", Map.of("expression", json("{\"var\": \"x\"}")), Map.of("x", "doesNotExist"));
        DagEngine engine = new DagEngine(DagNodeExecutors.standard());

        assertThatThrownBy(() -> engine.execute(List.of(a)))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("doesNotExist");
    }

    @Test
    void unknownNodeTypeThrowsClearError() {
        DagNode a = new DagNode("a", "not-a-real-type", Map.of(), Map.of());
        DagEngine engine = new DagEngine(DagNodeExecutors.standard());

        assertThatThrownBy(() -> engine.execute(List.of(a)))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("not-a-real-type");
    }

    /**
     * S3-T7's real pattern (docs/decisions.md D-047): when the N rows a
     * {@code weighted-average} needs come from several independent scalar
     * sources (e.g. N separate real {@code query} nodes, one per period —
     * simulated here with {@code formula} literals, since {@code query}
     * itself is app-layer/S3-T3) rather than one literal list, each row is
     * first assembled by an {@code output} node (zips named scalars into a
     * {@code Map}), then all rows are zipped into a list by the new {@code
     * collect} node, and only then fed to {@code weighted-average}.
     */
    @Test
    void collectNodeZipsOutputNodeRowsIntoAListForWeightedAverage() {
        DagNode costSep = new DagNode("costSep", "formula", Map.of("expression", json("40.00")), Map.of());
        DagNode qtySep = new DagNode("qtySep", "formula", Map.of("expression", json("120")), Map.of());
        DagNode costOct = new DagNode("costOct", "formula", Map.of("expression", json("42.50")), Map.of());
        DagNode qtyOct = new DagNode("qtyOct", "formula", Map.of("expression", json("95")), Map.of());
        DagNode costNov = new DagNode("costNov", "formula", Map.of("expression", json("44.00")), Map.of());
        DagNode qtyNov = new DagNode("qtyNov", "formula", Map.of("expression", json("150")), Map.of());

        DagNode rowSep = new DagNode("rowSep", "output", Map.of(), Map.of("cost", "costSep", "qty", "qtySep"));
        DagNode rowOct = new DagNode("rowOct", "output", Map.of(), Map.of("cost", "costOct", "qty", "qtyOct"));
        DagNode rowNov = new DagNode("rowNov", "output", Map.of(), Map.of("cost", "costNov", "qty", "qtyNov"));

        DagNode rows =
                new DagNode("rows", "collect", Map.of(), Map.of("sep", "rowSep", "oct", "rowOct", "nov", "rowNov"));

        DagNode avgCost =
                new DagNode(
                        "avgCost", "weighted-average", Map.of("valueField", "cost", "weightField", "qty"), Map.of("rows", "rows"));

        DagEngine engine = new DagEngine(DagNodeExecutors.standard());
        DagExecutionResult result =
                engine.execute(
                        List.of(costSep, qtySep, costOct, qtyOct, costNov, qtyNov, rowSep, rowOct, rowNov, rows, avgCost));

        // Same weighted average as PricingTaxonomyDemoSeedRunner's real CostHistory
        // seed data (D-043): (40*120 + 42.5*95 + 44*150) / (120+95+150).
        assertThat((BigDecimal) result.results().get("avgCost")).isEqualByComparingTo("42.2945205479");
    }

    @Test
    void outputNodeWithTwoDifferentInputsReturnsBothInResultMap() {
        DagNode price = new DagNode("price", "formula", Map.of("expression", json("5")), Map.of());
        DagNode risk = new DagNode("risk", "formula", Map.of("expression", json("1")), Map.of());
        DagNode out =
                new DagNode("out", "output", Map.of(), Map.of("price", "price", "riskLevel", "risk"));
        DagEngine engine = new DagEngine(DagNodeExecutors.standard());

        DagExecutionResult result = engine.execute(List.of(price, risk, out));

        @SuppressWarnings("unchecked")
        Map<String, Object> outputMap = (Map<String, Object>) result.results().get("out");
        assertThat(outputMap).hasSize(2);
        assertThat((BigDecimal) outputMap.get("price")).isEqualByComparingTo("5");
        assertThat((BigDecimal) outputMap.get("riskLevel")).isEqualByComparingTo("1");
    }
}
