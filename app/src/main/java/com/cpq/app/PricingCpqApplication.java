package com.cpq.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Composition-root Spring Boot application. Wires together the core-*
 * modules; must never contain domain-specific (automotive, ...) business
 * logic (CLAUDE.md "Ranh gioi Core vs Domain", D-013) — domain is config
 * loaded by {@code PackLoaderService} from
 * {@code src/main/resources/config/packs/<pack-id>/pack.yaml} (ADR-001),
 * added starting Sprint 1 task S1-T7+.
 *
 * <p>{@code @EntityScan("com.cpq.core")} is needed because JPA entities
 * (e.g. {@code com.cpq.core.metamodel.EntityDefinition}, S1-T7) live in
 * core-* modules, outside the {@code com.cpq.app} subtree that Spring
 * Boot's default component/entity scan covers — kept at the generic
 * {@code com.cpq.core} root (not just {@code core.metamodel}) so other
 * core-* modules can add entities later without touching this class.
 * Spring Data JPA repositories (e.g. {@code com.cpq.app.metamodel.
 * EntityDefinitionRepository}) stay under {@code com.cpq.app} and need no
 * such extra scan annotation.
 */
@SpringBootApplication
@EntityScan("com.cpq.core")
public class PricingCpqApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingCpqApplication.class, args);
    }
}
