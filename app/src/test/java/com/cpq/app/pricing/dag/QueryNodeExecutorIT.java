package com.cpq.app.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.app.metamodel.EntityDefinitionDimensionAttributeRepository;
import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.app.pricing.CalculationBindingRepository;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityDefinitionDimensionAttribute;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityRole;
import com.cpq.core.pricing.dag.CalculationBindingFilterOperator;
import com.cpq.core.pricing.dag.CalculationBindingSortDirection;
import com.cpq.core.pricing.dag.CalculationBindingSourceType;
import com.cpq.core.pricing.dag.CalculationBindingValueSource;
import com.cpq.core.pricing.dag.DagNode;
import com.cpq.core.pricing.dag.DagValidationException;
import com.cpq.core.pricing.persistence.CalculationBindingEntity;
import com.cpq.core.pricing.persistence.CalculationBindingFilterEntity;
import com.cpq.core.pricing.persistence.CalculationBindingSortEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S3-T3 Done-khi (sprint-3.md): Calculation Binding + DAG node {@code
 * query} reads PRODUCT/CUSTOMER (direct attribute), EXTENSION/PARAMETER
 * (filter -> sort -> take-first); ambiguous match without {@code
 * take_first_after_sort} throws; 0 matches returns {@code null}; a
 * binding that used to resolve unambiguously becomes ambiguous when
 * source data changes between two calls, and the *second* call throws
 * (runtime check, not a one-time config check).
 *
 * <p>Deliberately uses made-up, non-automotive entities ("Gadget" and
 * friends) — same spirit as {@code ConstraintInstanceEvaluationServiceIT}
 * (S2-T2) and {@code EntityInstanceServiceIT} (S3-T1): the point is to
 * prove the Calculation Binding machinery has no idea what "CostHistory"/
 * "MarginThreshold" are (that's S3-T4's seed data, a different task).
 * Follows the same Testcontainers pattern as those classes.
 */
@Testcontainers
@SpringBootTest
class QueryNodeExecutorIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceRepository entityInstanceRepository;
    @Autowired private EntityInstanceService entityInstanceService;
    @Autowired private EntityDefinitionDimensionAttributeRepository dimensionAttributeRepository;
    @Autowired private CalculationBindingRepository calculationBindingRepository;
    @Autowired private QueryNodeExecutor queryNodeExecutor;

    // Each @Test below shares the same static Testcontainers Postgres (one schema
    // per test class, not per test method), so codes must be unique per call to
    // avoid tripping unique constraints across methods (same convention as
    // ConstraintInstanceEvaluationServiceIT/EntityInstanceServiceIT).
    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private DagNode queryNode(String bindingAttributeCode, Long anchorInstanceId) {
        Map<String, Object> config =
                anchorInstanceId == null
                        ? Map.of("bindingAttributeCode", bindingAttributeCode)
                        : Map.of(
                                "bindingAttributeCode",
                                bindingAttributeCode,
                                "anchorInstanceId",
                                anchorInstanceId);
        return new DagNode("q1", "query", config, Map.of());
    }

    @Test
    void readsProductAttributeDirectlyOffTheAnchorInstanceWithNoFilterOrSort() {
        String suffix = UUID.randomUUID().toString();
        AttributeDefinition unitCost =
                new AttributeDefinition("unitCost", "Unit Cost", AttributeDataType.DECIMAL, false, null);
        EntityDefinition gadgetDef = new EntityDefinition("Gadget_" + suffix, "Gadget", 1);
        gadgetDef.addAttribute(unitCost);
        EntityDefinition savedGadgetDef = entityDefinitionRepository.save(gadgetDef);
        AttributeDefinition savedUnitCost = findByCode(savedGadgetDef, "unitCost");

        EntityInstance gadget = entityInstanceService.createInstance(savedGadgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(gadget, savedUnitCost, AttributeValue.ofNumber(new BigDecimal("42.50")));

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "gadgetUnitCost_" + suffix, CalculationBindingSourceType.PRODUCT, null, "unitCost", false);
        calculationBindingRepository.save(binding);

        Object result =
                queryNodeExecutor.execute(queryNode("gadgetUnitCost_" + suffix, gadget.getId()), Map.of());

        assertThat(result).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result).isEqualByComparingTo("42.50");
    }

    @Test
    void readsCustomerAttributeDirectlyOffTheAnchorInstanceTheSameWayAsProduct() {
        String suffix = UUID.randomUUID().toString();
        AttributeDefinition tier =
                new AttributeDefinition("tier", "Tier", AttributeDataType.STRING, false, null);
        EntityDefinition customerLikeDef = new EntityDefinition("Gizmo_" + suffix, "Gizmo", 1);
        customerLikeDef.addAttribute(tier);
        EntityDefinition saved = entityDefinitionRepository.save(customerLikeDef);
        AttributeDefinition savedTier = findByCode(saved, "tier");

        EntityInstance customerLike = entityInstanceService.createInstance(saved, "test-pack", 1);
        entityInstanceService.writeAttributeValue(customerLike, savedTier, AttributeValue.ofString("GOLD"));

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "gizmoTier_" + suffix, CalculationBindingSourceType.CUSTOMER, null, "tier", false);
        calculationBindingRepository.save(binding);

        Object result =
                queryNodeExecutor.execute(queryNode("gizmoTier_" + suffix, customerLike.getId()), Map.of());

        assertThat(result).isEqualTo("GOLD");
    }

    /** Anchor + Extension fixture shared by the EXTENSION-source tests below. */
    private record GadgetCostFixture(EntityInstance anchor, EntityDefinition costDef, String suffix) {}

    private GadgetCostFixture defineGadgetWithCostExtension() {
        String suffix = UUID.randomUUID().toString();
        AttributeDefinition anchorCode =
                new AttributeDefinition("code", "Code", AttributeDataType.STRING, true, null);
        EntityDefinition gadgetDef = new EntityDefinition("GadgetA_" + suffix, "Gadget A", 1);
        gadgetDef.addAttribute(anchorCode);
        EntityDefinition savedGadgetDef = entityDefinitionRepository.save(gadgetDef);
        AttributeDefinition savedAnchorCode = findByCode(savedGadgetDef, "code");
        savedGadgetDef.designateKeyAttribute(savedAnchorCode);
        savedGadgetDef = entityDefinitionRepository.save(savedGadgetDef);

        AttributeDefinition period =
                new AttributeDefinition("period", "Period", AttributeDataType.STRING, true, null);
        AttributeDefinition amount =
                new AttributeDefinition("amount", "Amount", AttributeDataType.DECIMAL, false, null);
        AttributeDefinition currency =
                new AttributeDefinition("currency", "Currency", AttributeDataType.STRING, false, null);
        EntityDefinition costDef = new EntityDefinition("GadgetCost_" + suffix, "Gadget Cost", 1);
        costDef.addAttribute(period);
        costDef.addAttribute(amount);
        costDef.addAttribute(currency);
        costDef.assignRole(EntityRole.EXTENSION);
        costDef.assignExtendsEntityDefinition(savedGadgetDef);
        EntityDefinition savedCostDef = entityDefinitionRepository.save(costDef);
        AttributeDefinition savedPeriod = findByCode(savedCostDef, "period");
        dimensionAttributeRepository.save(new EntityDefinitionDimensionAttribute(savedCostDef, savedPeriod, 0));

        EntityInstance anchor = entityInstanceService.createInstance(savedGadgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(anchor, savedAnchorCode, AttributeValue.ofString("GADGET-" + suffix));

        return new GadgetCostFixture(anchor, savedCostDef, suffix);
    }

    private EntityInstance addCostRow(
            GadgetCostFixture fixture, EntityInstance anchor, String period, String amount, String currency) {
        AttributeDefinition periodAttr = findByCode(fixture.costDef(), "period");
        AttributeDefinition amountAttr = findByCode(fixture.costDef(), "amount");
        AttributeDefinition currencyAttr = findByCode(fixture.costDef(), "currency");
        EntityInstance row =
                entityInstanceService.createInstance(fixture.costDef(), "test-pack", 1, anchor.getId());
        entityInstanceService.writeAttributeValue(row, periodAttr, AttributeValue.ofString(period));
        entityInstanceService.writeAttributeValue(row, amountAttr, AttributeValue.ofNumber(new BigDecimal(amount)));
        entityInstanceService.writeAttributeValue(row, currencyAttr, AttributeValue.ofString(currency));
        return row;
    }

    @Test
    void extensionBindingWithZeroMatchingRowsReturnsNullWithoutThrowing() {
        GadgetCostFixture fixture = defineGadgetWithCostExtension();

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "gadgetCost_" + fixture.suffix(),
                        CalculationBindingSourceType.EXTENSION,
                        fixture.costDef().getCode(),
                        "amount",
                        false);
        calculationBindingRepository.save(binding);

        Object result =
                queryNodeExecutor.execute(
                        queryNode("gadgetCost_" + fixture.suffix(), fixture.anchor().getId()), Map.of());

        assertThat(result).isNull();
    }

    @Test
    void extensionBindingWithExactlyOneMatchingRowReturnsItsValue() {
        GadgetCostFixture fixture = defineGadgetWithCostExtension();
        addCostRow(fixture, fixture.anchor(), "2026-01", "10", "USD");

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "gadgetCostSingle_" + fixture.suffix(),
                        CalculationBindingSourceType.EXTENSION,
                        fixture.costDef().getCode(),
                        "amount",
                        false);
        calculationBindingRepository.save(binding);

        Object result =
                queryNodeExecutor.execute(
                        queryNode("gadgetCostSingle_" + fixture.suffix(), fixture.anchor().getId()), Map.of());

        assertThat((BigDecimal) result).isEqualByComparingTo("10");
    }

    @Test
    void extensionBindingWithTwoMatchingRowsAndTakeFirstFalseThrowsAmbiguousError() {
        GadgetCostFixture fixture = defineGadgetWithCostExtension();
        addCostRow(fixture, fixture.anchor(), "2026-01", "10", "USD");
        addCostRow(fixture, fixture.anchor(), "2026-02", "20", "USD");

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "gadgetCostAmbiguous_" + fixture.suffix(),
                        CalculationBindingSourceType.EXTENSION,
                        fixture.costDef().getCode(),
                        "amount",
                        false);
        calculationBindingRepository.save(binding);

        assertThatThrownBy(
                        () ->
                                queryNodeExecutor.execute(
                                        queryNode("gadgetCostAmbiguous_" + fixture.suffix(), fixture.anchor().getId()),
                                        Map.of()))
                .isInstanceOf(CalculationBindingAmbiguousException.class)
                .hasMessageContaining("gadgetCostAmbiguous_" + fixture.suffix())
                .hasMessageContaining("2");
    }

    @Test
    void extensionBindingWithTwoMatchingRowsAndTakeFirstTrueUsesSortToPickOne() {
        GadgetCostFixture fixture = defineGadgetWithCostExtension();
        addCostRow(fixture, fixture.anchor(), "2026-01", "10", "USD");
        addCostRow(fixture, fixture.anchor(), "2026-02", "20", "USD");

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "gadgetCostLatest_" + fixture.suffix(),
                        CalculationBindingSourceType.EXTENSION,
                        fixture.costDef().getCode(),
                        "amount",
                        true);
        binding.addSort(new CalculationBindingSortEntity("period", CalculationBindingSortDirection.DESC, 0));
        calculationBindingRepository.save(binding);

        Object result =
                queryNodeExecutor.execute(
                        queryNode("gadgetCostLatest_" + fixture.suffix(), fixture.anchor().getId()), Map.of());

        // DESC by period -> "2026-02" sorts first -> amount 20.
        assertThat((BigDecimal) result).isEqualByComparingTo("20");
    }

    @Test
    void sourceDataChangingBetweenTwoCallsMakesAPreviouslyUnambiguousBindingThrowOnTheSecondCall() {
        GadgetCostFixture fixture = defineGadgetWithCostExtension();
        addCostRow(fixture, fixture.anchor(), "2026-01", "10", "USD");

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "gadgetCostUsd_" + fixture.suffix(),
                        CalculationBindingSourceType.EXTENSION,
                        fixture.costDef().getCode(),
                        "amount",
                        false);
        binding.addFilter(
                new CalculationBindingFilterEntity(
                        "currency", CalculationBindingFilterOperator.EQ, CalculationBindingValueSource.CONSTANT, "USD"));
        calculationBindingRepository.save(binding);

        DagNode node = queryNode("gadgetCostUsd_" + fixture.suffix(), fixture.anchor().getId());

        // First call: exactly one USD row -> resolves cleanly.
        Object first = queryNodeExecutor.execute(node, Map.of());
        assertThat((BigDecimal) first).isEqualByComparingTo("10");

        // Source data changes: a second USD row appears (different period,
        // so it doesn't collide on the composite business key).
        addCostRow(fixture, fixture.anchor(), "2026-02", "20", "USD");

        // Second call, same binding/config, unchanged take_first_after_sort=false:
        // now ambiguous -> must throw immediately, not silently reuse/average
        // the previous answer (ADR-009 mục 3: checked at runtime, not cached).
        assertThatThrownBy(() -> queryNodeExecutor.execute(node, Map.of()))
                .isInstanceOf(CalculationBindingAmbiguousException.class);
    }

    @Test
    void parameterBindingDoesNotAutoMatchAnyAnchorAndFiltersOnAContextAttributeInputValue() {
        String suffix = UUID.randomUUID().toString();
        AttributeDefinition levelCode =
                new AttributeDefinition("levelCode", "Level Code", AttributeDataType.STRING, true, null);
        AttributeDefinition minPct =
                new AttributeDefinition("minPct", "Min %", AttributeDataType.DECIMAL, false, null);
        AttributeDefinition maxPct =
                new AttributeDefinition("maxPct", "Max %", AttributeDataType.DECIMAL, false, null);
        EntityDefinition marginLevelDef = new EntityDefinition("MarginLevel_" + suffix, "Margin Level", 1);
        marginLevelDef.addAttribute(levelCode);
        marginLevelDef.addAttribute(minPct);
        marginLevelDef.addAttribute(maxPct);
        marginLevelDef.assignRole(EntityRole.PARAMETER);
        EntityDefinition saved = entityDefinitionRepository.save(marginLevelDef);
        AttributeDefinition savedLevelCode = findByCode(saved, "levelCode");
        AttributeDefinition savedMinPct = findByCode(saved, "minPct");
        AttributeDefinition savedMaxPct = findByCode(saved, "maxPct");
        saved.designateKeyAttribute(savedLevelCode);
        entityDefinitionRepository.save(saved);

        createMarginLevel(saved, savedLevelCode, savedMinPct, savedMaxPct, "CRITICAL", "0", "15");
        createMarginLevel(saved, savedLevelCode, savedMinPct, savedMaxPct, "RED", "15", "20");
        createMarginLevel(saved, savedLevelCode, savedMinPct, savedMaxPct, "YELLOW", "20", "30");
        createMarginLevel(saved, savedLevelCode, savedMinPct, savedMaxPct, "GREEN", "30", "100");

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "marginLevelForPct_" + suffix,
                        CalculationBindingSourceType.PARAMETER,
                        saved.getCode(),
                        "levelCode",
                        true);
        binding.addFilter(
                new CalculationBindingFilterEntity(
                        "minPct",
                        CalculationBindingFilterOperator.LTE,
                        CalculationBindingValueSource.CONTEXT_ATTRIBUTE,
                        "marginPct"));
        binding.addFilter(
                new CalculationBindingFilterEntity(
                        "maxPct",
                        CalculationBindingFilterOperator.GT,
                        CalculationBindingValueSource.CONTEXT_ATTRIBUTE,
                        "marginPct"));
        calculationBindingRepository.save(binding);

        // No anchorInstanceId in config at all — PARAMETER must not require one.
        DagNode node = queryNode("marginLevelForPct_" + suffix, null);
        Map<String, Object> resolvedInputs = Map.of("marginPct", new BigDecimal("18"));

        Object result = queryNodeExecutor.execute(node, resolvedInputs);

        assertThat(result).isEqualTo("RED");
    }

    private void createMarginLevel(
            EntityDefinition def,
            AttributeDefinition levelCode,
            AttributeDefinition minPct,
            AttributeDefinition maxPct,
            String level,
            String min,
            String max) {
        EntityInstance instance = entityInstanceService.createInstance(def, "test-pack", 1);
        entityInstanceService.writeAttributeValue(instance, levelCode, AttributeValue.ofString(level));
        entityInstanceService.writeAttributeValue(instance, minPct, AttributeValue.ofNumber(new BigDecimal(min)));
        entityInstanceService.writeAttributeValue(instance, maxPct, AttributeValue.ofNumber(new BigDecimal(max)));
    }

    @Test
    void filterUsingRequestParamValueSourceThrowsAClearNotImplementedErrorInsteadOfSilentlySkippingIt() {
        String suffix = UUID.randomUUID().toString();
        EntityDefinition paramDef = new EntityDefinition("Param_" + suffix, "Param", 1);
        AttributeDefinition flag =
                new AttributeDefinition("flag", "Flag", AttributeDataType.STRING, false, null);
        paramDef.addAttribute(flag);
        paramDef.assignRole(EntityRole.PARAMETER);
        EntityDefinition saved = entityDefinitionRepository.save(paramDef);

        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        "paramRequestParam_" + suffix,
                        CalculationBindingSourceType.PARAMETER,
                        saved.getCode(),
                        "flag",
                        false);
        binding.addFilter(
                new CalculationBindingFilterEntity(
                        "flag",
                        CalculationBindingFilterOperator.EQ,
                        CalculationBindingValueSource.REQUEST_PARAM,
                        "someRequestParamName"));
        calculationBindingRepository.save(binding);

        DagNode node = queryNode("paramRequestParam_" + suffix, null);

        assertThatThrownBy(() -> queryNodeExecutor.execute(node, Map.of()))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("REQUEST_PARAM");
    }

    @Test
    void unknownBindingAttributeCodeFailsClearlyRatherThanReturningNull() {
        DagNode node = queryNode("does-not-exist-" + UUID.randomUUID(), 1L);

        assertThatThrownBy(() -> queryNodeExecutor.execute(node, Map.of()))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("unknown calculation binding");
    }

    @Test
    void entityInstanceRepositoryFindsOnlyExtensionRowsMatchingTheGivenAnchor() {
        GadgetCostFixture fixtureA = defineGadgetWithCostExtension();
        addCostRow(fixtureA, fixtureA.anchor(), "2026-01", "10", "USD");

        // A second, unrelated anchor of a *different* entity definition —
        // proves findByEntityDefinition_CodeAndAnchorInstanceOrderByIdAsc
        // filters by anchor, not just by source_entity_code.
        GadgetCostFixture fixtureB = defineGadgetWithCostExtension();
        addCostRow(fixtureB, fixtureB.anchor(), "2026-01", "999", "USD");

        List<EntityInstance> rows =
                entityInstanceRepository.findByEntityDefinition_CodeAndAnchorInstanceOrderByIdAsc(
                        fixtureA.costDef().getCode(), fixtureA.anchor());

        assertThat(rows).hasSize(1);
    }
}
