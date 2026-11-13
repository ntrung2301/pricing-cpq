package com.cpq.core.api.catalog;

/**
 * REST projection of one {@code import_job_row_error} row (S2-T5). {@code
 * rowNumber == 0} means a header-level error (unknown column / missing
 * required column, D-005); {@code columnName} is nullable for structural
 * row errors not attributable to one column.
 */
public record ImportRowErrorDto(int rowNumber, String columnName, String message) {}
