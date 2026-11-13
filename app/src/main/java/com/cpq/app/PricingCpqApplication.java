package com.cpq.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition-root Spring Boot application. Wires together the core-*
 * modules; must never contain domain-specific (automotive, ...) business
 * logic (CLAUDE.md "Ranh gioi Core vs Domain", D-013) — domain is config
 * loaded by {@code PackLoaderService} from
 * {@code src/main/resources/config/packs/<pack-id>/pack.yaml} (ADR-001),
 * added starting Sprint 1 task S1-T7+.
 */
@SpringBootApplication
public class PricingCpqApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingCpqApplication.class, args);
    }
}
