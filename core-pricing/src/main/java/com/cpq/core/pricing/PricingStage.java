package com.cpq.core.pricing;

import java.util.Arrays;

/**
 * The fixed, generic pricing pipeline stage order mandated by ADR-004 —
 * {@code base -> segment-adjustment -> quantity-adjustment -> discount ->
 * override -> guardrail}. {@link RuleEngine} always walks {@link #values()}
 * in this declaration order, never by DB read order, so the pipeline order
 * is deterministic regardless of how rules were inserted. No domain pack
 * may add a new stage (ADR-004 "Hệ quả") — a pack only attaches its rules
 * to one of these six.
 */
public enum PricingStage {
    BASE("base"),
    SEGMENT_ADJUSTMENT("segment-adjustment"),
    QUANTITY_ADJUSTMENT("quantity-adjustment"),
    DISCOUNT("discount"),
    OVERRIDE("override"),
    GUARDRAIL("guardrail");

    private final String wireCode;

    PricingStage(String wireCode) {
        this.wireCode = wireCode;
    }

    /** The exact string stored in the {@code rule.stage} column (ADR-004 naming). */
    public String wireCode() {
        return wireCode;
    }

    public static PricingStage fromWireCode(String wireCode) {
        return Arrays.stream(values())
                .filter(stage -> stage.wireCode.equals(wireCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown pricing stage: " + wireCode));
    }
}
