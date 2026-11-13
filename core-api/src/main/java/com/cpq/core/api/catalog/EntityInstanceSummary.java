package com.cpq.core.api.catalog;

import java.util.Map;

/**
 * One {@code entity_instance} row returned by {@code GET
 * /api/catalog/{entityDefCode}/instances} (S2-T9-prereq, see
 * docs/decisions.md D-030) — real catalog *data*, not entity_def
 * *metadata* (contrast with {@code com.cpq.core.api.entities.EntityDefinitionSummary},
 * S1-T8). Generic: core only knows {@code id}/{@code businessKey}/
 * {@code displayLabel} (D-019) plus every {@code
 * entity_instance_attribute_value} the instance has, keyed by {@code
 * attribute_def.code} with the raw typed Java value (String/BigDecimal/
 * LocalDate/Boolean/Long — same shapes {@code
 * com.cpq.app.constraint.ConstraintInstanceEvaluationService} already
 * extracts from {@code EntityInstanceAttributeValue} for the constraint
 * engine) — it never knows what the entity_def or attribute actually
 * represent (e.g. "Vehicle"/"make").
 */
public record EntityInstanceSummary(
        Long id, String businessKey, String displayLabel, Map<String, Object> attributes) {}
