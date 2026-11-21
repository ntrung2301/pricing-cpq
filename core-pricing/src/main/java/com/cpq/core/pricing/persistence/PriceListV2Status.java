package com.cpq.core.pricing.persistence;

/**
 * Lifecycle status of a {@link PriceListV2Entity} (S3-T6, ADR-009 mục 5):
 * {@code DRAFT -> PENDING_APPROVAL -> APPROVED -> ACTIVE -> EXPIRED}, with
 * a {@code REJECTED} branch off {@code PENDING_APPROVAL}. S3-T6's API
 * surface (create/add-item/submit/approve/override) only drives
 * transitions up to {@code APPROVED} — {@code ACTIVE}/{@code EXPIRED}/
 * {@code REJECTED} exist in the schema/enum for completeness per ADR-009
 * mục 5 but have no endpoint reaching them yet (a later task, e.g. a
 * scheduled activation/expiry job or a reject action, per
 * docs/decisions.md D-046).
 *
 * <p>Named with a {@code V2} suffix (unlike the {@code DRAFT}-less old
 * engine's {@link PriceListEntity}, which has no status at all) purely to
 * avoid colliding with that class's own name in this same package — see
 * D-046 for the full naming-collision rationale.
 */
public enum PriceListV2Status {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    ACTIVE,
    EXPIRED,
    REJECTED
}
