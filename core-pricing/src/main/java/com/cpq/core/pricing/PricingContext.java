package com.cpq.core.pricing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Generic pricing input: a base price, a quantity, and a bag of attribute
 * values (e.g. projected from an entity_instance's attribute values, a
 * Customer's {@code tier}, or any other context the caller has resolved
 * ahead of time). {@link RuleEngine} and its DSL/Script executors only
 * know these three generic things — never "Customer" or "Product"
 * (ADR-002: core's {@code RuleContext} is domain-agnostic).
 *
 * <p>{@code attributes} values are plain Java types the DSL/Script
 * executors know how to compare/use: {@link BigDecimal}, {@link String},
 * {@link Boolean}, {@link java.time.LocalDate}.
 */
public record PricingContext(BigDecimal basePrice, BigDecimal quantity, Map<String, Object> attributes) {

    public PricingContext(BigDecimal basePrice, BigDecimal quantity, Map<String, Object> attributes) {
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
