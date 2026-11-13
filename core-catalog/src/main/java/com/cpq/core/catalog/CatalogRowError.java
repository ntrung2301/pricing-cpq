package com.cpq.core.catalog;

/**
 * One row/column-specific import error (S2-T5, ADR-003's
 * {@code import_job_row_error}). {@code rowNumber == 0} is this project's
 * convention for a header-level error (unknown column, missing required
 * column) — see {@code docs/decisions.md} for the full row-numbering
 * decision. {@code columnName} is nullable: a purely structural row error
 * (e.g. wrong number of fields) isn't attributable to one column.
 */
public record CatalogRowError(int rowNumber, String columnName, String message) {}
