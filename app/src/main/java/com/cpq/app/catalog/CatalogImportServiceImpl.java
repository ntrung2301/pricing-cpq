package com.cpq.app.catalog;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.core.api.catalog.CatalogImportService;
import com.cpq.core.api.catalog.ImportResultDto;
import com.cpq.core.api.catalog.ImportRowErrorDto;
import com.cpq.core.catalog.CatalogCsvParser;
import com.cpq.core.catalog.CatalogRowError;
import com.cpq.core.catalog.ImportColumnSpec;
import com.cpq.core.catalog.ImportJob;
import com.cpq.core.catalog.ImportJobRowError;
import com.cpq.core.catalog.ImportJobStatus;
import com.cpq.core.catalog.ParsedCatalogCsv;
import com.cpq.core.catalog.ParsedCatalogRow;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.AttributeValue;
import com.cpq.core.metamodel.EntityDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code app}-side implementation of {@code core-api}'s
 * {@link CatalogImportService} port (S2-T5, E1-04). Orchestrates:
 * {@code core-metamodel}'s {@code EntityDefinition}/{@code
 * AttributeDefinition} (to build the expected column list and convert raw
 * CSV strings to typed {@code AttributeValue}s), {@code core-catalog}'s
 * {@link CatalogCsvParser} (pure header/row parsing), {@link
 * CatalogRowWriter} (one DB transaction per row), and {@link
 * ImportJobRepository}/{@link ImportJobRowErrorRepository} (bookkeeping).
 *
 * <p>Deliberately *not* {@code @Transactional}: each row must commit (or
 * roll back) independently — see {@link CatalogRowWriter}'s Javadoc.
 */
@Service
public class CatalogImportServiceImpl implements CatalogImportService {

    /**
     * Sentinel pack id assigned to instances created by an import run
     * against an {@code entity_def} that has no pack of origin (e.g. one
     * created directly through the service layer/tests, not via {@code
     * PackLoaderService}) — {@code entity_instance.pack_id} is {@code NOT
     * NULL}, so *some* value is required. Decision recorded in
     * docs/decisions.md.
     */
    static final String MANUAL_IMPORT_PACK_ID = "manual-import";

    static final int MANUAL_IMPORT_PACK_VERSION = 1;

    private static final String FAILED = ImportJobStatus.FAILED.name();

    private final EntityDefinitionRepository entityDefinitionRepository;
    private final ImportJobRepository importJobRepository;
    private final ImportJobRowErrorRepository importJobRowErrorRepository;
    private final CatalogRowWriter catalogRowWriter;

    public CatalogImportServiceImpl(
            EntityDefinitionRepository entityDefinitionRepository,
            ImportJobRepository importJobRepository,
            ImportJobRowErrorRepository importJobRowErrorRepository,
            CatalogRowWriter catalogRowWriter) {
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.importJobRepository = importJobRepository;
        this.importJobRowErrorRepository = importJobRowErrorRepository;
        this.catalogRowWriter = catalogRowWriter;
    }

    @Override
    public ImportResultDto importCsv(String entityDefCode, String fileName, String csvContent) {
        EntityDefinition entityDefinition =
                entityDefinitionRepository
                        .findByCodeWithAttributes(entityDefCode)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unknown entity_def code: " + entityDefCode));

        Instant startedAt = Instant.now();
        List<AttributeDefinition> attributes = entityDefinition.getAttributes();
        List<ImportColumnSpec> columns =
                attributes.stream().map(a -> new ImportColumnSpec(a.getCode(), a.isRequired())).toList();

        ParsedCatalogCsv parsed = CatalogCsvParser.parse(columns, csvContent);

        List<CatalogRowError> errors = new ArrayList<>();
        int createdCount = 0;
        ImportJobStatus status;

        if (!parsed.isHeaderValid()) {
            status = ImportJobStatus.FAILED;
            errors.addAll(parsed.headerErrors());
        } else {
            status = ImportJobStatus.COMPLETED;
            errors.addAll(parsed.rowErrors());
            String packId =
                    entityDefinition.getPackId() != null ? entityDefinition.getPackId() : MANUAL_IMPORT_PACK_ID;
            int packVersion =
                    entityDefinition.getPackId() != null
                            ? entityDefinition.getPackVersion()
                            : MANUAL_IMPORT_PACK_VERSION;

            for (ParsedCatalogRow row : parsed.rows()) {
                RowConversionResult conversion = convertRow(attributes, row);
                if (!conversion.errors().isEmpty()) {
                    errors.addAll(conversion.errors());
                    continue;
                }
                try {
                    catalogRowWriter.writeRow(
                            entityDefinition, attributes, conversion.valuesByCode(), packId, packVersion);
                    createdCount++;
                } catch (DataAccessException e) {
                    errors.add(
                            new CatalogRowError(
                                    row.rowNumber(),
                                    keyColumnCode(entityDefinition),
                                    "Could not import row (likely a duplicate business key): "
                                            + e.getMostSpecificCause().getMessage()));
                } catch (RuntimeException e) {
                    errors.add(
                            new CatalogRowError(
                                    row.rowNumber(), null, "Failed to import row: " + e.getMessage()));
                }
            }
        }

        ImportJob job =
                importJobRepository.save(
                        new ImportJob(entityDefinition.getId(), fileName, status, startedAt, Instant.now()));
        importJobRowErrorRepository.saveAll(
                errors.stream()
                        .map(e -> new ImportJobRowError(job, e.rowNumber(), e.columnName(), e.message()))
                        .toList());

        return new ImportResultDto(
                job.getId(),
                entityDefinition.getCode(),
                status.name(),
                createdCount,
                errors.stream()
                        .map(e -> new ImportRowErrorDto(e.rowNumber(), e.columnName(), e.message()))
                        .toList());
    }

    private static String keyColumnCode(EntityDefinition entityDefinition) {
        return entityDefinition.getKeyAttributeDefinition() != null
                ? entityDefinition.getKeyAttributeDefinition().getCode()
                : null;
    }

    private static RowConversionResult convertRow(List<AttributeDefinition> attributes, ParsedCatalogRow row) {
        Map<String, AttributeValue> valuesByCode = new LinkedHashMap<>();
        List<CatalogRowError> rowErrors = new ArrayList<>();
        for (AttributeDefinition attribute : attributes) {
            String raw = row.valuesByColumnCode().get(attribute.getCode());
            if (raw == null || raw.isBlank()) {
                if (attribute.isRequired()) {
                    rowErrors.add(
                            new CatalogRowError(
                                    row.rowNumber(),
                                    attribute.getCode(),
                                    "Missing required value for column '" + attribute.getCode() + "'"));
                }
                continue;
            }
            try {
                valuesByCode.put(attribute.getCode(), convertValue(attribute, raw));
            } catch (IllegalArgumentException e) {
                rowErrors.add(new CatalogRowError(row.rowNumber(), attribute.getCode(), e.getMessage()));
            }
        }
        return new RowConversionResult(valuesByCode, rowErrors);
    }

    private static AttributeValue convertValue(AttributeDefinition attribute, String raw) {
        String trimmed = raw.trim();
        try {
            return switch (attribute.getDataType()) {
                case STRING, ENUM, JSON -> AttributeValue.ofString(raw);
                case NUMBER, DECIMAL -> AttributeValue.ofNumber(new BigDecimal(trimmed));
                case DATE -> AttributeValue.ofDate(LocalDate.parse(trimmed));
                case BOOLEAN -> AttributeValue.ofBoolean(parseBoolean(attribute, trimmed));
                case REFERENCE -> AttributeValue.ofReferenceInstanceId(Long.parseLong(trimmed));
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid value '"
                            + raw
                            + "' for column '"
                            + attribute.getCode()
                            + "' (expected "
                            + attribute.getDataType()
                            + ")");
        }
    }

    private static boolean parseBoolean(AttributeDefinition attribute, String trimmed) {
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Invalid value '"
                        + trimmed
                        + "' for column '"
                        + attribute.getCode()
                        + "' (expected "
                        + AttributeDataType.BOOLEAN
                        + ": 'true' or 'false')");
    }

    private record RowConversionResult(
            Map<String, AttributeValue> valuesByCode, List<CatalogRowError> errors) {}
}
