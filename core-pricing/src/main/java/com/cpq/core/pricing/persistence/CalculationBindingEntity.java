package com.cpq.core.pricing.persistence;

import com.cpq.core.pricing.dag.CalculationBindingSourceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JPA row for {@code calculation_binding} (ADR-009 mục 3, E8-03b, S3-T3):
 * how a {@code query} {@code DagNode} resolves one value, generically,
 * from {@code Product}/{@code Customer} (direct attribute read) or an
 * {@code Extension}/{@code Parameter} entity (filter -> sort ->
 * take-first). {@code attributeCode} is this binding's own lookup key — a
 * {@code query} node's {@code config} references a binding by this code
 * (see {@code com.cpq.app.pricing.dag.QueryNodeExecutor}), not by its
 * database id, so the DAG graph stays readable/portable across
 * environments. Plain {@code jakarta.persistence} class — same ADR-005
 * split as {@code RuleSetEntity}/{@code PriceListEntity}: {@code app} owns
 * the repository.
 */
@Entity
@Table(name = "calculation_binding")
public class CalculationBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attribute_code", nullable = false, unique = true, length = 100)
    private String attributeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private CalculationBindingSourceType sourceType;

    @Column(name = "source_entity_code", length = 100)
    private String sourceEntityCode;

    @Column(name = "source_attribute_code", nullable = false, length = 100)
    private String sourceAttributeCode;

    @Column(name = "take_first_after_sort", nullable = false)
    private boolean takeFirstAfterSort;

    @OneToMany(mappedBy = "binding", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CalculationBindingFilterEntity> filters = new ArrayList<>();

    @OneToMany(mappedBy = "binding", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CalculationBindingSortEntity> sorts = new ArrayList<>();

    protected CalculationBindingEntity() {
        // JPA
    }

    public CalculationBindingEntity(
            String attributeCode,
            CalculationBindingSourceType sourceType,
            String sourceEntityCode,
            String sourceAttributeCode,
            boolean takeFirstAfterSort) {
        this.attributeCode = attributeCode;
        this.sourceType = sourceType;
        this.sourceEntityCode = sourceEntityCode;
        this.sourceAttributeCode = sourceAttributeCode;
        this.takeFirstAfterSort = takeFirstAfterSort;
    }

    public void addFilter(CalculationBindingFilterEntity filter) {
        filter.assignBinding(this);
        filters.add(filter);
    }

    public void addSort(CalculationBindingSortEntity sort) {
        sort.assignBinding(this);
        sorts.add(sort);
    }

    public Long getId() {
        return id;
    }

    public String getAttributeCode() {
        return attributeCode;
    }

    public CalculationBindingSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceEntityCode() {
        return sourceEntityCode;
    }

    public String getSourceAttributeCode() {
        return sourceAttributeCode;
    }

    public boolean isTakeFirstAfterSort() {
        return takeFirstAfterSort;
    }

    public List<CalculationBindingFilterEntity> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    public List<CalculationBindingSortEntity> getSorts() {
        return Collections.unmodifiableList(sorts);
    }
}
