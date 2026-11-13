package com.cpq.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The pipeline order is fixed by ADR-004: {@code base -> segment-adjustment
 * -> quantity-adjustment -> discount -> override -> guardrail}. {@link
 * RuleEngine} relies on {@link PricingStage#values()} returning exactly
 * this order (declaration order), so this is pinned by a test rather than
 * left as an implicit assumption.
 */
class PricingStageTest {

    @Test
    void pipelineOrderMatchesAdr004() {
        assertThat(PricingStage.values())
                .containsExactly(
                        PricingStage.BASE,
                        PricingStage.SEGMENT_ADJUSTMENT,
                        PricingStage.QUANTITY_ADJUSTMENT,
                        PricingStage.DISCOUNT,
                        PricingStage.OVERRIDE,
                        PricingStage.GUARDRAIL);
    }

    @Test
    void wireCodeRoundTripsToStage() {
        for (PricingStage stage : PricingStage.values()) {
            assertThat(PricingStage.fromWireCode(stage.wireCode())).isEqualTo(stage);
        }
    }

    @Test
    void wireCodesMatchAdr004NamingExactly() {
        assertThat(PricingStage.BASE.wireCode()).isEqualTo("base");
        assertThat(PricingStage.SEGMENT_ADJUSTMENT.wireCode()).isEqualTo("segment-adjustment");
        assertThat(PricingStage.QUANTITY_ADJUSTMENT.wireCode()).isEqualTo("quantity-adjustment");
        assertThat(PricingStage.DISCOUNT.wireCode()).isEqualTo("discount");
        assertThat(PricingStage.OVERRIDE.wireCode()).isEqualTo("override");
        assertThat(PricingStage.GUARDRAIL.wireCode()).isEqualTo("guardrail");
    }
}
