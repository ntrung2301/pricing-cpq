package com.cpq.app.pricing.dag;

/**
 * Thrown by {@link QueryNodeExecutor} when an {@code EXTENSION}/{@code
 * PARAMETER} Calculation Binding's filter+sort pipeline still leaves >=2
 * candidate rows and {@code take_first_after_sort} is {@code false}
 * (ADR-009 mục 3, S3-T3): "**không có fallback ngầm tự chọn thay user**".
 *
 * <p>Thrown from {@code QueryNodeExecutor.execute(...)} itself — evaluated
 * on every call, not cached from a one-time config check — so a binding
 * that *used to* resolve unambiguously but whose source data changed since
 * (e.g. a second matching row was added) throws immediately the next time
 * it's calculated, rather than silently returning a wrong/stale answer.
 */
public class CalculationBindingAmbiguousException extends RuntimeException {

    public CalculationBindingAmbiguousException(String bindingAttributeCode, int matchCount) {
        super(
                "Calculation binding '"
                        + bindingAttributeCode
                        + "' matched "
                        + matchCount
                        + " rows after filter+sort, and take_first_after_sort is false — set it to"
                        + " true (and/or add a sort) or narrow the filters so exactly one row"
                        + " matches");
    }
}
