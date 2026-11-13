package com.cpq.core.api.catalog;

import java.util.List;

/**
 * REST response for {@code POST /api/catalog/{entityDefCode}/import}
 * (S2-T5, E1-04). {@code status} is {@code "COMPLETED"} (ran through every
 * row, possibly with some row errors) or {@code "FAILED"} (header/config
 * problem, no row was processed at all) — see {@code
 * com.cpq.core.catalog.ImportJobStatus} for the full decision.
 */
public record ImportResultDto(
        Long jobId,
        String entityDefCode,
        String status,
        int createdCount,
        List<ImportRowErrorDto> errors) {}
