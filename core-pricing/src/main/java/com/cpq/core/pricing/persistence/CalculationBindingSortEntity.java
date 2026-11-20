package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.CalculationBindingSortDirection;
import jakarta.persistence.Column;
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
 * JPA row for {@code calculation_binding_sort} — one ordered sort field
 * applied to an {@code EXTENSION}/{@code PARAMETER} {@link
 * CalculationBindingEntity}'s candidate rows when >=2 remain after
 * filtering (ADR-009 mục 3, S3-T3). {@code sortOrder} orders multiple sort
 * fields deterministically (same convention as {@code
 * EntityDefinitionDimensionAttribute.position}, S3-T1).
 */
@Entity
@Table(name = "calculation_binding_sort")
public class CalculationBindingSortEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "binding_id", nullable = false)
    private CalculationBindingEntity binding;

    @Column(name = "field_code", nullable = false, length = 100)
    private String fieldCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private CalculationBindingSortDirection direction;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CalculationBindingSortEntity() {
        // JPA
    }

    public CalculationBindingSortEntity(String fieldCode, CalculationBindingSortDirection direction, int sortOrder) {
        this.fieldCode = fieldCode;
        this.direction = direction;
        this.sortOrder = sortOrder;
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

    public CalculationBindingSortDirection getDirection() {
        return direction;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
