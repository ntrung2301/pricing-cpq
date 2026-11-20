package com.cpq.core.pricing.dag;

/**
 * Sort direction of one {@code calculation_binding_sort} row (ADR-009 mục
 * 3, S3-T3). Persisted via {@code @Enumerated(EnumType.STRING)} directly —
 * {@code name()} matches the {@code direction} CHECK constraint verbatim.
 */
public enum CalculationBindingSortDirection {
    ASC,
    DESC
}
