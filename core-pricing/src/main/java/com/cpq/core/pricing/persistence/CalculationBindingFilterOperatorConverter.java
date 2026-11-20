package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.CalculationBindingFilterOperator;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link CalculationBindingFilterOperator} to/from its {@link
 * CalculationBindingFilterOperator#wireCode()} (e.g. {@code GTE} &lt;-&gt;
 * {@code ">="}) — the exact symbol the {@code
 * calculation_binding_filter.operator} column's {@code CHECK} constraint
 * allows. Same pattern as {@code PricingStageConverter} (ADR-004).
 */
@Converter(autoApply = false)
public class CalculationBindingFilterOperatorConverter
        implements AttributeConverter<CalculationBindingFilterOperator, String> {

    @Override
    public String convertToDatabaseColumn(CalculationBindingFilterOperator attribute) {
        return attribute == null ? null : attribute.wireCode();
    }

    @Override
    public CalculationBindingFilterOperator convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CalculationBindingFilterOperator.fromWireCode(dbData);
    }
}
