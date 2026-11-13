package com.cpq.core.catalog;

import java.util.List;

/**
 * Result of {@link CatalogCsvParser#parse(List, String)} (S2-T5).
 *
 * <ul>
 *   <li>{@code headerErrors} — non-empty means the header itself didn't
 *       match the entity definition's columns (unknown column name, or a
 *       required column entirely missing, D-005). When non-empty, {@code
 *       rows} and {@code rowErrors} are always empty: no data row is
 *       processed at all if the header is wrong (the whole import is a
 *       config-level failure, not a per-row one).</li>
 *   <li>{@code rows} — structurally valid data rows, ready for semantic
 *       (type/required-value) validation by the caller.</li>
 *   <li>{@code rowErrors} — purely structural per-row problems found while
 *       parsing (currently: wrong number of fields). Semantic row errors
 *       (bad value type, duplicate business key, ...) are *not* produced
 *       here — the caller appends those to the same
 *       {@code import_job_row_error} list after processing {@code rows}.</li>
 * </ul>
 */
public record ParsedCatalogCsv(
        List<CatalogRowError> headerErrors,
        List<ParsedCatalogRow> rows,
        List<CatalogRowError> rowErrors) {

    public boolean isHeaderValid() {
        return headerErrors.isEmpty();
    }
}
