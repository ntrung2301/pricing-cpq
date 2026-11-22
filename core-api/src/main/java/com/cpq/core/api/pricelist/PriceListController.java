package com.cpq.core.api.pricelist;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/price-lists-v2/...} (S3-T6, E8-05): the DAG-engine-based
 * Price List module's REST surface. Thin by design — the real logic
 * (state machine, DAG execution, {@code @PreAuthorize} checks) lives in
 * {@link PriceListService}, same controller/service split as every other
 * controller in this package family ({@code ConfiguratorController},
 * {@code ImportController}).
 *
 * <p>Deliberately a separate route prefix from the Sprint 2 {@code
 * RuleEngine}-based {@code /api/pricing/calculate} — this module's
 * {@code price_list_v2}/{@code price_list_item_v2} tables are an
 * independent, parallel schema (sprint-3.md's "NEEDS-PO-REVIEW đang mở":
 * no decision yet on migrating/retiring the old engine).
 */
@RestController
public class PriceListController {

    private final PriceListService priceListService;

    public PriceListController(PriceListService priceListService) {
        this.priceListService = priceListService;
    }

    @PostMapping("/api/price-lists-v2")
    public PriceListDto createPriceList(@RequestBody CreatePriceListRequest request) {
        return priceListService.createPriceList(request);
    }

    @GetMapping("/api/price-lists-v2")
    public List<PriceListDto> listPriceLists() {
        return priceListService.listPriceLists();
    }

    @PostMapping("/api/price-lists-v2/{id}/items")
    public PriceListItemDto addItem(@PathVariable Long id, @RequestBody AddPriceListItemRequest request) {
        return priceListService.addItem(id, request);
    }

    @PostMapping("/api/price-lists-v2/{id}/submit")
    public PriceListDto submit(@PathVariable Long id) {
        return priceListService.submit(id);
    }

    @PostMapping("/api/price-lists-v2/{id}/approve")
    public PriceListDto approve(@PathVariable Long id) {
        return priceListService.approve(id);
    }

    @PatchMapping("/api/price-lists-v2/items/{itemId}/override")
    public PriceListItemDto overridePrice(
            @PathVariable Long itemId, @RequestBody OverridePriceRequest request) {
        return priceListService.overridePrice(itemId, request);
    }

    /** S3-T8 (docs/decisions.md D-049): read-back for the Measure Canvas UI. */
    @GetMapping("/api/price-lists-v2/{id}")
    public PriceListDetailDto getPriceList(@PathVariable Long id) {
        return priceListService.getPriceList(id);
    }
}
