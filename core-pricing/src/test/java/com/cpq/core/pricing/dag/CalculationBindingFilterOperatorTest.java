package com.cpq.core.pricing.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * S3-T3: {@link CalculationBindingFilterOperator#wireCode()}/{@link
 * CalculationBindingFilterOperator#fromWireCode(String)} must round-trip
 * exactly the symbols the {@code calculation_binding_filter.operator}
 * column's CHECK constraint allows — same reasoning as {@code
 * PricingStageConverter}'s wire-code mapping (ADR-004).
 */
class CalculationBindingFilterOperatorTest {

    @Test
    void everyOperatorRoundTripsThroughItsWireCode() {
        for (CalculationBindingFilterOperator operator : CalculationBindingFilterOperator.values()) {
            assertThat(CalculationBindingFilterOperator.fromWireCode(operator.wireCode())).isEqualTo(operator);
        }
    }

    @Test
    void wireCodesMatchTheDatabaseCheckConstraintSymbolsExactly() {
        assertThat(CalculationBindingFilterOperator.EQ.wireCode()).isEqualTo("=");
        assertThat(CalculationBindingFilterOperator.NE.wireCode()).isEqualTo("!=");
        assertThat(CalculationBindingFilterOperator.LT.wireCode()).isEqualTo("<");
        assertThat(CalculationBindingFilterOperator.LTE.wireCode()).isEqualTo("<=");
        assertThat(CalculationBindingFilterOperator.GT.wireCode()).isEqualTo(">");
        assertThat(CalculationBindingFilterOperator.GTE.wireCode()).isEqualTo(">=");
        assertThat(CalculationBindingFilterOperator.IN.wireCode()).isEqualTo("in");
    }

    @Test
    void fromWireCodeRejectsAnUnknownSymbolClearly() {
        assertThatThrownBy(() -> CalculationBindingFilterOperator.fromWireCode("<>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<>");
    }
}
