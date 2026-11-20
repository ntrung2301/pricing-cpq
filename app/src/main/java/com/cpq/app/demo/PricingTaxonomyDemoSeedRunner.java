package com.cpq.app.demo;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds real demo data for ADR-009 mục 1's entity taxonomy v2 (S3-T4,
 * sprint-3.md, backlog E8-02), on top of the {@code Product}/{@code
 * Customer} data {@link DemoDataSeedRunner} (S2-T6) already seeds, now
 * extended by the automotive {@code pack.yaml} (S3-T4) with {@code
 * Product.country_of_manufacture}, the new {@code CostHistory} ({@code
 * role=EXTENSION} of {@code Product}, dimension {@code period}) and {@code
 * MarginThreshold} ({@code role=PARAMETER}, key {@code level}) entity
 * definitions:
 *
 * <ol>
 *   <li>{@code CostHistory}: 3 instances (periods {@code "2026-09"},
 *       {@code "2026-10"}, {@code "2026-11"}) anchored to {@value
 *       #COST_HISTORY_SKU} — cost/quantity figures are illustrative demo
 *       numbers in the range a later weighted-average-cost DAG node
 *       (S3-T7) will read, not real supplier data.
 *   <li>{@code MarginThreshold}: the 4 risk levels PO specified (D-038 mục
 *       4) — {@code CRITICAL} [0, 15), {@code RED} [15, 20), {@code
 *       YELLOW} [20, 30), {@code GREEN} [30, ∞) (no {@code max_margin_pct}
 *       for the top tier).
 * </ol>
 *
 * <p>{@code Product.country_of_manufacture} itself needs no seeding code
 * here — it's just a new, optional column on {@code products.csv}
 * (S3-T4), populated by {@link DemoDataSeedRunner}'s existing {@code
 * CatalogImportService} call the first time the app starts against an
 * empty database.
 *
 * <p><b>Why a separate runner:</b> same reasoning {@code
 * AutomotiveFitmentSeedRunner} (D-029) and {@code AutomotivePricingSeedRunner}
 * already gave — this task's data shape (entity taxonomy v2 instances) is
 * different from S2-T6's CSV-only import, so it gets its own runner/
 * Definition of Done rather than growing an existing one.
 *
 * <p><b>Ordering:</b> same strategy as those two runners — rather than
 * rely on Spring Boot's unspecified invocation order between {@code
 * ApplicationRunner} beans, this runner calls {@link
 * DemoDataSeedRunner#run} itself first (idempotent, so a harmless no-op if
 * Spring already ran it), guaranteeing {@code Product} instances (and the
 * {@code CostHistory}/{@code MarginThreshold} {@code entity_def} rows,
 * loaded by {@code PackLoaderService} as part of that call) exist first,
 * regardless of what order Spring actually chose. This runner does not
 * need the fitment/supersession data {@code AutomotiveFitmentSeedRunner}
 * adds, so it depends on {@code DemoDataSeedRunner} directly.
 *
 * <p><b>Idempotency on restart:</b> same "any row present = fully seeded"
 * trade-off {@code DemoDataSeedRunner} (D-026 point 4) already accepted —
 * if any {@code CostHistory} (respectively {@code MarginThreshold}) {@code
 * entity_instance} row already exists, that whole seeding step is skipped.
 */
@Component
public class PricingTaxonomyDemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PricingTaxonomyDemoSeedRunner.class);

    static final String COST_HISTORY_ENTITY_DEF_CODE = "CostHistory";
    static final String MARGIN_THRESHOLD_ENTITY_DEF_CODE = "MarginThreshold";

    private static final String COST_HISTORY_SKU = "BRK-PAD-NEW-001";

    private record CostHistoryRow(String period, BigDecimal cost, BigDecimal quantity) {}

    private record MarginThresholdRow(String level, BigDecimal minMarginPct, BigDecimal maxMarginPct) {}

    private static final List<CostHistoryRow> COST_HISTORY_PLAN =
            List.of(
                    new CostHistoryRow("2026-09", new BigDecimal("40.00"), new BigDecimal("120")),
                    new CostHistoryRow("2026-10", new BigDecimal("42.50"), new BigDecimal("95")),
                    new CostHistoryRow("2026-11", new BigDecimal("44.00"), new BigDecimal("150")));

    /** 4 risk levels per D-038 mục 4 / backlog E8-02: CRITICAL&lt;15%/RED&lt;20%/YELLOW&lt;30%/GREEN, no upper bound on GREEN. */
    private static final List<MarginThresholdRow> MARGIN_THRESHOLD_PLAN =
            List.of(
                    new MarginThresholdRow("CRITICAL", new BigDecimal("0"), new BigDecimal("15")),
                    new MarginThresholdRow("RED", new BigDecimal("15"), new BigDecimal("20")),
                    new MarginThresholdRow("YELLOW", new BigDecimal("20"), new BigDecimal("30")),
                    new MarginThresholdRow("GREEN", new BigDecimal("30"), null));

    private final DemoDataSeedRunner demoDataSeedRunner;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final EntityInstanceRepository entityInstanceRepository;
    private final EntityInstanceService entityInstanceService;

    public PricingTaxonomyDemoSeedRunner(
            DemoDataSeedRunner demoDataSeedRunner,
            EntityDefinitionRepository entityDefinitionRepository,
            EntityInstanceRepository entityInstanceRepository,
            EntityInstanceService entityInstanceService) {
        this.demoDataSeedRunner = demoDataSeedRunner;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.entityInstanceRepository = entityInstanceRepository;
        this.entityInstanceService = entityInstanceService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        demoDataSeedRunner.run(args);

        seedCostHistoryIfAbsent();
        seedMarginThresholdIfAbsent();
    }

    private void seedCostHistoryIfAbsent() {
        if (!entityInstanceRepository.findByEntityDefinition_Code(COST_HISTORY_ENTITY_DEF_CODE).isEmpty()) {
            log.info(
                    "CostHistory demo instances already present — skipping seed (idempotent restart, S3-T4)");
            return;
        }

        EntityDefinition costHistoryDefinition = requireEntityDefinition(COST_HISTORY_ENTITY_DEF_CODE);
        AttributeDefinition period = attributeByCode(costHistoryDefinition, "period");
        AttributeDefinition cost = attributeByCode(costHistoryDefinition, "cost");
        AttributeDefinition quantity = attributeByCode(costHistoryDefinition, "quantity");

        EntityInstance product =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey(
                                DemoDataSeedRunner.PRODUCT_ENTITY_DEF_CODE, COST_HISTORY_SKU)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Product SKU '"
                                                        + COST_HISTORY_SKU
                                                        + "' not found — DemoDataSeedRunner should have seeded it"
                                                        + " already (S2-T6)"));

        int created = 0;
        for (CostHistoryRow row : COST_HISTORY_PLAN) {
            EntityInstance instance =
                    entityInstanceService.createInstance(
                            costHistoryDefinition,
                            costHistoryDefinition.getPackId(),
                            costHistoryDefinition.getPackVersion(),
                            product.getId());
            entityInstanceService.writeAttributeValue(instance, cost, AttributeValue.ofNumber(row.cost()));
            entityInstanceService.writeAttributeValue(
                    instance, quantity, AttributeValue.ofNumber(row.quantity()));
            entityInstanceService.writeAttributeValue(instance, period, AttributeValue.ofString(row.period()));
            created++;
        }
        log.info("Seeded {} CostHistory instance(s) for Product '{}'", created, COST_HISTORY_SKU);
    }

    private void seedMarginThresholdIfAbsent() {
        if (!entityInstanceRepository.findByEntityDefinition_Code(MARGIN_THRESHOLD_ENTITY_DEF_CODE).isEmpty()) {
            log.info(
                    "MarginThreshold demo instances already present — skipping seed (idempotent restart,"
                            + " S3-T4)");
            return;
        }

        EntityDefinition marginThresholdDefinition = requireEntityDefinition(MARGIN_THRESHOLD_ENTITY_DEF_CODE);
        AttributeDefinition level = attributeByCode(marginThresholdDefinition, "level");
        AttributeDefinition minMarginPct = attributeByCode(marginThresholdDefinition, "min_margin_pct");
        AttributeDefinition maxMarginPct = attributeByCode(marginThresholdDefinition, "max_margin_pct");

        int created = 0;
        for (MarginThresholdRow row : MARGIN_THRESHOLD_PLAN) {
            EntityInstance instance =
                    entityInstanceService.createInstance(
                            marginThresholdDefinition,
                            marginThresholdDefinition.getPackId(),
                            marginThresholdDefinition.getPackVersion());
            entityInstanceService.writeAttributeValue(
                    instance, minMarginPct, AttributeValue.ofNumber(row.minMarginPct()));
            if (row.maxMarginPct() != null) {
                entityInstanceService.writeAttributeValue(
                        instance, maxMarginPct, AttributeValue.ofNumber(row.maxMarginPct()));
            }
            entityInstanceService.writeAttributeValue(instance, level, AttributeValue.ofString(row.level()));
            created++;
        }
        log.info("Seeded {} MarginThreshold instance(s)", created);
    }

    private EntityDefinition requireEntityDefinition(String code) {
        return entityDefinitionRepository
                .findByCodeWithAttributes(code)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "entity_def '"
                                                + code
                                                + "' not found — pack.yaml should have defined it already"
                                                + " (S3-T4)"));
    }

    private static AttributeDefinition attributeByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "entity_def '"
                                                + entityDefinition.getCode()
                                                + "' has no attribute '"
                                                + code
                                                + "'"));
    }
}
