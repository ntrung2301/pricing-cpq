package com.cpq.core.pricing.dag;

/**
 * Where a Calculation Binding (ADR-009 mục 3, S3-T3) reads its value from —
 * mirrors the entity taxonomy v2 roles (S3-T1's {@code EntityRole}), plus a
 * split of {@code ANCHOR} into {@code PRODUCT}/{@code CUSTOMER} since a
 * binding must know *which* of the two fixed anchors it reads, not just
 * "an anchor".
 *
 * <ul>
 *   <li>{@link #PRODUCT}/{@link #CUSTOMER} — read {@code sourceAttributeCode}
 *       directly off the anchor instance currently being calculated, no
 *       filter/sort/take-first.
 *   <li>{@link #EXTENSION} — candidate rows auto-match the anchor instance
 *       currently being calculated ({@code anchor_instance_id}), then
 *       filter -> sort -> take-first.
 *   <li>{@link #PARAMETER} — candidate rows are *not* auto-matched to any
 *       anchor (independent entity) — only the declared filters apply.
 * </ul>
 *
 * <p>Persisted via {@code @Enumerated(EnumType.STRING)} directly —
 * {@code name()} matches the {@code calculation_binding.source_type} CHECK
 * constraint's allowed values verbatim, unlike {@code PricingStage} which
 * needed a wire-code converter.
 */
public enum CalculationBindingSourceType {
    PRODUCT,
    CUSTOMER,
    EXTENSION,
    PARAMETER
}
