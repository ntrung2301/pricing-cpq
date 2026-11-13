package com.cpq.core.pricing;

import java.math.BigDecimal;

/**
 * One row of {@code quote_line_waterfall_step} (ADR-004): a single
 * generic pricing pipeline adjustment, whether it came from a DSL action
 * or a Script return value — the caller cannot tell which produced it
 * except via {@code ruleId} + looking up the {@link Rule}.
 *
 * @param stepOrder 1-based order the step was produced in — matches
 *     {@code (stage, priority, rule.id)} order, not insertion/DB order.
 * @param stage the {@link PricingStage} this step belongs to.
 * @param ruleId the {@link Rule#id()} that produced this step, or
 *     {@code null} for the synthetic {@code base}/final-rounding steps.
 * @param label human-readable explanation of the adjustment.
 * @param deltaAmount the signed amount this step added/removed, internal
 *     scale (ADR-004: scale 6) unless {@code rounded}.
 * @param resultingAmount the running total after this step is applied.
 * @param rounded whether this step's {@code resultingAmount} has been
 *     {@code setScale(2, HALF_UP)} (ADR-004) — true only for the final
 *     step (or any step explicitly marked so).
 */
public record WaterfallStep(
        int stepOrder,
        PricingStage stage,
        Long ruleId,
        String label,
        BigDecimal deltaAmount,
        BigDecimal resultingAmount,
        boolean rounded) {}
