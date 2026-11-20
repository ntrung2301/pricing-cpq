package com.cpq.core.pricing.dag;

/**
 * Where a {@link com.cpq.core.pricing.persistence.CalculationBindingFilterEntity}'s
 * comparison value comes from (ADR-009 mục 3, S3-T3).
 *
 * <ul>
 *   <li>{@link #CONSTANT} — {@code value} is a literal, parsed according to
 *       the filtered field's {@code AttributeDataType}.
 *   <li>{@link #CONTEXT_ATTRIBUTE} — {@code value} names one of the owning
 *       {@code query} {@code DagNode}'s {@code inputs()} keys; the actual
 *       comparison value is whatever that input resolved to at execution
 *       time (see {@code QueryNodeExecutor}, app).
 *   <li>{@link #REQUEST_PARAM} — declared for schema completeness only.
 *       **Not implemented in S3-T3** (NEEDS-PO-REVIEW, docs/decisions.md):
 *       no request-level parameter plumbing exists into {@code
 *       DagEngine.execute()} yet. A filter using it throws a clear error
 *       rather than silently being skipped.
 * </ul>
 *
 * <p>Persisted via {@code @Enumerated(EnumType.STRING)} directly — {@code
 * name()} matches {@code calculation_binding_filter.value_source}'s CHECK
 * constraint verbatim.
 */
public enum CalculationBindingValueSource {
    CONSTANT,
    CONTEXT_ATTRIBUTE,
    REQUEST_PARAM
}
