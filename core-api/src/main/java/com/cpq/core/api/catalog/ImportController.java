package com.cpq.core.api.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code POST /api/catalog/{entityDefCode}/import} (S2-T5, E1-04): generic
 * CSV catalog import. Thin by design — the real logic (header matching,
 * row-by-row import, {@code @PreAuthorize} check) lives in
 * {@link CatalogImportService}, following the same controller/service
 * split as {@code com.cpq.core.api.entities.EntitiesController} (S1-T8).
 *
 * <p>Core has no idea whether {@code entityDefCode} names a "Part", a
 * "Customer", or anything else — it only knows "an entity definition's
 * code, and a CSV file to match against its attribute codes" (D-005).
 *
 * <p>Response status: {@code 200} when the job ran ({@code COMPLETED},
 * possibly with some row errors — see {@link ImportResultDto}); {@code
 * 400} when the job couldn't start at all ({@code FAILED} — header/config
 * problem, decision recorded in {@code docs/decisions.md}); an unknown
 * {@code entityDefCode} surfaces as {@code 404} via the exception thrown
 * by the service implementation (no job can be recorded without a valid
 * {@code entity_def_id} foreign key).
 */
@RestController
public class ImportController {

    private static final String FAILED_STATUS = "FAILED";

    private final CatalogImportService catalogImportService;

    public ImportController(CatalogImportService catalogImportService) {
        this.catalogImportService = catalogImportService;
    }

    @PostMapping(
            value = "/api/catalog/{entityDefCode}/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDto> importCsv(
            @PathVariable String entityDefCode, @RequestParam("file") MultipartFile file)
            throws IOException {
        String csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        ImportResultDto result =
                catalogImportService.importCsv(entityDefCode, file.getOriginalFilename(), csvContent);
        if (FAILED_STATUS.equals(result.status())) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
