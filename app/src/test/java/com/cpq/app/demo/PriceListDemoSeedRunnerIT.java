package com.cpq.app.demo;

import com.cpq.app.AbstractIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cpq.app.pricing.PriceListItemV2Repository;
import com.cpq.app.pricing.PriceListV2Repository;
import com.cpq.core.pricing.dag.TraceEntry;
import com.cpq.core.pricing.persistence.PriceListItemV2Entity;
import com.cpq.core.pricing.persistence.PriceListV2Entity;
import com.cpq.core.pricing.persistence.PriceListV2Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S3-T7 Done-khi (sprint-3.md, backlog E8-07): a real Price List, created
 * through the real S3-T6 {@code PriceListService} API (not a test fixture,
 * not mocked persistence), whose {@code calculation_graph} reads all 4
 * required sources — {@code Product} (direct attribute), {@code
 * CostHistory} (EXTENSION, weighted-average cost over 3 real rows), {@code
 * MarginThreshold} (PARAMETER, margin risk lookup) and {@code Customer} (a
 * side-output node, not an ancestor of {@code result_price}) — and ends up
 * {@code APPROVED} with a correct {@code result_price} and risk level.
 *
 * <p>{@link PriceListDemoSeedRunner} is a genuine {@code ApplicationRunner}
 * bean, so Spring Boot's real context startup already invoked it (same
 * framing as {@code PricingTaxonomyDemoSeedRunnerIT}, D-041) — this test
 * never calls any seeding method directly except to simulate a restart for
 * the idempotency check.
 */
@Testcontainers
@SpringBootTest
class PriceListDemoSeedRunnerIT extends AbstractIntegrationTest {

    @Autowired private PriceListDemoSeedRunner priceListDemoSeedRunner;
    @Autowired private PriceListV2Repository priceListV2Repository;
    @Autowired private PriceListItemV2Repository priceListItemV2Repository;

    private PriceListV2Entity requireDemoPriceList() {
        return priceListV2Repository.findByCode(PriceListDemoSeedRunner.PRICE_LIST_CODE).orElseThrow();
    }

    private PriceListItemV2Entity requireSoleItem(PriceListV2Entity priceList) {
        List<PriceListItemV2Entity> items = priceListItemV2Repository.findByPriceList_Id(priceList.getId());
        assertThat(items).hasSize(1);
        return items.get(0);
    }

    private static TraceEntry requireTraceEntry(PriceListItemV2Entity item, String nodeId) {
        Optional<TraceEntry> entry =
                item.getWaterfallSteps().stream().filter(step -> step.nodeId().equals(nodeId)).findFirst();
        assertThat(entry).as("trace entry for node '" + nodeId + "'").isPresent();
        return entry.get();
    }

    @Test
    void demoPriceListIsApprovedWithACorrectResultPrice() {
        PriceListV2Entity priceList = requireDemoPriceList();

        assertThat(priceList.getStatus()).isEqualTo(PriceListV2Status.APPROVED);
        assertThat(priceList.getApprovedBy()).isNotBlank();
        assertThat(priceList.getApprovedAt()).isNotNull();

        PriceListItemV2Entity item = requireSoleItem(priceList);

        // Weighted-average cost over the 3 real CostHistory rows seeded by
        // PricingTaxonomyDemoSeedRunner/D-043: (40*120 + 42.5*95 + 44*150) / 365
        // = 42.2945205479 (scale 10, HALF_UP, WeightedAverageNodeExecutor).
        // *1.35 markup = 57.097602739665; rounded CEILING to nearest 0.50
        // (Vietnam -- D-047) = 57.50.
        assertThat(item.getResultPrice()).isEqualByComparingTo("57.50");
        assertThat(item.getManualOverridePrice()).isNull();
    }

    @Test
    void waterfallReadsAllFourSourcesAndTheMarginRiskLevelNeverAffectsTheResultPrice() {
        PriceListV2Entity priceList = requireDemoPriceList();
        PriceListItemV2Entity item = requireSoleItem(priceList);

        // 1. Product: country_of_manufacture, read directly.
        assertThat(requireTraceEntry(item, "q_country").output()).isEqualTo("Vietnam");

        // 2. CostHistory (EXTENSION): weighted-average cost over 3 real rows.
        assertThat((BigDecimal) requireTraceEntry(item, "avgCost").output())
                .isEqualByComparingTo("42.2945205479");

        // 3. MarginThreshold (PARAMETER): margin risk level, a pure side
        // output -- not an ancestor of roundedPrice (no cycle).
        assertThat(requireTraceEntry(item, "q_marginTier").output()).isEqualTo("YELLOW");
        assertThat((BigDecimal) requireTraceEntry(item, "marginPct").output())
                .isEqualByComparingTo("25.92592593");

        // 4. Customer: read directly, never wired into roundedPrice.
        assertThat(requireTraceEntry(item, "q_customer").output()).isEqualTo("OEM");

        // result_price itself is untouched by the Customer/margin-tier side outputs.
        assertThat((BigDecimal) requireTraceEntry(item, "roundedPrice").output())
                .isEqualByComparingTo(item.getResultPrice());
    }

    @Test
    void runningSeedAgainSimulatingRestartIsIdempotentAndDoesNotDuplicateThePriceListOrItsItem() {
        PriceListV2Entity priceListBefore = requireDemoPriceList();
        int itemCountBefore = priceListItemV2Repository.findByPriceList_Id(priceListBefore.getId()).size();

        assertThatCode(() -> priceListDemoSeedRunner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        List<PriceListV2Entity> all = priceListV2Repository.findAll();
        long matchingCode =
                all.stream().filter(pl -> pl.getCode().equals(PriceListDemoSeedRunner.PRICE_LIST_CODE)).count();
        assertThat(matchingCode).isEqualTo(1);

        PriceListV2Entity priceListAfter = requireDemoPriceList();
        assertThat(priceListItemV2Repository.findByPriceList_Id(priceListAfter.getId())).hasSize(itemCountBefore);
    }
}
