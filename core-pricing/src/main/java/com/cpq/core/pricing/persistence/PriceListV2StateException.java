package com.cpq.core.pricing.persistence;

/**
 * Thrown by {@link PriceListV2Entity}'s state-transition guard methods
 * ({@code submit()}/{@code approve(...)}/{@code requireMutable()}) when
 * the caller's requested transition does not match the price list's
 * current {@link PriceListV2Status} — e.g. submitting a price list that
 * isn't {@code DRAFT}, or adding/recomputing an item on one that is
 * already {@code APPROVED} (ADR-009 mục 5: "Sau APPROVED: snapshot vĩnh
 * viễn, không tự tính lại").
 *
 * <p>Plain {@code RuntimeException} — no {@code @ResponseStatus}/Spring
 * MVC annotation, since {@code core-pricing} must stay framework-free
 * (ADR-005). {@code com.cpq.app.pricing.pricelist.PriceListServiceImpl}
 * (app) catches this and rethrows it as a {@code
 * ResponseStatusException(HttpStatus.CONFLICT, ...)} for the HTTP layer.
 */
public class PriceListV2StateException extends RuntimeException {

    public PriceListV2StateException(String message) {
        super(message);
    }
}
