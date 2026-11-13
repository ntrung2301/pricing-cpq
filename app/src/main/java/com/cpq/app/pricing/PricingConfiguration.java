package com.cpq.app.pricing;

import com.cpq.core.pricing.RuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link RuleEngine} (core-pricing) has no Spring annotations of its own
 * — it is a plain class, usable with or without a DI container — and
 * {@code com.cpq.core.pricing} is outside {@code @SpringBootApplication}'s
 * default component-scan base package ({@code com.cpq.app}). This
 * {@code app}-side configuration is the composition root wiring it into
 * the context as a singleton bean, same role as
 * {@code PricingCpqApplication}'s {@code @EntityScan("com.cpq.core")} for
 * JPA entities.
 */
@Configuration
public class PricingConfiguration {

    @Bean
    public RuleEngine ruleEngine() {
        return new RuleEngine();
    }
}
