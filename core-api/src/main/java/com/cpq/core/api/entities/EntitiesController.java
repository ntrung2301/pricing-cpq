package com.cpq.core.api.entities;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/entities} (S1-T8 walking skeleton): returns the entity
 * definitions created via S1-T7's metamodel (real Postgres data, not
 * mocked). Thin by design — the real logic (and {@code @PreAuthorize}
 * check) lives in {@link EntityDefinitionQueryService}, following the same
 * controller/service split as {@code com.cpq.core.api.ping.PingController}
 * (S1-T6).
 */
@RestController
public class EntitiesController {

    private final EntityDefinitionQueryService entityDefinitionQueryService;

    public EntitiesController(EntityDefinitionQueryService entityDefinitionQueryService) {
        this.entityDefinitionQueryService = entityDefinitionQueryService;
    }

    @GetMapping("/api/entities")
    public List<EntityDefinitionSummary> getEntities() {
        return entityDefinitionQueryService.listEntityDefinitions();
    }
}
