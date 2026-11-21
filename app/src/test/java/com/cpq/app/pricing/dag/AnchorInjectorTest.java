package com.cpq.app.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.core.pricing.dag.DagNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit test (no Spring context needed — pure static utility) for {@link
 * AnchorInjector}: the "bake anchorInstanceId into nodes missing one"
 * algorithm, extracted out of {@code MeasureRefNodeExecutor} (S3-T5,
 * D-042/D-045) and generalized here (S3-T6, D-046) so {@code
 * PriceListServiceImpl} can reuse it for a Price List's top-level {@code
 * calculation_graph} — which, unlike a Measure's subgraph, may contain
 * BOTH {@code query} and {@code measure-ref} nodes needing anchor
 * injection, not just {@code query}.
 */
class AnchorInjectorTest {

    @Test
    void injectsAnchorIntoAQueryNodeMissingOne() {
        DagNode node = new DagNode("q1", "query", Map.of("bindingAttributeCode", "unitCost"), Map.of());

        List<DagNode> result =
                AnchorInjector.injectAnchorIntoNodesMissingOne(List.of(node), 42L, Set.of("query"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).config().get(AnchorInjector.CONFIG_ANCHOR_INSTANCE_ID)).isEqualTo(42L);
        // original node must be untouched (immutability).
        assertThat(node.config()).doesNotContainKey(AnchorInjector.CONFIG_ANCHOR_INSTANCE_ID);
    }

    @Test
    void injectsAnchorIntoAMeasureRefNodeMissingOne() {
        DagNode node = new DagNode("m1", "measure-ref", Map.of("measureCode", "weightedCost"), Map.of());

        List<DagNode> result =
                AnchorInjector.injectAnchorIntoNodesMissingOne(
                        List.of(node), 7L, Set.of("query", "measure-ref"));

        assertThat(result.get(0).config().get(AnchorInjector.CONFIG_ANCHOR_INSTANCE_ID)).isEqualTo(7L);
    }

    @Test
    void doesNotOverrideAnAlreadyBakedAnchor() {
        DagNode node =
                new DagNode(
                        "q1",
                        "query",
                        Map.of("bindingAttributeCode", "unitCost", "anchorInstanceId", 999L),
                        Map.of());

        List<DagNode> result =
                AnchorInjector.injectAnchorIntoNodesMissingOne(List.of(node), 1L, Set.of("query"));

        assertThat(result.get(0).config().get(AnchorInjector.CONFIG_ANCHOR_INSTANCE_ID)).isEqualTo(999L);
    }

    @Test
    void leavesNodeTypesNotInTheGivenSetUntouched() {
        DagNode formulaNode = new DagNode("f1", "formula", Map.of("expression", "irrelevant"), Map.of());

        List<DagNode> result =
                AnchorInjector.injectAnchorIntoNodesMissingOne(List.of(formulaNode), 1L, Set.of("query"));

        assertThat(result.get(0).config()).doesNotContainKey(AnchorInjector.CONFIG_ANCHOR_INSTANCE_ID);
        assertThat(result.get(0)).isEqualTo(formulaNode);
    }

    @Test
    void preservesNodeIdTypeAndInputsWhenInjecting() {
        DagNode node = new DagNode("q1", "query", Map.of("bindingAttributeCode", "unitCost"), Map.of("x", "other"));

        List<DagNode> result =
                AnchorInjector.injectAnchorIntoNodesMissingOne(List.of(node), 1L, Set.of("query"));

        DagNode patched = result.get(0);
        assertThat(patched.id()).isEqualTo("q1");
        assertThat(patched.type()).isEqualTo("query");
        assertThat(patched.inputs()).isEqualTo(Map.of("x", "other"));
        assertThat(patched.config()).containsEntry("bindingAttributeCode", "unitCost");
    }
}
