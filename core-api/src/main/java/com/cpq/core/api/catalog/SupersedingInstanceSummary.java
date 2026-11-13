package com.cpq.core.api.catalog;

/**
 * One {@code entity_instance} found to supersede (replace) a given
 * {@code entity_instance}, via a {@code "supersedes"}-typed
 * {@code relationship_instance} (S2-T7, ADR-003 "Supersession"). Generic:
 * core only knows "this instance is the 'from' side of a
 * relationship_instance whose relationship_def code is {@code
 * "supersedes"} and whose 'to' side is the instance you asked about" —
 * never "this part replaces that part".
 */
public record SupersedingInstanceSummary(Long entityInstanceId, String businessKey, String displayLabel) {}
