package com.cpq.core.pricing;

/**
 * Thrown when a <em>valid, already-accepted</em> rule fails while actually
 * being applied to a {@link PricingContext} — e.g. a Script rule timing
 * out, returning a shape the engine can't interpret, or throwing at
 * runtime. Distinct from {@link RuleDefinitionRejectedException} (which is
 * always a compile/validate-time rejection) so callers can tell "this rule
 * is fundamentally broken" apart from "this rule blew up on this input" —
 * ADR-002 Hệ quả (4): must never let a quote compute silently-wrong price.
 */
public class RuleExecutionException extends RuntimeException {

    public RuleExecutionException(String message) {
        super(message);
    }

    public RuleExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
