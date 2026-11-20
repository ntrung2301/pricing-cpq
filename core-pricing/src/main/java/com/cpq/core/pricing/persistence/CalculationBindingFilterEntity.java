package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.CalculationBindingFilterOperator;
import com.cpq.core.pricing.dag.CalculationBindingValueSource;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA row for {@code calculation_binding_filter} — one AND-ed filter
 * condition narrowing an {@code EXTENSION}/{@code PARAMETER} {@link
 * CalculationBindingEntity}'s candidate rows (ADR-009 mục 3, S3-T3).
 * {@code operator} persists via {@link CalculationBindingFilterOperatorConverter}
 * (wire symbols, e.g. {@code ">="}), not {@code name()}, same pattern as
 * {@code RuleEntity.stage}/{@code PricingStageConverter}.
 */
@Entity
@Table(name = "calculation_binding_filter")
public class CalculationBindingFilterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "binding_id", nullable = false)
    private CalculationBindingEntity binding;

    @Column(name = "field_code", nullable = false, length = 100)
    private String fieldCode;

    @Convert(converter = CalculationBindingFilterOperatorConverter.class)
    @Column(nullable = false, length = 10)
    private CalculationBindingFilterOperator operator;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_source", nullable = false, length = 20)
    private CalculationBindingValueSource valueSource;

    @Column(columnDefinition = "TEXT")
    private String value;

    protected CalculationBindingFilterEntity() {
        // JPA
    }

    public CalculationBindingFilterEntity(
            String fieldCode,
            CalculationBindingFilterOperator operator,
            CalculationBindingValueSource valueSource,
            String value) {
        this.fieldCode = fieldCode;
        this.operator = operator;
        this.valueSource = valueSource;
        this.value = value;
    }

    void assignBinding(CalculationBindingEntity binding) {
        this.binding = binding;
    }

    public Long getId() {
        return id;
    }

    public CalculationBindingEntity getBinding() {
        return binding;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public CalculationBindingFilterOperator getOperator() {
        return operator;
    }

    public CalculationBindingValueSource getValueSource() {
        return valueSource;
    }

    public String getValue() {
        return value;
    }
}
