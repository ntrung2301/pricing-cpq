package com.cpq.core.api.configurator;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/configurator/validate-selection} and
 * {@code GET /api/configurator/compatible} (S2-T8, E2-01): generic
 * configurator engine endpoints. Thin by design — the real logic (and
 * {@code @PreAuthorize} check) lives in {@link ConfiguratorService},
 * following the same controller/service split as
 * {@code com.cpq.core.api.entities.EntitiesController} (S1-T8) and
 * {@code com.cpq.core.api.catalog.ImportController} (S2-T5).
 *
 * <p>Core has no idea whether a selection is "vehicle + part" or anything
 * else — it only knows "a list of entity_instance ids" and
 * "constraint_instance rows whose endpoints are among them" (D-024).
 *
 * <p>Response status for {@code validate-selection}: {@code 200} when
 * {@code valid == true}; {@code 400} when {@code valid == false} (at
 * least one relevant constraint_instance failed) — same
 * result-carries-the-status pattern as {@code ImportController}'s
 * {@code FAILED} branch, deliberately not a thrown exception, so a
 * constraint violation is never surfaced as an unhandled 500 (sprint-2.md
 * S2-T8 Definition of Done).
 */
@RestController
public class ConfiguratorController {

    private final ConfiguratorService configuratorService;

    public ConfiguratorController(ConfiguratorService configuratorService) {
        this.configuratorService = configuratorService;
    }

    @PostMapping("/api/configurator/validate-selection")
    public ResponseEntity<ValidateSelectionResponse> validateSelection(
            @RequestBody ValidateSelectionRequest request) {
        ValidateSelectionResponse response = configuratorService.validateSelection(request);
        if (!response.valid()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/configurator/compatible")
    public List<CompatibleInstanceSummary> compatibleInstances(
            @RequestParam Long anchorInstanceId, @RequestParam String targetEntityDefCode) {
        return configuratorService.listCompatibleInstances(anchorInstanceId, targetEntityDefCode);
    }
}
