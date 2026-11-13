package com.cpq.core.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One row/column-specific error belonging to an {@link ImportJob} (S2-T5,
 * ADR-003). {@code rowNumber == 0} is this project's header-error
 * convention (see {@link CatalogRowError}, mirrored 1:1 by this entity);
 * {@code columnName} is nullable for structural row errors not
 * attributable to one column.
 *
 * <p>Unlike {@link ImportJob#getEntityDefId()}, {@code importJob} *is* a
 * real JPA {@code @ManyToOne} — both entities live in the same module
 * ({@code core-catalog}), so there's no cross-module coupling concern
 * here, same as {@code AttributeDefinition -> EntityDefinition} within
 * {@code core-metamodel}.
 */
@Entity
@Table(name = "import_job_row_error")
public class ImportJobRowError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_job_id", nullable = false)
    private ImportJob importJob;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "column_name", length = 100)
    private String columnName;

    @Column(nullable = false, length = 1000)
    private String message;

    protected ImportJobRowError() {
        // JPA
    }

    public ImportJobRowError(ImportJob importJob, int rowNumber, String columnName, String message) {
        this.importJob = importJob;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public ImportJob getImportJob() {
        return importJob;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getMessage() {
        return message;
    }
}
