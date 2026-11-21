package com.cpq.core.api.pricelist;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response projection of {@code GET /api/price-lists-v2/{id}} (S3-T8, see
 * docs/decisions.md D-049): the header fields already on {@link
 * PriceListDto}, plus the {@code calculationGraph} it was built from and
 * every {@code price_list_item_v2} row priced against it (each carrying
 * its own {@code waterfallSteps}/Calculation Trace). Added for the Measure
 * Canvas UI, which needs to read a Price List back after creating it (S3-T6
 * shipped create/addItem/submit/approve/override but no read-back
 * endpoint).
 */
public record PriceListDetailDto(
        Long id,
        String code,
        String name,
        String currency,
        String status,
        LocalDate validFrom,
        LocalDate validTo,
        String resultNodeId,
        int version,
        String createdBy,
        Instant submittedAt,
        String approvedBy,
        Instant approvedAt,
        List<DagNodeDto> calculationGraph,
        List<PriceListItemDto> items) {}
