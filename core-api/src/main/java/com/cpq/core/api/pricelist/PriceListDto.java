package com.cpq.core.api.pricelist;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response projection of a Price List header (S3-T6) — {@code status} is
 * the plain enum name ({@code DRAFT}/{@code PENDING_APPROVAL}/{@code
 * APPROVED}/{@code ACTIVE}/{@code EXPIRED}/{@code REJECTED}), same
 * string-projection convention as {@code ImportResultDto}'s {@code
 * status}.
 */
public record PriceListDto(
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
        Instant approvedAt) {}
