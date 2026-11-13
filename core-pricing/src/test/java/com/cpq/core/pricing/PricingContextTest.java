package com.cpq.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link PricingContext} is the generic input bag {@link RuleEngine} reads
 * from — intentionally NOT coupled to core-metamodel's EntityInstance/
 * AttributeValue (ADR-002: "RuleContext ... core định nghĩa, không biết nội
 * dung domain"). {@code attributes} holds plain Java values (BigDecimal /
 * String / Boolean / java.time.LocalDate) keyed by attribute name — the
 * caller (app layer) is responsible for projecting entity_instance
 * attribute values into this generic shape.
 */
class PricingContextTest {

    @Test
    void attributesMapIsImmutableCopyNotLiveView() {
        Map<String, Object> source = new java.util.HashMap<>();
        source.put("tier", "GOLD");
        PricingContext context = new PricingContext(new BigDecimal("100.00"), new BigDecimal("1"), source);

        source.put("tier", "SILVER");

        assertThat(context.attributes()).containsEntry("tier", "GOLD");
        assertThatThrownBy(() -> context.attributes().put("tier", "BRONZE"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullAttributesDefaultsToEmptyMap() {
        PricingContext context = new PricingContext(new BigDecimal("50.00"), BigDecimal.ONE, null);

        assertThat(context.attributes()).isEmpty();
    }

    @Test
    void rejectsNullBasePriceOrQuantity() {
        assertThatThrownBy(() -> new PricingContext(null, BigDecimal.ONE, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PricingContext(BigDecimal.TEN, null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
