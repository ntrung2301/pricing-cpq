package com.cpq.core.api.configurator;

/**
 * One failed {@code constraint_instance} found between two ids that are
 * both present in the caller's selection (S2-T8). {@code message} is the
 * human-readable explanation of which constraint failed and between which
 * two entity_instance ids — turning
 * {@code com.cpq.core.constraintengine.ConstraintEvaluationResult}'s plain
 * boolean into something a caller/UI can show is deliberately this
 * configurator API's job, not the engine's (see D-024's note in
 * {@code docs/decisions.md}).
 */
public record ConstraintViolationSummary(
        Long constraintInstanceId,
        String constraintDefCode,
        String constraintDefType,
        Long entityInstanceXId,
        Long entityInstanceYId,
        String message) {}
