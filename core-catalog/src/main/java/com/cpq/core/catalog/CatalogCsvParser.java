package com.cpq.core.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic, domain-agnostic CSV parser for catalog import (S2-T5, E1-04).
 * Pure Java — no Spring, no JPA, no I/O — so it's unit-testable without
 * Testcontainers. Knows nothing about "Part"/"Vehicle"/"Customer": it only
 * matches a CSV's header row against a caller-supplied list of
 * {@link ImportColumnSpec} by exact {@code code} (D-005), then splits each
 * data row into a {@code code -> raw string value} map.
 *
 * <p>Row numbering convention (recorded in {@code docs/decisions.md}): the
 * header line is never counted as a row; the first data line is row 1.
 * Header-level problems (unknown column, missing required column) are
 * reported as {@link CatalogRowError} with {@code rowNumber == 0}.
 */
public final class CatalogCsvParser {

    private CatalogCsvParser() {}

    public static ParsedCatalogCsv parse(List<ImportColumnSpec> columns, String csvContent) {
        List<String> lines = splitLines(csvContent);
        if (lines.isEmpty()) {
            return new ParsedCatalogCsv(
                    List.of(new CatalogRowError(0, null, "CSV file is empty — no header row found")),
                    List.of(),
                    List.of());
        }

        List<String> header = splitLine(lines.get(0)).stream().map(String::trim).toList();
        List<CatalogRowError> headerErrors = validateHeader(columns, header);
        if (!headerErrors.isEmpty()) {
            return new ParsedCatalogCsv(headerErrors, List.of(), List.of());
        }

        List<ParsedCatalogRow> rows = new ArrayList<>();
        List<CatalogRowError> rowErrors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            int rowNumber = i;
            List<String> fields = splitLine(lines.get(i));
            if (fields.size() != header.size()) {
                rowErrors.add(
                        new CatalogRowError(
                                rowNumber,
                                null,
                                "Row has "
                                        + fields.size()
                                        + " column(s), expected "
                                        + header.size()
                                        + " (matching the header)"));
                continue;
            }
            Map<String, String> valuesByCode = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                valuesByCode.put(header.get(c), fields.get(c));
            }
            rows.add(new ParsedCatalogRow(rowNumber, valuesByCode));
        }
        return new ParsedCatalogCsv(List.of(), rows, rowErrors);
    }

    private static List<CatalogRowError> validateHeader(
            List<ImportColumnSpec> columns, List<String> header) {
        List<CatalogRowError> errors = new ArrayList<>();
        Set<String> knownCodes =
                columns.stream().map(ImportColumnSpec::code).collect(Collectors.toSet());
        for (String headerColumn : header) {
            if (!knownCodes.contains(headerColumn)) {
                errors.add(
                        new CatalogRowError(
                                0,
                                headerColumn,
                                "Unknown column '"
                                        + headerColumn
                                        + "' — no attribute with this code is defined for this"
                                        + " entity"));
            }
        }
        Set<String> headerSet = new HashSet<>(header);
        for (ImportColumnSpec column : columns) {
            if (column.required() && !headerSet.contains(column.code())) {
                errors.add(
                        new CatalogRowError(
                                0, column.code(), "Missing required column '" + column.code() + "'"));
            }
        }
        return errors;
    }

    /** Splits on any line ending, dropping only trailing blank lines (e.g. EOF newline). */
    private static List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(List.of(content.split("\r\n|\r|\n", -1)));
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    /** Minimal RFC-4180-style line splitter: double-quoted fields may contain commas/escaped quotes. */
    private static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
