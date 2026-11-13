package com.cpq.core.api.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/catalog/instances/{instanceId}/superseded-by} (S2-T7,
 * ADR-003 "Supersession"): generic lookup endpoint — "which entity_instance
 * rows supersede/replace this one". Thin by design, same controller/
 * service split as {@code com.cpq.core.api.configurator.ConfiguratorController}
 * (S2-T8): the real logic (and the {@code @PreAuthorize} check) lives in
 * {@link RelationshipInstanceQueryService}.
 *
 * <p>Core has no idea whether {@code instanceId} is a "part" or anything
 * else — it only knows "an entity_instance id" and "relationship_instance
 * rows whose relationship_def code is {@code 'supersedes'} and whose 'to'
 * side matches it".
 */
@RestController
public class CatalogRelationshipController {

    private final RelationshipInstanceQueryService relationshipInstanceQueryService;

    public CatalogRelationshipController(RelationshipInstanceQueryService relationshipInstanceQueryService) {
        this.relationshipInstanceQueryService = relationshipInstanceQueryService;
    }

    @GetMapping("/api/catalog/instances/{instanceId}/superseded-by")
    public List<SupersedingInstanceSummary> supersededBy(@PathVariable Long instanceId) {
        return relationshipInstanceQueryService.findSupersededBy(instanceId);
    }
}
