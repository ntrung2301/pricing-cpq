package com.cpq.core.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A single catalog CSV import run (S2-T5, ADR-003's "Import" section,
 * E1-04). Core only knows "an import of entity_def X, with this
 * file/status/timestamps" — it has no idea whether the entity definition
 * being imported into is a "Part", a "Customer", or anything else.
 *
 * <p>{@code entityDefId} is a plain foreign-key column (not a JPA
 * {@code @ManyToOne}) — deliberately, to avoid {@code core-catalog}
 * depending on {@code core-metamodel}'s {@code EntityDefinition} entity;
 * the DB-level foreign key constraint still lives in the Flyway migration.
 * This mirrors how cross-module references are handled elsewhere in this
 * codebase (same-module relations use real JPA associations, e.g.
 * {@code AttributeDefinition -> EntityDefinition}; cross-module references
 * use a plain id column).
 */
@Entity
@Table(name = "import_job")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_def_id", nullable = false)
    private Long entityDefId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportJobStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportJob() {
        // JPA
    }

    public ImportJob(
            Long entityDefId,
            String fileName,
            ImportJobStatus status,
            Instant startedAt,
            Instant completedAt) {
        this.entityDefId = entityDefId;
        this.fileName = fileName;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getEntityDefId() {
        return entityDefId;
    }

    public String getFileName() {
        return fileName;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
