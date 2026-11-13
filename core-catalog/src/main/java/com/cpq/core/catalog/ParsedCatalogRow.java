package com.cpq.core.catalog;

import java.util.Map;

/**
 * One successfully-structurally-parsed CSV data row (S2-T5): a 1-based
 * {@code rowNumber} (first data line after the header is row 1 — the
 * header itself is never counted as a row) and its raw string values
 * keyed by {@link ImportColumnSpec#code()}. Values are still raw strings
 * here — converting them to a typed {@code AttributeValue} (and rejecting
 * values that don't match the attribute's {@code data_type}) is the
 * {@code app}-side orchestration's job, since {@code core-catalog} doesn't
 * depend on {@code core-metamodel}.
 */
public record ParsedCatalogRow(int rowNumber, Map<String, String> valuesByColumnCode) {}
