package com.cpq.core.api.pricelist;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Port {@link PriceListController} depends on for the DAG-engine-based
 * Price List module (S3-T6, ADR-009 mục 5, backlog E8-05) — same
 * interface-in-core-api/implementation-in-app split as {@code
 * ConfiguratorService}/{@code CatalogImportService}, for the same
 * ADR-005 reason: the real implementation ({@code
 * com.cpq.app.pricing.pricelist.PriceListServiceImpl}) needs {@code
 * PriceListV2Repository}/{@code PriceListItemV2Repository}/{@code
 * MeasureAwareDagEngine}, all of which live in {@code app}.
 *
 * <p>{@code @PreAuthorize} is declared here, on the interface method, for
 * the same JDK-dynamic-proxy reason documented on {@code
 * CatalogImportService}. Every operation here creates/mutates pricing
 * data (a new Price List, a priced item, a lifecycle transition, a manual
 * override) — same scope decision as {@code CatalogImportService}: only
 * {@code PRICING_ADMIN} may call any of them (this task's RBAC
 * instructions: "PRICING_ADMIN cho hành động tạo/submit/approve/
 * override").
 */
public interface PriceListService {

    @PreAuthorize("hasRole('PRICING_ADMIN')")
    PriceListDto createPriceList(CreatePriceListRequest request);

    @PreAuthorize("hasRole('PRICING_ADMIN')")
    PriceListItemDto addItem(Long priceListId, AddPriceListItemRequest request);

    @PreAuthorize("hasRole('PRICING_ADMIN')")
    PriceListDto submit(Long priceListId);

    @PreAuthorize("hasRole('PRICING_ADMIN')")
    PriceListDto approve(Long priceListId);

    @PreAuthorize("hasRole('PRICING_ADMIN')")
    PriceListItemDto overridePrice(Long itemId, OverridePriceRequest request);

    /**
     * {@code GET /api/price-lists-v2/{id}} (S3-T8, docs/decisions.md D-049):
     * read-only, so same RBAC level as {@code
     * EntityInstanceQueryService.listInstances} (D-030) — any of the 3
     * core roles, not {@code PRICING_ADMIN}-only like every mutating
     * method above.
     */
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'PRICING_ADMIN')")
    PriceListDetailDto getPriceList(Long priceListId);

    /**
     * {@code GET /api/price-lists-v2} — list every Price List header
     * (newest first), for a browse/list page. Same read-only RBAC as
     * {@link #getPriceList}; does not need per-row item/graph detail, so
     * returns the lighter {@link PriceListDto} rather than {@link
     * PriceListDetailDto}.
     */
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'PRICING_ADMIN')")
    List<PriceListDto> listPriceLists();
}
