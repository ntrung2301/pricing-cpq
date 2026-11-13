package com.cpq.core.pricing;

/**
 * The two rule authoring mechanisms (D-014/ADR-002), both running through
 * the same {@link RuleEngine} pipeline and producing the same
 * {@link WaterfallStep} shape:
 *
 * <ul>
 *   <li>{@link #DSL} — the visual rule builder's JSON output (E5-01),
 *       condition grammar borrowed from JsonLogic, finite generic action
 *       set defined by core (see {@code com.cpq.core.pricing.dsl}).
 *   <li>{@link #SCRIPT} — Groovy source written directly by a technical
 *       Pricing Admin/dev, run in a mandatory sandbox (see
 *       {@code com.cpq.core.pricing.script}).
 * </ul>
 */
public enum RuleType {
    DSL,
    SCRIPT
}
