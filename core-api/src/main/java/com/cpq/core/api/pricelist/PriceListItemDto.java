package com.cpq.core.api.pricelist;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response projection of a {@code price_list_item_v2} row (S3-T6).
 * {@code resultPrice}/{@code waterfallSteps} are read-only (no request DTO
 * in this package ever carries them); {@code manualOverridePrice} is the
 * one field {@code PATCH /api/price-lists-v2/items/{itemId}/override}
 * (see {@link OverridePriceRequest}) can change.
 */
public record PriceListItemDto(
        Long id,
        Long priceListId,
        Long entityInstanceId,
        BigDecimal resultPrice,
        BigDecimal manualOverridePrice,
        List<TraceEntryDto> waterfallSteps,
        LocalDate validFrom,
        LocalDate validTo) {}
