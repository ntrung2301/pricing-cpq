package com.cpq.core.catalog;

/**
 * Terminal status of an {@link ImportJob} (S2-T5). Decision recorded in
 * {@code docs/decisions.md}:
 *
 * <ul>
 *   <li>{@link #FAILED} — a config/header-level problem (unknown column,
 *       missing required column, or an unknown {@code entity_def} code)
 *       prevented the job from processing *any* row at all.</li>
 *   <li>{@link #COMPLETED} — the job ran through every data row; it may
 *       still have per-row errors recorded in
 *       {@code import_job_row_error} (a row error never aborts the whole
 *       job, per CLAUDE.md/sprint-2.md's "không crash cả file vì 1 dòng
 *       lỗi").</li>
 * </ul>
 *
 * <p>There is deliberately no {@code IN_PROGRESS}/{@code RUNNING} status:
 * the import runs synchronously within one HTTP request (S2-T5's scope),
 * so the {@code import_job} row is only ever written once, already in its
 * terminal state.
 */
public enum ImportJobStatus {
    COMPLETED,
    FAILED
}
