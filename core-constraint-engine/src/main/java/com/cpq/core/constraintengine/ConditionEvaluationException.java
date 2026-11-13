package com.cpq.core.constraintengine;

/**
 * Thrown when a condition JSON tree (ADR-002 grammar) cannot be
 * evaluated — malformed JSON, unknown operator, wrong arity, unknown
 * alias, or a type that cannot be compared. Deliberately a hard failure
 * rather than a silent pass/fail: a constraint/rule author's mistake must
 * surface clearly (CLAUDE.md mục 7 — deterministic, auditable), not be
 * swallowed into an ambiguous boolean result.
 */
public class ConditionEvaluationException extends RuntimeException {

    public ConditionEvaluationException(String message) {
        super(message);
    }

    public ConditionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
