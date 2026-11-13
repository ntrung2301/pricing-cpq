package com.cpq.core.constraintengine;

/**
 * The outcome of evaluating one constraint condition (E1-02, S2-T2).
 * Deliberately minimal for this task's scope — turning this into a
 * human-readable "why did it fail" explanation is part of the
 * configurator API's job (S2-T8), not the engine's.
 */
public record ConstraintEvaluationResult(boolean passed) {}
