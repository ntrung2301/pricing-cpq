package com.cpq.core.api.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/catalog/{entityDefCode}/instances} (S2-T9-prereq, see
 * docs/decisions.md D-030): generic, paginated listing of every {@code
 * entity_instance} row belonging to one entity_def; real catalog data
 * (contrast with {@code com.cpq.core.api.entities.EntitiesController}'s
 * {@code GET /api/entities}, S1-T8, which lists entity_def definitions
 * / metadata). Added ahead of S2-T9 (vehicle/part selection UI) because the
 * UI needs a way to render a catalog table and let the user pick an
 * initial anchor instance; no existing API listed real {@code
 * entity_instance} rows by entity_def.
 *
 * <p>Thin by design, same controller/service split as {@code
 * CatalogRelationshipController} (S2-T7) / {@code ConfiguratorController}
 * (S2-T8); the real logic (and the {@code @PreAuthorize} check) lives in
 * {@link EntityInstanceQueryService}.
 *
 * <p>Core has no idea whether {@code entityDefCode} names a "Product" or a
 * "Vehicle"; it only knows "an entity_def code, and page through its
 * entity_instance rows" (plus an optional case-insensitive substring
 * filter over businessKey/displayLabel).
 */
@RestController
public class CatalogInstanceController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;

    private final EntityInstanceQueryService entityInstanceQueryService;

    public CatalogInstanceController(EntityInstanceQueryService entityInstanceQueryService) {
        this.entityInstanceQueryService = entityInstanceQueryService;
    }

    @GetMapping("/api/catalog/{entityDefCode}/instances")
    public EntityInstancePageDto listInstances(
            @PathVariable String entityDefCode,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(required = false) String search) {
        return entityInstanceQueryService.listInstances(entityDefCode, page, size, search);
    }
}
