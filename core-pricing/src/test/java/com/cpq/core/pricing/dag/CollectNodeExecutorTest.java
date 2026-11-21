package com.cpq.core.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the {@code collect} node type (S3-T7, ADR-009 mục 2's "mở
 * rộng dần theo nhu cầu thật"): bundles N named inputs into a single
 * ordered {@code List<Object>} — the "zip into a list" counterpart to
 * {@code output} ("zip into a map"). See {@link DagNodeExecutors}'s Javadoc
 * and docs/decisions.md D-047 for why this was added: a real {@code query}
 * node (S3-T3) always collapses its candidates to ONE value (filter -&gt;
 * sort -&gt; take-first), so it can never itself hand {@code
 * weighted-average} the {@code List<Map<String,Object>>} of rows it needs
 * when those rows come from several real Calculation Binding lookups (e.g.
 * N periods of a real {@code CostHistory}) — unlike {@code DagEngineTest}'s
 * S3-T2 POC, which fed {@code weighted-average} a single literal/stand-in
 * list.
 */
class CollectNodeExecutorTest {

    private final CollectNodeExecutor executor = new CollectNodeExecutor();

    @Test
    void collectsNamedInputsIntoAList() {
        DagNode node = new DagNode("rows", "collect", Map.of(), Map.of("a", "rowA", "b", "rowB", "c", "rowC"));
        Map<String, Object> resolvedInputs =
                Map.of(
                        "a", Map.of("cost", new BigDecimal("1")),
                        "b", Map.of("cost", new BigDecimal("2")),
                        "c", Map.of("cost", new BigDecimal("3")));

        Object result = executor.execute(node, resolvedInputs);

        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) result;
        assertThat(rows).hasSize(3);
        assertThat(rows).containsExactlyInAnyOrder(
                Map.of("cost", new BigDecimal("1")),
                Map.of("cost", new BigDecimal("2")),
                Map.of("cost", new BigDecimal("3")));
    }

    @Test
    void noInputsProducesAnEmptyList() {
        DagNode node = new DagNode("rows", "collect", Map.of(), Map.of());

        Object result = executor.execute(node, Map.of());

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).isEmpty();
    }

    @Test
    void isRegisteredInTheStandardRegistry() {
        assertThat(DagNodeExecutors.standard()).containsKey("collect");
        assertThat(DagNodeExecutors.standard().get("collect")).isInstanceOf(CollectNodeExecutor.class);
    }
}
