package com.cpq.core.pricing;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The result of successfully applying one matching rule: the signed
 * amount to add to the running total, and the label to show on the
 * resulting {@link WaterfallStep}. A {@link RuleExecutor} returns
 * {@code Optional.empty()} instead of this when the rule's condition does
 * not match the current {@link PricingContext} (no step is produced).
 */
public record RuleApplication(BigDecimal delta, String label) {

    public RuleApplication {
        Objects.requireNonNull(delta, "delta");
        Objects.requireNonNull(label, "label");
    }
}
