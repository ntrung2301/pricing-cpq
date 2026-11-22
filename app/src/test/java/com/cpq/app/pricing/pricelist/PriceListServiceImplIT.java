package com.cpq.app.pricing.pricelist;

import com.cpq.app.AbstractIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.app.metamodel.EntityDefinitionDimensionAttributeRepository;
import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.EntityInstanceService;
import com.cpq.app.pricing.CalculationBindingRepository;
import com.cpq.app.pricing.PriceListItemV2Repository;
import com.cpq.app.pricing.PriceListV2Repository;
import com.cpq.core.api.pricelist.AddPriceListItemRequest;
import com.cpq.core.api.pricelist.CreatePriceListRequest;
import com.cpq.core.api.pricelist.DagNodeDto;
import com.cpq.core.api.pricelist.OverridePriceRequest;
import com.cpq.core.api.pricelist.PriceListDetailDto;
import com.cpq.core.api.pricelist.PriceListDto;
import com.cpq.core.api.pricelist.PriceListItemDto;
import com.cpq.core.api.pricelist.PriceListService;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityDefinitionDimensionAttribute;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityRole;
import com.cpq.core.pricing.dag.CalculationBindingSourceType;
import com.cpq.core.pricing.persistence.PriceListItemV2Entity;
import com.cpq.core.pricing.persistence.PriceListV2Entity;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S3-T6 Done-khi (sprint-3.md, backlog E8-05): a Price List whose {@code
 * calculation_graph} reads multiple sources (here: {@code PRODUCT} direct
 * attribute + an {@code EXTENSION} + an independent {@code PARAMETER},
 * same taxonomy as {@code QueryNodeExecutorIT}/{@code MeasureRefNodeExecutorIT}
 * — "entity giả định", not automotive/CostHistory/MarginThreshold, which
 * is S3-T7's job) produces the correct {@code result_price}; after {@code
 * approve}, the item's {@code result_price} is a permanent snapshot —
 * changing the source data afterwards and attempting to add/recompute an
 * item is blocked outright (chosen default per this task's "chặn HOẶC
 * giá trị không đổi" either/or, see docs/decisions.md D-046), and
 * re-reading the already-approved item shows its original value
 * unchanged. {@code manualOverridePrice} is the only field any API call
 * here can change, regardless of the price list's status.
 *
 * <p>Deliberately does NOT wire a {@code CUSTOMER} source — how Customer
 * participates in a Price List graph is explicitly an open question left
 * to S3-T7 (sprint-3.md's second NEEDS-PO-REVIEW), out of this task's
 * scope.
 */
@Testcontainers
@SpringBootTest
@WithMockUser(roles = "PRICING_ADMIN", username = "pricing-admin-1")
class PriceListServiceImplIT extends AbstractIntegrationTest {

    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityInstanceService entityInstanceService;
    @Autowired private EntityDefinitionDimensionAttributeRepository dimensionAttributeRepository;
    @Autowired private CalculationBindingRepository calculationBindingRepository;
    @Autowired private PriceListService priceListService;
    @Autowired private PriceListV2Repository priceListV2Repository;
    @Autowired private PriceListItemV2Repository priceListItemV2Repository;

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

    /**
     * A "Gadget" (PRODUCT-role) instance with a {@code unitCost} attribute,
     * one {@code GadgetDiscount} (EXTENSION) row with a {@code
     * volumeDiscountPct}, and one independent {@code MarginLevel}
     * (PARAMETER) row with a {@code marginPct} — three sources, same
     * fixture shape as {@code QueryNodeExecutorIT}.
     */
    private record MultiSourceFixture(
            EntityInstance gadget,
            String costBindingCode,
            String discountBindingCode,
            String marginBindingCode,
            BigDecimal unitCost,
            BigDecimal discountPct,
            BigDecimal marginPct,
            EntityInstance discountRow,
            AttributeDefinition discountPctAttribute) {}

    private MultiSourceFixture createMultiSourceFixture() {
        String suffix = UUID.randomUUID().toString();

        // PRODUCT: Gadget.unitCost
        AttributeDefinition code = new AttributeDefinition("code", "Code", AttributeDataType.STRING, true, null);
        AttributeDefinition unitCostAttr =
                new AttributeDefinition("unitCost", "Unit Cost", AttributeDataType.DECIMAL, false, null);
        EntityDefinition gadgetDef = new EntityDefinition("Gadget_" + suffix, "Gadget", 1);
        gadgetDef.addAttribute(code);
        gadgetDef.addAttribute(unitCostAttr);
        EntityDefinition savedGadgetDef = entityDefinitionRepository.save(gadgetDef);
        savedGadgetDef.designateKeyAttribute(findByCode(savedGadgetDef, "code"));
        savedGadgetDef = entityDefinitionRepository.save(savedGadgetDef);
        AttributeDefinition savedCode = findByCode(savedGadgetDef, "code");
        AttributeDefinition savedUnitCost = findByCode(savedGadgetDef, "unitCost");

        EntityInstance gadget = entityInstanceService.createInstance(savedGadgetDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(gadget, savedCode, AttributeValue.ofString("GADGET-" + suffix));
        BigDecimal unitCost = new BigDecimal("100.00");
        entityInstanceService.writeAttributeValue(gadget, savedUnitCost, AttributeValue.ofNumber(unitCost));

        // EXTENSION: GadgetDiscount.volumeDiscountPct, one row per anchor
        AttributeDefinition period = new AttributeDefinition("period", "Period", AttributeDataType.STRING, true, null);
        AttributeDefinition discountPctAttr =
                new AttributeDefinition("volumeDiscountPct", "Volume Discount Pct", AttributeDataType.DECIMAL, false, null);
        EntityDefinition discountDef = new EntityDefinition("GadgetDiscount_" + suffix, "Gadget Discount", 1);
        discountDef.addAttribute(period);
        discountDef.addAttribute(discountPctAttr);
        discountDef.assignRole(EntityRole.EXTENSION);
        discountDef.assignExtendsEntityDefinition(savedGadgetDef);
        EntityDefinition savedDiscountDef = entityDefinitionRepository.save(discountDef);
        AttributeDefinition savedPeriod = findByCode(savedDiscountDef, "period");
        dimensionAttributeRepository.save(new EntityDefinitionDimensionAttribute(savedDiscountDef, savedPeriod, 0));
        AttributeDefinition savedDiscountPct = findByCode(savedDiscountDef, "volumeDiscountPct");

        EntityInstance discountRow =
                entityInstanceService.createInstance(savedDiscountDef, "test-pack", 1, gadget.getId());
        entityInstanceService.writeAttributeValue(discountRow, savedPeriod, AttributeValue.ofString("2026-11"));
        BigDecimal discountPct = new BigDecimal("0.10");
        entityInstanceService.writeAttributeValue(discountRow, savedDiscountPct, AttributeValue.ofNumber(discountPct));

        // PARAMETER: MarginLevel.marginPct, one independent global row
        AttributeDefinition level = new AttributeDefinition("level", "Level", AttributeDataType.STRING, true, null);
        AttributeDefinition marginPctAttr =
                new AttributeDefinition("marginPct", "Margin Pct", AttributeDataType.DECIMAL, false, null);
        EntityDefinition marginDef = new EntityDefinition("MarginLevel_" + suffix, "Margin Level", 1);
        marginDef.addAttribute(level);
        marginDef.addAttribute(marginPctAttr);
        marginDef.assignRole(EntityRole.PARAMETER);
        EntityDefinition savedMarginDef = entityDefinitionRepository.save(marginDef);
        AttributeDefinition savedLevel = findByCode(savedMarginDef, "level");
        AttributeDefinition savedMarginPct = findByCode(savedMarginDef, "marginPct");

        EntityInstance marginRow = entityInstanceService.createInstance(savedMarginDef, "test-pack", 1);
        entityInstanceService.writeAttributeValue(marginRow, savedLevel, AttributeValue.ofString("STANDARD"));
        BigDecimal marginPct = new BigDecimal("0.20");
        entityInstanceService.writeAttributeValue(marginRow, savedMarginPct, AttributeValue.ofNumber(marginPct));

        String costBindingCode = "plv2Cost_" + suffix;
        String discountBindingCode = "plv2Discount_" + suffix;
        String marginBindingCode = "plv2Margin_" + suffix;
        calculationBindingRepository.save(
                new com.cpq.core.pricing.persistence.CalculationBindingEntity(
                        costBindingCode, CalculationBindingSourceType.PRODUCT, null, "unitCost", false));
        calculationBindingRepository.save(
                new com.cpq.core.pricing.persistence.CalculationBindingEntity(
                        discountBindingCode,
                        CalculationBindingSourceType.EXTENSION,
                        savedDiscountDef.getCode(),
                        "volumeDiscountPct",
                        false));
        calculationBindingRepository.save(
                new com.cpq.core.pricing.persistence.CalculationBindingEntity(
                        marginBindingCode,
                        CalculationBindingSourceType.PARAMETER,
                        savedMarginDef.getCode(),
                        "marginPct",
                        false));

        return new MultiSourceFixture(
                gadget,
                costBindingCode,
                discountBindingCode,
                marginBindingCode,
                unitCost,
                discountPct,
                marginPct,
                discountRow,
                savedDiscountPct);
    }

    /** {@code cost * (1 - discount) * (1 + margin)}, rounded to nearest 0.01 (CEILING, matching RoundNodeExecutor). */
    private BigDecimal expectedPrice(MultiSourceFixture fixture) {
        BigDecimal raw =
                fixture.unitCost()
                        .multiply(BigDecimal.ONE.subtract(fixture.discountPct()))
                        .multiply(BigDecimal.ONE.add(fixture.marginPct()));
        BigDecimal nearest = new BigDecimal("0.01");
        return raw.divide(nearest, 0, java.math.RoundingMode.CEILING).multiply(nearest);
    }

    private List<DagNodeDto> buildGraph(MultiSourceFixture fixture) {
        return List.of(
                new DagNodeDto("q_cost", "query", Map.of("bindingAttributeCode", fixture.costBindingCode()), Map.of()),
                new DagNodeDto(
                        "q_discount", "query", Map.of("bindingAttributeCode", fixture.discountBindingCode()), Map.of()),
                new DagNodeDto(
                        "q_margin", "query", Map.of("bindingAttributeCode", fixture.marginBindingCode()), Map.of()),
                new DagNodeDto(
                        "discounted",
                        "formula",
                        Map.of(
                                "expression",
                                json(
                                        "{\"*\": [{\"var\": \"cost\"}, {\"-\": [1, {\"var\": \"discount\"}]}]}")),
                        Map.of("cost", "q_cost", "discount", "q_discount")),
                new DagNodeDto(
                        "withMargin",
                        "formula",
                        Map.of(
                                "expression",
                                json("{\"*\": [{\"var\": \"base\"}, {\"+\": [1, {\"var\": \"margin\"}]}]}")),
                        Map.of("base", "discounted", "margin", "q_margin")),
                new DagNodeDto("rounded", "round", Map.of("nearest", new BigDecimal("0.01")), Map.of("value", "withMargin")));
    }

    private PriceListDto createDraftPriceList(MultiSourceFixture fixture, String code) {
        CreatePriceListRequest request =
                new CreatePriceListRequest(
                        code, "Test Price List", "USD", null, null, buildGraph(fixture), "rounded");
        return priceListService.createPriceList(request);
    }

    @Test
    void createPriceListStartsAsDraftWithCreatedByFromTheCurrentPrincipal() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto created = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.createdBy()).isEqualTo("pricing-admin-1");
        assertThat(created.submittedAt()).isNull();
        assertThat(created.approvedBy()).isNull();
    }

    @Test
    void addItemRunsTheMultiSourceGraphAndStoresTheCorrectResultPriceAndFullWaterfall() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto priceList = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());

        PriceListItemDto item =
                priceListService.addItem(
                        priceList.id(), new AddPriceListItemRequest(fixture.gadget().getId()));

        BigDecimal expected = expectedPrice(fixture);
        assertThat(item.resultPrice()).isEqualByComparingTo(expected);
        assertThat(item.manualOverridePrice()).isNull();
        assertThat(item.priceListId()).isEqualTo(priceList.id());
        assertThat(item.entityInstanceId()).isEqualTo(fixture.gadget().getId());
        assertThat(item.waterfallSteps()).isNotEmpty();
        assertThat(item.waterfallSteps().stream().map(step -> step.nodeId()).toList())
                .contains("q_cost", "q_discount", "q_margin", "discounted", "withMargin", "rounded");

        // Persisted row matches the returned DTO exactly (no silent divergence).
        PriceListItemV2Entity persisted = priceListItemV2Repository.findById(item.id()).orElseThrow();
        assertThat(persisted.getResultPrice()).isEqualByComparingTo(expected);
        assertThat(persisted.getWaterfallSteps()).isNotEmpty();
    }

    @Test
    void submitMovesDraftToPendingApprovalAndRejectsASecondSubmit() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto priceList = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());

        PriceListDto submitted = priceListService.submit(priceList.id());
        assertThat(submitted.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(submitted.submittedAt()).isNotNull();

        assertThatThrownBy(() -> priceListService.submit(priceList.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    void approveRequiresPendingApprovalAndRecordsApprover() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto priceList = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());

        assertThatThrownBy(() -> priceListService.approve(priceList.id()))
                .as("cannot approve a DRAFT price list directly, must submit first")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRAFT");

        priceListService.submit(priceList.id());
        PriceListDto approved = priceListService.approve(priceList.id());

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.approvedBy()).isEqualTo("pricing-admin-1");
        assertThat(approved.approvedAt()).isNotNull();

        assertThatThrownBy(() -> priceListService.approve(priceList.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void approvedPriceListIsAPermanentSnapshotAddingAnItemAfterApprovalIsBlockedAndSourceDataChangesNeverLeak() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto priceList = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());

        PriceListItemDto itemBeforeApproval =
                priceListService.addItem(
                        priceList.id(), new AddPriceListItemRequest(fixture.gadget().getId()));
        BigDecimal expectedBeforeChange = expectedPrice(fixture);
        assertThat(itemBeforeApproval.resultPrice()).isEqualByComparingTo(expectedBeforeChange);

        priceListService.submit(priceList.id());
        priceListService.approve(priceList.id());

        // Change the EXTENSION source's value after approval (ADR-009 mục 5's
        // scenario: "sửa dữ liệu nguồn (vd attribute 1 Extension instance)") —
        // proves the block below isn't merely coincidental with stale data.
        // Reuses the EntityInstance/AttributeDefinition object references the
        // fixture already holds in memory (not a fresh repository re-fetch) —
        // same convention every other test in this codebase follows, since
        // EntityInstance.entityDefinition is a lazy association and a
        // detached, freshly-queried instance cannot lazy-load it outside the
        // (already-closed) transaction that loaded it.
        entityInstanceService.writeAttributeValue(
                fixture.discountRow(), fixture.discountPctAttribute(), AttributeValue.ofNumber(new BigDecimal("0.50")));

        assertThatThrownBy(
                        () ->
                                priceListService.addItem(
                                        priceList.id(), new AddPriceListItemRequest(fixture.gadget().getId())))
                .as("approved price list must block further add/recompute (permanent snapshot)")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("APPROVED");

        // Re-reading the already-approved item shows its original value, unaffected
        // by anything that happened to source data afterwards.
        PriceListItemV2Entity persisted = priceListItemV2Repository.findById(itemBeforeApproval.id()).orElseThrow();
        assertThat(persisted.getResultPrice()).isEqualByComparingTo(expectedBeforeChange);
    }

    @Test
    void addItemOnAnUnknownPriceListReturns404() {
        assertThatThrownBy(() -> priceListService.addItem(-1L, new AddPriceListItemRequest(-1L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void addItemWithAnUnknownEntityInstanceIdReturns404() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto priceList = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());

        assertThatThrownBy(() -> priceListService.addItem(priceList.id(), new AddPriceListItemRequest(-999L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void overridePriceIsTheOnlyFieldChangedAndWorksRegardlessOfPriceListStatus() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto priceList = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());
        PriceListItemDto item =
                priceListService.addItem(
                        priceList.id(), new AddPriceListItemRequest(fixture.gadget().getId()));
        BigDecimal originalResultPrice = item.resultPrice();

        PriceListItemDto overridden =
                priceListService.overridePrice(item.id(), new OverridePriceRequest(new BigDecimal("1.00")));

        assertThat(overridden.manualOverridePrice()).isEqualByComparingTo("1.00");
        assertThat(overridden.resultPrice()).isEqualByComparingTo(originalResultPrice);

        priceListService.submit(priceList.id());
        priceListService.approve(priceList.id());

        PriceListItemDto overriddenAfterApproval =
                priceListService.overridePrice(item.id(), new OverridePriceRequest(new BigDecimal("2.00")));
        assertThat(overriddenAfterApproval.manualOverridePrice()).isEqualByComparingTo("2.00");
        assertThat(overriddenAfterApproval.resultPrice()).isEqualByComparingTo(originalResultPrice);
    }

    @Test
    void overridePriceOnAnUnknownItemReturns404() {
        assertThatThrownBy(() -> priceListService.overridePrice(-1L, new OverridePriceRequest(BigDecimal.TEN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    // --- S3-T8: GET /api/price-lists-v2/{id} (docs/decisions.md D-049) ---
    // S3-T6 shipped create/addItem/submit/approve/override but no read-back
    // endpoint; the Measure Canvas UI needs one to show the Calculation
    // Trace after pricing an item, so this task adds the missing GET.

    @Test
    void getPriceListReturnsHeaderPlusCalculationGraphAndItemsWithWaterfallSteps() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto priceList = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());
        PriceListItemDto item =
                priceListService.addItem(
                        priceList.id(), new AddPriceListItemRequest(fixture.gadget().getId()));

        PriceListDetailDto detail = priceListService.getPriceList(priceList.id());

        assertThat(detail.id()).isEqualTo(priceList.id());
        assertThat(detail.code()).isEqualTo(priceList.code());
        assertThat(detail.status()).isEqualTo("DRAFT");
        assertThat(detail.resultNodeId()).isEqualTo("rounded");

        assertThat(detail.calculationGraph()).hasSameSizeAs(buildGraph(fixture));
        assertThat(detail.calculationGraph().stream().map(DagNodeDto::id).toList())
                .contains("q_cost", "q_discount", "q_margin", "discounted", "withMargin", "rounded");

        assertThat(detail.items()).hasSize(1);
        PriceListItemDto detailItem = detail.items().get(0);
        assertThat(detailItem.id()).isEqualTo(item.id());
        assertThat(detailItem.resultPrice()).isEqualByComparingTo(item.resultPrice());
        assertThat(detailItem.waterfallSteps()).isNotEmpty();
        assertThat(detailItem.waterfallSteps().stream().map(step -> step.nodeId()).toList())
                .contains("q_cost", "q_discount", "q_margin", "discounted", "withMargin", "rounded");
    }

    @Test
    void getPriceListOnAnUnknownIdReturns404() {
        assertThatThrownBy(() -> priceListService.getPriceList(-1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void listPriceListsReturnsEveryPriceListNewestFirst() {
        MultiSourceFixture fixture = createMultiSourceFixture();
        PriceListDto first = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());
        PriceListDto second = createDraftPriceList(fixture, "PLV2_" + UUID.randomUUID());

        List<PriceListDto> all = priceListService.listPriceLists();

        List<Long> ids = all.stream().map(PriceListDto::id).toList();
        assertThat(ids).contains(first.id(), second.id());
        assertThat(ids.indexOf(second.id())).isLessThan(ids.indexOf(first.id()));

        PriceListDto found = all.stream().filter(pl -> pl.id().equals(second.id())).findFirst().orElseThrow();
        assertThat(found.code()).isEqualTo(second.code());
        assertThat(found.status()).isEqualTo("DRAFT");
    }
}
