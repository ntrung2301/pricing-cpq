package com.cpq.core.pricing;

/**
 * Thrown when a {@link Rule#definition()} fails validation/compilation —
 * malformed JSON DSL, or (per D-014/CLAUDE.md mục 7) a Groovy script that
 * violates the sandbox policy (I/O, reflection, {@code System}/{@code
 * Thread}, unseeded time/random). This is always a compile/validate-time
 * failure, never a runtime one — the rule's body is never executed when
 * this is thrown (see {@code com.cpq.core.pricing.script.GroovyRuleCompiler}).
 */
public class RuleDefinitionRejectedException extends RuntimeException {

    public RuleDefinitionRejectedException(String message) {
        super(message);
    }

    public RuleDefinitionRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
