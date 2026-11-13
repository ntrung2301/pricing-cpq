package com.cpq.core.api.configurator;

/**
 * One {@code entity_instance} found compatible with an anchor instance via
 * a passing {@code "compatibility"}-typed {@code constraint_instance}
 * (S2-T8/E2-01's "list compatible instances" addition — see
 * {@code docs/decisions.md} D-027). Generic: core only knows "this
 * instance, of the entity_def you asked for, has a passing compatibility
 * constraint with your anchor" — never "this part fits this vehicle".
 */
public record CompatibleInstanceSummary(Long entityInstanceId, String businessKey, String displayLabel) {}
