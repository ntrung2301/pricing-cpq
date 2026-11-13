-- S2-T5 (E1-04): generic catalog CSV import framework (ADR-003's "Import"
-- section). Core only knows "an import run against entity_def X, with a
-- file/status/timestamps, and a list of row/column errors" — nothing here
-- is automotive- or domain-specific (CLAUDE.md "Ranh giới Core vs Domain").
--
-- import_job.entity_def_id has a real FK to entity_def at the DB level,
-- even though core-catalog's ImportJob JPA entity stores it as a plain
-- Long column (not a @ManyToOne) to avoid a core-catalog -> core-metamodel
-- module dependency — see docs/decisions.md for this decision.

CREATE TABLE import_job (
    id BIGSERIAL PRIMARY KEY,
    entity_def_id BIGINT NOT NULL REFERENCES entity_def (id),
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX idx_import_job_entity_def_id ON import_job (entity_def_id);

CREATE TABLE import_job_row_error (
    id BIGSERIAL PRIMARY KEY,
    import_job_id BIGINT NOT NULL REFERENCES import_job (id),
    row_number INT NOT NULL,
    column_name VARCHAR(100),
    message VARCHAR(1000) NOT NULL
);

CREATE INDEX idx_import_job_row_error_job_id ON import_job_row_error (import_job_id);
