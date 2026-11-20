package com.cpq.core.pricing.dag;

/**
 * Comparison operator of one {@code calculation_binding_filter} row
 * (ADR-009 mục 3, S3-T3). Unlike {@link CalculationBindingSourceType}/
 * {@link CalculationBindingValueSource}/{@link CalculationBindingSortDirection},
 * {@code name()} (e.g. {@code EQ}) does not match the {@code operator}
 * CHECK constraint's symbols (e.g. {@code "="}) — {@link #wireCode()}/
 * {@link #fromWireCode(String)} do that mapping, same pattern as {@code
 * com.cpq.core.pricing.PricingStage}'s {@code wireCode()} (ADR-004).
 */
public enum CalculationBindingFilterOperator {
    EQ("="),
    NE("!="),
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    IN("in");

    private final String wireCode;

    CalculationBindingFilterOperator(String wireCode) {
        this.wireCode = wireCode;
    }

    public String wireCode() {
        return wireCode;
    }

    public static CalculationBindingFilterOperator fromWireCode(String wireCode) {
        for (CalculationBindingFilterOperator operator : values()) {
            if (operator.wireCode.equals(wireCode)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("Unknown calculation binding filter operator: '" + wireCode + "'");
    }
}
