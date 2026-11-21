package com.cpq.app.demo;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceAttributeValueRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.pricing.CalculationBindingRepository;
import com.cpq.app.pricing.PriceListV2Repository;
import com.cpq.core.api.pricelist.AddPriceListItemRequest;
import com.cpq.core.api.pricelist.CreatePriceListRequest;
import com.cpq.core.api.pricelist.DagNodeDto;
import com.cpq.core.api.pricelist.PriceListDto;
import com.cpq.core.api.pricelist.PriceListItemDto;
import com.cpq.core.api.pricelist.PriceListService;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import com.cpq.core.pricing.dag.CalculationBindingFilterOperator;
import com.cpq.core.pricing.dag.CalculationBindingSourceType;
import com.cpq.core.pricing.dag.CalculationBindingValueSource;
import com.cpq.core.pricing.persistence.CalculationBindingEntity;
import com.cpq.core.pricing.persistence.CalculationBindingFilterEntity;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Seeds a real, S3-T6-API-driven demo Price List (S3-T7, sprint-3.md,
 * backlog E8-07) whose {@code calculation_graph} reads all 4 sources the
 * task requires:
 *
 * <ol>
 *   <li><b>Product</b> — {@code country_of_manufacture}, read directly
 *       (node {@code q_country}).
 *   <li><b>CostHistory</b> (EXTENSION, S3-T4) — 3 real rows (periods
 *       {@code 2026-09/10/11}), each read by its own {@code query} node
 *       (one {@code calculation_binding} per period+field, since a single
 *       binding's filter -&gt; sort -&gt; take-first always collapses to
 *       ONE row — ADR-009 mục 3 — it cannot itself return "all 3 rows").
 *       Each period's {@code cost}/{@code quantity} pair is zipped into a
 *       row {@code Map} by an {@code output} node, the 3 rows are zipped
 *       into a list by the new {@code collect} node (S3-T7, see
 *       docs/decisions.md D-047), and {@code weighted-average} (S3-T2,
 *       unmodified) computes the weighted-average cost from that real list.
 *   <li><b>MarginThreshold</b> (PARAMETER, S3-T4) — looked up by a {@code
 *       query} node whose binding filters {@code min_margin_pct}/{@code
 *       max_margin_pct} against the margin % computed from this graph's own
 *       {@code avgCost}/{@code markupPrice} nodes (a {@code
 *       CONTEXT_ATTRIBUTE} filter, ADR-009 mục 3) — the resulting risk
 *       level ({@code q_marginTier}) is a pure side output, never an
 *       ancestor of {@code roundedPrice} (no cycle, per this task's
 *       instructions).
 *   <li><b>Customer</b> — 1 {@code query} node ({@code q_customer}) reads
 *       one real {@code Customer} instance's {@code tier} directly, with a
 *       concrete {@code anchorInstanceId} already baked into its config at
 *       graph-authoring time (so {@code AnchorInjector}, which only injects
 *       into nodes *missing* an anchor, leaves it alone — it is never
 *       re-anchored to the Product being priced). It feeds only the
 *       {@code summary} display node, never {@code roundedPrice} — see
 *       docs/decisions.md D-047 for why this is the chosen, PO-flagged-
 *       NEEDS-REVIEW default for "how Customer participates" (sprint-3.md's
 *       second open question).
 * </ol>
 *
 * <p><b>Rounding "theo xuất xứ":</b> {@code RoundNodeExecutor}'s {@code
 * nearest} is a static config literal, not wired from another node's
 * output (S3-T2, not modified here) — there is no live DAG-runtime branch
 * from {@code country_of_manufacture} to the rounding amount. Instead this
 * runner reads the Product's real {@code country_of_manufacture} value
 * itself, once, at graph-authoring time, and picks {@code nearest}
 * accordingly (Vietnam-made parts round to the nearest 0.50, everything
 * else to the nearest 1.00) — a seed-time/business config decision, same
 * spirit as every other DAG node config being data, not a new "conditional
 * config" node type invented for this one demo (see D-047).
 *
 * <p><b>Calls the real S3-T6 {@link PriceListService} port</b> (create -&gt;
 * add item -&gt; submit -&gt; approve), never persistence/SQL directly —
 * same "reuse the real service" instruction this task set. Since {@code
 * @PreAuthorize("hasRole('PRICING_ADMIN')")} guards every method and this
 * runner executes at startup with no HTTP/JWT request, a synthetic {@code
 * ROLE_PRICING_ADMIN} authentication is established for the duration of the
 * call and restored afterward — identical pattern to {@link
 * DemoDataSeedRunner#run}.
 *
 * <p><b>Ordering:</b> calls {@link PricingTaxonomyDemoSeedRunner#run}
 * itself first (same "call the previous runner directly, don't rely on
 * Spring's unspecified bean-invocation order" strategy as every other demo
 * seed runner in this package), guaranteeing {@code Product}/{@code
 * CostHistory}/{@code MarginThreshold}/{@code Customer} all exist first.
 *
 * <p><b>Idempotency:</b> gated on the Price List's own {@code code} (unique)
 * existing already — if present, the entire run (including Calculation
 * Binding creation) is skipped, same "any row present = fully seeded"
 * trade-off every other demo seed runner here accepts.
 */
@Component
public class PriceListDemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PriceListDemoSeedRunner.class);

    static final String PRICE_LIST_CODE = "PL-DEMO-BRK-PAD-NEW-001";

    private static final String PRODUCT_SKU = "BRK-PAD-NEW-001";
    private static final String CUSTOMER_BUSINESS_KEY = "CUST-OEM-001";

    static final String BINDING_COUNTRY = "plv2_product_country_of_manufacture";
    static final String BINDING_COST_SEP = "plv2_cost_history_cost_2026_09";
    static final String BINDING_QTY_SEP = "plv2_cost_history_qty_2026_09";
    static final String BINDING_COST_OCT = "plv2_cost_history_cost_2026_10";
    static final String BINDING_QTY_OCT = "plv2_cost_history_qty_2026_10";
    static final String BINDING_COST_NOV = "plv2_cost_history_cost_2026_11";
    static final String BINDING_QTY_NOV = "plv2_cost_history_qty_2026_11";
    static final String BINDING_MARGIN_TIER = "plv2_margin_threshold_level";
    static final String BINDING_CUSTOMER_TIER = "plv2_customer_tier";

    /** Cost-plus markup applied to the weighted-average cost (seed-time business config, not a PO-mandated number). */
    private static final BigDecimal MARKUP_MULTIPLIER = new BigDecimal("1.35");

    private static final BigDecimal VIETNAM_ROUNDING_NEAREST = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_ROUNDING_NEAREST = new BigDecimal("1.00");

    private final PricingTaxonomyDemoSeedRunner pricingTaxonomyDemoSeedRunner;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final EntityInstanceRepository entityInstanceRepository;
    private final EntityInstanceAttributeValueRepository attributeValueRepository;
    private final CalculationBindingRepository calculationBindingRepository;
    private final PriceListV2Repository priceListV2Repository;
    private final PriceListService priceListService;

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    public PriceListDemoSeedRunner(
            PricingTaxonomyDemoSeedRunner pricingTaxonomyDemoSeedRunner,
            EntityDefinitionRepository entityDefinitionRepository,
            EntityInstanceRepository entityInstanceRepository,
            EntityInstanceAttributeValueRepository attributeValueRepository,
            CalculationBindingRepository calculationBindingRepository,
            PriceListV2Repository priceListV2Repository,
            PriceListService priceListService) {
        this.pricingTaxonomyDemoSeedRunner = pricingTaxonomyDemoSeedRunner;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.entityInstanceRepository = entityInstanceRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.calculationBindingRepository = calculationBindingRepository;
        this.priceListV2Repository = priceListV2Repository;
        this.priceListService = priceListService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        pricingTaxonomyDemoSeedRunner.run(args);

        if (priceListV2Repository.findByCode(PRICE_LIST_CODE).isPresent()) {
            log.info(
                    "Demo Price List '{}' already present — skipping seed (idempotent restart, S3-T7)",
                    PRICE_LIST_CODE);
            return;
        }

        EntityInstance product =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey("Product", PRODUCT_SKU)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Product SKU '"
                                                        + PRODUCT_SKU
                                                        + "' not found — PricingTaxonomyDemoSeedRunner should have"
                                                        + " seeded it already"));
        EntityInstance customer =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey("Customer", CUSTOMER_BUSINESS_KEY)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Customer '"
                                                        + CUSTOMER_BUSINESS_KEY
                                                        + "' not found — DemoDataSeedRunner should have seeded it"
                                                        + " already"));

        seedCalculationBindingsIfAbsent();

        BigDecimal roundingNearest = roundingNearestForCountryOf(product);
        List<DagNodeDto> graph = buildCalculationGraph(customer.getId(), roundingNearest);

        asPricingAdmin(
                () -> {
                    PriceListDto priceList =
                            priceListService.createPriceList(
                                    new CreatePriceListRequest(
                                            PRICE_LIST_CODE,
                                            "Demo Price List — " + PRODUCT_SKU + " (S3-T7)",
                                            "USD",
                                            LocalDate.of(2026, 11, 1),
                                            LocalDate.of(2027, 10, 31),
                                            graph,
                                            "roundedPrice"));

                    PriceListItemDto item =
                            priceListService.addItem(priceList.id(), new AddPriceListItemRequest(product.getId()));

                    priceListService.submit(priceList.id());
                    PriceListDto approved = priceListService.approve(priceList.id());

                    log.info(
                            "Seeded demo Price List '{}' (status={}): item id={}, resultPrice={}",
                            PRICE_LIST_CODE,
                            approved.status(),
                            item.id(),
                            item.resultPrice());
                    return null;
                });
    }

    private void seedCalculationBindingsIfAbsent() {
        createBindingIfAbsent(
                BINDING_COUNTRY,
                () ->
                        new CalculationBindingEntity(
                                BINDING_COUNTRY,
                                CalculationBindingSourceType.PRODUCT,
                                null,
                                "country_of_manufacture",
                                false));

        createBindingIfAbsent(BINDING_COST_SEP, () -> costHistoryBinding(BINDING_COST_SEP, "cost", "2026-09"));
        createBindingIfAbsent(BINDING_QTY_SEP, () -> costHistoryBinding(BINDING_QTY_SEP, "quantity", "2026-09"));
        createBindingIfAbsent(BINDING_COST_OCT, () -> costHistoryBinding(BINDING_COST_OCT, "cost", "2026-10"));
        createBindingIfAbsent(BINDING_QTY_OCT, () -> costHistoryBinding(BINDING_QTY_OCT, "quantity", "2026-10"));
        createBindingIfAbsent(BINDING_COST_NOV, () -> costHistoryBinding(BINDING_COST_NOV, "cost", "2026-11"));
        createBindingIfAbsent(BINDING_QTY_NOV, () -> costHistoryBinding(BINDING_QTY_NOV, "quantity", "2026-11"));

        createBindingIfAbsent(
                BINDING_MARGIN_TIER,
                () -> {
                    CalculationBindingEntity binding =
                            new CalculationBindingEntity(
                                    BINDING_MARGIN_TIER,
                                    CalculationBindingSourceType.PARAMETER,
                                    "MarginThreshold",
                                    "level",
                                    false);
                    // candidate.min_margin_pct <= marginPct
                    binding.addFilter(
                            new CalculationBindingFilterEntity(
                                    "min_margin_pct",
                                    CalculationBindingFilterOperator.LTE,
                                    CalculationBindingValueSource.CONTEXT_ATTRIBUTE,
                                    "marginPct"));
                    // candidate.max_margin_pct > marginPct (GREEN's open-ended upper
                    // bound is never written -- see QueryNodeExecutor.matches(): a
                    // null rawValue always fails a filter, so this AND-of-2-filters
                    // approach can only ever match a BOUNDED tier, never GREEN. This
                    // demo's markup (D-047) is deliberately chosen so margin% lands
                    // in YELLOW, a bounded tier, side-stepping that known limitation
                    // rather than touching QueryNodeExecutor (out of scope).
                    binding.addFilter(
                            new CalculationBindingFilterEntity(
                                    "max_margin_pct",
                                    CalculationBindingFilterOperator.GT,
                                    CalculationBindingValueSource.CONTEXT_ATTRIBUTE,
                                    "marginPct"));
                    return binding;
                });

        createBindingIfAbsent(
                BINDING_CUSTOMER_TIER,
                () ->
                        new CalculationBindingEntity(
                                BINDING_CUSTOMER_TIER, CalculationBindingSourceType.CUSTOMER, null, "tier", false));
    }

    private static CalculationBindingEntity costHistoryBinding(String code, String sourceAttributeCode, String period) {
        CalculationBindingEntity binding =
                new CalculationBindingEntity(
                        code, CalculationBindingSourceType.EXTENSION, "CostHistory", sourceAttributeCode, false);
        binding.addFilter(
                new CalculationBindingFilterEntity(
                        "period", CalculationBindingFilterOperator.EQ, CalculationBindingValueSource.CONSTANT, period));
        return binding;
    }

    private void createBindingIfAbsent(String attributeCode, Supplier<CalculationBindingEntity> factory) {
        if (calculationBindingRepository.findByAttributeCode(attributeCode).isEmpty()) {
            calculationBindingRepository.save(factory.get());
        }
    }

    /** See class Javadoc's "Rounding theo xuất xứ" note — a seed-time config decision, not a live DAG branch. */
    private BigDecimal roundingNearestForCountryOf(EntityInstance product) {
        EntityDefinition productDefinition =
                entityDefinitionRepository.findByCodeWithAttributes("Product").orElseThrow();
        AttributeDefinition countryAttribute = attributeByCode(productDefinition, "country_of_manufacture");
        String country =
                attributeValueRepository
                        .findByEntityInstanceAndAttributeDefinition(product, countryAttribute)
                        .map(EntityInstanceAttributeValue::getValueString)
                        .orElse(null);
        return "Vietnam".equals(country) ? VIETNAM_ROUNDING_NEAREST : DEFAULT_ROUNDING_NEAREST;
    }

    private static AttributeDefinition attributeByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "entity_def '" + entityDefinition.getCode() + "' has no attribute '" + code + "'"));
    }

    private List<DagNodeDto> buildCalculationGraph(Long customerId, BigDecimal roundingNearest) {
        DagNodeDto qCountry = queryNode("q_country", BINDING_COUNTRY);

        DagNodeDto qCostSep = queryNode("q_cost_sep", BINDING_COST_SEP);
        DagNodeDto qQtySep = queryNode("q_qty_sep", BINDING_QTY_SEP);
        DagNodeDto qCostOct = queryNode("q_cost_oct", BINDING_COST_OCT);
        DagNodeDto qQtyOct = queryNode("q_qty_oct", BINDING_QTY_OCT);
        DagNodeDto qCostNov = queryNode("q_cost_nov", BINDING_COST_NOV);
        DagNodeDto qQtyNov = queryNode("q_qty_nov", BINDING_QTY_NOV);

        DagNodeDto rowSep =
                new DagNodeDto("row_sep", "output", Map.of(), Map.of("cost", "q_cost_sep", "quantity", "q_qty_sep"));
        DagNodeDto rowOct =
                new DagNodeDto("row_oct", "output", Map.of(), Map.of("cost", "q_cost_oct", "quantity", "q_qty_oct"));
        DagNodeDto rowNov =
                new DagNodeDto("row_nov", "output", Map.of(), Map.of("cost", "q_cost_nov", "quantity", "q_qty_nov"));

        DagNodeDto costRows =
                new DagNodeDto(
                        "costRows",
                        "collect",
                        Map.of(),
                        Map.of("sep", "row_sep", "oct", "row_oct", "nov", "row_nov"));

        DagNodeDto avgCost =
                new DagNodeDto(
                        "avgCost",
                        "weighted-average",
                        Map.of("valueField", "cost", "weightField", "quantity"),
                        Map.of("rows", "costRows"));

        DagNodeDto markupPrice =
                new DagNodeDto(
                        "markupPrice",
                        "formula",
                        Map.of(
                                "expression",
                                json(
                                        "{\"*\": [{\"var\": \"avgCost\"}, "
                                                + MARKUP_MULTIPLIER
                                                + "]}")),
                        Map.of("avgCost", "avgCost"));

        DagNodeDto marginPct =
                new DagNodeDto(
                        "marginPct",
                        "formula",
                        Map.of(
                                "expression",
                                json(
                                        "{\"*\": [{\"/\": [{\"-\": [{\"var\": \"price\"}, {\"var\": \"cost\"}]},"
                                                + " {\"var\": \"price\"}]}, 100]}")),
                        Map.of("price", "markupPrice", "cost", "avgCost"));

        DagNodeDto qMarginTier =
                new DagNodeDto(
                        "q_marginTier",
                        "query",
                        Map.of("bindingAttributeCode", BINDING_MARGIN_TIER),
                        Map.of("marginPct", "marginPct"));

        DagNodeDto roundedPrice =
                new DagNodeDto("roundedPrice", "round", Map.of("nearest", roundingNearest), Map.of("value", "markupPrice"));

        DagNodeDto qCustomer =
                new DagNodeDto(
                        "q_customer",
                        "query",
                        Map.of("bindingAttributeCode", BINDING_CUSTOMER_TIER, "anchorInstanceId", customerId),
                        Map.of());

        DagNodeDto summary =
                new DagNodeDto(
                        "summary",
                        "output",
                        Map.of(),
                        Map.of(
                                "price", "roundedPrice",
                                "riskLevel", "q_marginTier",
                                "country", "q_country",
                                "customerTier", "q_customer"));

        return List.of(
                qCountry,
                qCostSep,
                qQtySep,
                qCostOct,
                qQtyOct,
                qCostNov,
                qQtyNov,
                rowSep,
                rowOct,
                rowNov,
                costRows,
                avgCost,
                markupPrice,
                marginPct,
                qMarginTier,
                roundedPrice,
                qCustomer,
                summary);
    }

    private static DagNodeDto queryNode(String id, String bindingAttributeCode) {
        return new DagNodeDto(id, "query", Map.of("bindingAttributeCode", bindingAttributeCode), Map.of());
    }

    private JsonNode json(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T asPricingAdmin(Supplier<T> action) {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        Authentication seedRunnerAuthentication =
                new UsernamePasswordAuthenticationToken(
                        "price-list-demo-seed-runner",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PRICING_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(seedRunnerAuthentication);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }
}
