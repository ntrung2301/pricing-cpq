package com.cpq.app.pricing.dag;

import com.cpq.app.AbstractIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.app.pricing.CalculationBindingRepository;
import com.cpq.app.pricing.MeasureDefinitionRepository;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.pricing.dag.CalculationBindingSourceType;
import com.cpq.core.pricing.dag.DagExecutionResult;
import com.cpq.core.pricing.dag.DagNode;
import com.cpq.core.pricing.dag.DagValidationException;
import com.cpq.core.pricing.dag.TraceEntry;
import com.cpq.core.pricing.persistence.CalculationBindingEntity;
import com.cpq.core.pricing.persistence.MeasureDefinitionEntity;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S3-T5 Done-khi (sprint-3.md, backlog E8-04): one {@code measure_definition}
 * is referenced via {@code measure-ref} by two different top-level graphs,
 * each with a different anchor instance — each must produce its own
 * correct value (no cross-contamination/cache bleed between calls), and
 * each top-level graph's {@link DagExecutionResult#trace()} must show the
 * measure's internal steps in full (not a black box).
 *
 * <p>Deliberately uses made-up, non-automotive entities ("Gadget" and
 * friends), same convention as {@code QueryNodeExecutorIT} (S3-T3): the
 * point is to prove the Measure machinery has no idea what domain it is
 * being used for.
 *
 * <p>The measure's subgraph uses {@code query -> formula -> round}, not a
 * literal {@code weighted-average} node — {@code WeightedAverageNodeExecutor}
 * (S3-T2) takes exactly one input, a {@code List<Map<String,Object>>} of
 * rows, but the real {@code query} node (S3-T3) always resolves down to a
 * single scalar (filter -> sort -> take-first), never a raw list of rows;
 * there is no "query-list" node type yet (ADR-009 mục 2: node tyes more
 * complex than this finite set are added "khi Promotion/Agreement hoặc
 * Quote-time calculation cần thật", not speculatively). Using {@code
 * query -> formula -> round} keeps every node in this test backed by a
 * real DB read through the real {@code QueryNodeExecutor}, which is what
 * actually exercises the S3-T5 feature under test (anchor injection +
 * trace transclusion) — see docs/decisions.md D-045.
 */
@Testcontainers
@SpringBootTest
class MeasureRefNodeExecutorIT extends AbstractIntegrationTest {

    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceService entityInstanceService;
    @Autowired private CalculationBindingRepository calculationBindingRepository;
    @Autowired private MeasureDefinitionRepository measureDefinitionRepository;
    @Autowired private MeasureAwareDagEngine measureAwareDagEngine;
    @Autowired private MeasureRefNodeExecutor measureRefNodeExecutor;

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    private JsonNode json(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    /** A "Gadget" instance with unitCost/marginPct PRODUCT-style attributes, for use as a measure's anchor. */
    private record GadgetFixture(EntityInstance instance, AttributeDefinition unitCost, AttributeDefinition marginPct) {}

    private GadgetFixture createGadget(String suffix, BigDecimal unitCostValue, BigDecimal marginPctValue) {
        AttributeDefinition unitCostAttr =
                new AttributeDefinition("unitCost", "Unit Cost", AttributeDataType.DECIMAL, false, null);
        AttributeDefinition marginPctAttr =
                new AttributeDefinition("marginPct", "Margin Pct", AttributeDataType.DECIMAL, false, null);
        EntityDefinition gadgetDef = new EntityDefinition("Gadget_" + suffix, "Gadget", 1);
        gadgetDef.addAttribute(unitCostAttr);
        gadgetDef.addAttribute(marginPctAttr);
        EntityDefinition saved = entityDefinitionRepository.save(gadgetDef);
        AttributeDefinition savedUnitCost = findByCode(saved, "unitCost");
        AttributeDefinition savedMarginPct = findByCode(saved, "marginPct");

        EntityInstance gadget = entityInstanceService.createInstance(saved, "test-pack", 1);
        entityInstanceService.writeAttributeValue(gadget, savedUnitCost, AttributeValue.ofNumber(unitCostValue));
        entityInstanceService.writeAttributeValue(gadget, savedMarginPct, AttributeValue.ofNumber(marginPctValue));
        return new GadgetFixture(gadget, savedUnitCost, savedMarginPct);
    }

    /** Builds + saves a "weighted cost" measure (query -> formula -> round), bindings scoped to this one suffix. */
    private MeasureDefinitionEntity createWeightedCostMeasure(String suffix) {
        CalculationBindingEntity costBinding =
                new CalculationBindingEntity(
                        "gadgetCost_" + suffix, CalculationBindingSourceType.PRODUCT, null, "unitCost", false);
        CalculationBindingEntity marginBinding =
                new CalculationBindingEntity(
                        "gadgetMargin_" + suffix, CalculationBindingSourceType.PRODUCT, null, "marginPct", false);
        calculationBindingRepository.save(costBinding);
        calculationBindingRepository.save(marginBinding);

        List<DagNode> graph =
                List.of(
                        new DagNode("q_cost", "query", Map.of("bindingAttributeCode", "gadgetCost_" + suffix), Map.of()),
                        new DagNode(
                                "q_margin", "query", Map.of("bindingAttributeCode", "gadgetMargin_" + suffix), Map.of()),
                        new DagNode(
                                "weighted",
                                "formula",
                                Map.of("expression", json("{\"*\": [{\"var\": \"cost\"}, {\"+\": [1, {\"var\": \"margin\"}]}]}")),
                                Map.of("cost", "q_cost", "margin", "q_margin")),
                        new DagNode("rounded", "round", Map.of("nearest", new BigDecimal("0.01")), Map.of("value", "weighted")));

        MeasureDefinitionEntity measure =
                new MeasureDefinitionEntity("weightedCost_" + suffix, "Weighted Cost", graph, "rounded");
        return measureDefinitionRepository.save(measure);
    }

    private BigDecimal expectedWeightedCost(BigDecimal unitCost, BigDecimal marginPct) {
        BigDecimal weighted = unitCost.multiply(BigDecimal.ONE.add(marginPct));
        BigDecimal nearest = new BigDecimal("0.01");
        return weighted.divide(nearest, 0, RoundingMode.CEILING).multiply(nearest);
    }

    @Test
    void twoTopLevelGraphsReferencingTheSameMeasureWithDifferentAnchorsProduceDifferentCorrectResultsAndFullTrace() {
        String suffix = UUID.randomUUID().toString();
        GadgetFixture gadgetA = createGadget(suffix + "A", new BigDecimal("100.00"), new BigDecimal("0.10"));
        GadgetFixture gadgetB = createGadget(suffix + "B", new BigDecimal("250.00"), new BigDecimal("0.20"));
        createWeightedCostMeasure(suffix);

        List<DagNode> graphA =
                List.of(
                        new DagNode(
                                "mrefA",
                                "measure-ref",
                                Map.of("measureCode", "weightedCost_" + suffix, "anchorInstanceId", gadgetA.instance().getId()),
                                Map.of()),
                        new DagNode("outA", "output", Map.of(), Map.of("finalCost", "mrefA")));
        List<DagNode> graphB =
                List.of(
                        new DagNode(
                                "mrefB",
                                "measure-ref",
                                Map.of("measureCode", "weightedCost_" + suffix, "anchorInstanceId", gadgetB.instance().getId()),
                                Map.of()),
                        new DagNode("outB", "output", Map.of(), Map.of("finalCost", "mrefB")));

        DagExecutionResult resultA = measureAwareDagEngine.execute(graphA);
        DagExecutionResult resultB = measureAwareDagEngine.execute(graphB);

        BigDecimal expectedA = expectedWeightedCost(new BigDecimal("100.00"), new BigDecimal("0.10"));
        BigDecimal expectedB = expectedWeightedCost(new BigDecimal("250.00"), new BigDecimal("0.20"));

        assertThat((BigDecimal) resultA.results().get("mrefA")).isEqualByComparingTo(expectedA);
        assertThat((BigDecimal) resultB.results().get("mrefB")).isEqualByComparingTo(expectedB);
        @SuppressWarnings("unchecked")
        Map<String, Object> outA = (Map<String, Object>) resultA.results().get("outA");
        @SuppressWarnings("unchecked")
        Map<String, Object> outB = (Map<String, Object>) resultB.results().get("outB");
        assertThat((BigDecimal) outA.get("finalCost")).isEqualByComparingTo(expectedA);
        assertThat((BigDecimal) outB.get("finalCost")).isEqualByComparingTo(expectedB);

        // Trace must transclude every inner node of the measure's subgraph, not just the
        // measure-ref node's own entry (ADR-009 mục 2 "không bao giờ là hộp đen").
        List<String> traceNodeIdsA = resultA.trace().stream().map(TraceEntry::nodeId).toList();
        assertThat(traceNodeIdsA)
                .contains(
                        "mrefA",
                        "outA",
                        "mrefA:weightedCost_" + suffix + ":q_cost",
                        "mrefA:weightedCost_" + suffix + ":q_margin",
                        "mrefA:weightedCost_" + suffix + ":weighted",
                        "mrefA:weightedCost_" + suffix + ":rounded");

        TraceEntry transcludedCostA =
                resultA.trace().stream()
                        .filter(entry -> entry.nodeId().equals("mrefA:weightedCost_" + suffix + ":q_cost"))
                        .findFirst()
                        .orElseThrow();
        assertThat(transcludedCostA.type()).isEqualTo("query");
        assertThat((BigDecimal) transcludedCostA.output()).isEqualByComparingTo("100.00");

        // graph B's trace must show gadgetB's own values, not graph A's (no cross-contamination).
        TraceEntry transcludedCostB =
                resultB.trace().stream()
                        .filter(entry -> entry.nodeId().equals("mrefB:weightedCost_" + suffix + ":q_cost"))
                        .findFirst()
                        .orElseThrow();
        assertThat((BigDecimal) transcludedCostB.output()).isEqualByComparingTo("250.00");
    }

    @Test
    void runningTheSameGraphTwiceProducesTheSameResultAndTrace() {
        String suffix = UUID.randomUUID().toString();
        GadgetFixture gadget = createGadget(suffix, new BigDecimal("42.00"), new BigDecimal("0.05"));
        createWeightedCostMeasure(suffix);

        List<DagNode> graph =
                List.of(
                        new DagNode(
                                "mref",
                                "measure-ref",
                                Map.of("measureCode", "weightedCost_" + suffix, "anchorInstanceId", gadget.instance().getId()),
                                Map.of()));

        DagExecutionResult first = measureAwareDagEngine.execute(graph);
        DagExecutionResult second = measureAwareDagEngine.execute(graph);

        assertThat(first.results()).isEqualTo(second.results());
        assertThat(first.trace()).isEqualTo(second.trace());
    }

    @Test
    void interleavedCallsToDifferentAnchorsNeverBleedIntoEachOther() {
        String suffix = UUID.randomUUID().toString();
        GadgetFixture gadgetA = createGadget(suffix + "A", new BigDecimal("10.00"), new BigDecimal("0.00"));
        GadgetFixture gadgetB = createGadget(suffix + "B", new BigDecimal("20.00"), new BigDecimal("0.00"));
        createWeightedCostMeasure(suffix);

        List<DagNode> graphA =
                List.of(
                        new DagNode(
                                "mref",
                                "measure-ref",
                                Map.of("measureCode", "weightedCost_" + suffix, "anchorInstanceId", gadgetA.instance().getId()),
                                Map.of()));
        List<DagNode> graphB =
                List.of(
                        new DagNode(
                                "mref",
                                "measure-ref",
                                Map.of("measureCode", "weightedCost_" + suffix, "anchorInstanceId", gadgetB.instance().getId()),
                                Map.of()));

        DagExecutionResult r1 = measureAwareDagEngine.execute(graphA);
        DagExecutionResult r2 = measureAwareDagEngine.execute(graphB);
        DagExecutionResult r3 = measureAwareDagEngine.execute(graphA);

        assertThat((BigDecimal) r1.results().get("mref")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) r2.results().get("mref")).isEqualByComparingTo("20.00");
        assertThat((BigDecimal) r3.results().get("mref")).isEqualByComparingTo("10.00");
    }

    @Test
    void queryNodeWithAnchorAlreadyBakedInsideTheMeasureIsNotOverridden() {
        String suffix = UUID.randomUUID().toString();
        GadgetFixture bakedAnchor = createGadget(suffix + "-baked", new BigDecimal("999.00"), new BigDecimal("0.00"));
        GadgetFixture callerAnchor = createGadget(suffix + "-caller", new BigDecimal("1.00"), new BigDecimal("0.00"));

        CalculationBindingEntity costBinding =
                new CalculationBindingEntity(
                        "gadgetCostBaked_" + suffix, CalculationBindingSourceType.PRODUCT, null, "unitCost", false);
        calculationBindingRepository.save(costBinding);

        List<DagNode> graph =
                List.of(
                        new DagNode(
                                "q_cost",
                                "query",
                                Map.of(
                                        "bindingAttributeCode",
                                        "gadgetCostBaked_" + suffix,
                                        "anchorInstanceId",
                                        bakedAnchor.instance().getId()),
                                Map.of()));
        MeasureDefinitionEntity measure =
                new MeasureDefinitionEntity("bakedAnchorMeasure_" + suffix, "Baked Anchor Measure", graph, "q_cost");
        measureDefinitionRepository.save(measure);

        List<DagNode> topLevel =
                List.of(
                        new DagNode(
                                "mref",
                                "measure-ref",
                                Map.of(
                                        "measureCode",
                                        "bakedAnchorMeasure_" + suffix,
                                        "anchorInstanceId",
                                        callerAnchor.instance().getId()),
                                Map.of()));

        DagExecutionResult result = measureAwareDagEngine.execute(topLevel);

        assertThat((BigDecimal) result.results().get("mref"))
                .as("a query node that already has its own anchorInstanceId baked in must not be overridden by the measure-ref caller's anchor")
                .isEqualByComparingTo("999.00");
    }

    @Test
    void nestedMeasureRefInsideAMeasuresOwnGraphThrowsAClearNotSupportedError() {
        String suffix = UUID.randomUUID().toString();
        GadgetFixture gadget = createGadget(suffix, new BigDecimal("1.00"), new BigDecimal("0.00"));
        createWeightedCostMeasure(suffix);

        List<DagNode> nestingGraph =
                List.of(
                        new DagNode(
                                "innerMref",
                                "measure-ref",
                                Map.of("measureCode", "weightedCost_" + suffix),
                                Map.of()));
        MeasureDefinitionEntity nestingMeasure =
                new MeasureDefinitionEntity("nestingMeasure_" + suffix, "Nesting Measure", nestingGraph, "innerMref");
        measureDefinitionRepository.save(nestingMeasure);

        List<DagNode> topLevel =
                List.of(
                        new DagNode(
                                "mref",
                                "measure-ref",
                                Map.of(
                                        "measureCode",
                                        "nestingMeasure_" + suffix,
                                        "anchorInstanceId",
                                        gadget.instance().getId()),
                                Map.of()));

        assertThatThrownBy(() -> measureAwareDagEngine.execute(topLevel))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("not supported yet");
    }

    @Test
    void unknownMeasureCodeThrowsAClearError() {
        List<DagNode> topLevel =
                List.of(new DagNode("mref", "measure-ref", Map.of("measureCode", "doesNotExist", "anchorInstanceId", 1L), Map.of()));

        assertThatThrownBy(() -> measureAwareDagEngine.execute(topLevel))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("doesNotExist");
    }

    @Test
    void missingAnchorInstanceIdOnTheCallingMeasureRefNodeThrowsAClearError() {
        String suffix = UUID.randomUUID().toString();
        createWeightedCostMeasure(suffix);
        List<DagNode> topLevel =
                List.of(new DagNode("mref", "measure-ref", Map.of("measureCode", "weightedCost_" + suffix), Map.of()));

        assertThatThrownBy(() -> measureAwareDagEngine.execute(topLevel))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("anchorInstanceId");
    }

    @Test
    void measureDefinitionWithAResultNodeIdNotPresentInItsOwnGraphThrowsAClearError() {
        String suffix = UUID.randomUUID().toString();
        GadgetFixture gadget = createGadget(suffix, new BigDecimal("1.00"), new BigDecimal("0.00"));
        List<DagNode> brokenGraph = List.of(new DagNode("onlyNode", "formula", Map.of("expression", json("5")), Map.of()));
        measureDefinitionRepository.save(
                new MeasureDefinitionEntity("brokenMeasure_" + suffix, "Broken Measure", brokenGraph, "doesNotExist"));

        List<DagNode> topLevel =
                List.of(
                        new DagNode(
                                "mref",
                                "measure-ref",
                                Map.of(
                                        "measureCode",
                                        "brokenMeasure_" + suffix,
                                        "anchorInstanceId",
                                        gadget.instance().getId()),
                                Map.of()));

        assertThatThrownBy(() -> measureAwareDagEngine.execute(topLevel))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("doesNotExist");
    }
}
