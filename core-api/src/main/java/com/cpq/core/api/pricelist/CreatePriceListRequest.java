package com.cpq.core.api.pricelist;

import java.time.LocalDate;
import java.util.List;

/** Request body for {@code POST /api/price-lists-v2} (S3-T6). */
public record CreatePriceListRequest(
        String code,
        String name,
        String currency,
        LocalDate validFrom,
        LocalDate validTo,
        List<DagNodeDto> calculationGraph,
        String resultNodeId) {}
