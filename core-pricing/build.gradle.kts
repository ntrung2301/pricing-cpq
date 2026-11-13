// core-pricing: RuleEngine (JSON DSL + Groovy script, ADR-002/D-014), price
// waterfall (ADR-004), SPI pricing function (E6-01). Standalone at
// skeleton stage (no dependency on other core-* modules — the
// RuleEngine's PricingContext is a plain generic value bag, not coupled to
// core-metamodel's EntityInstance/AttributeValue, see docs/decisions.md
// S2-T10 entry).
plugins {
    id("cpq.java-conventions")
}

dependencies {
    // Plain JPA-annotated persistence classes for rule_set/rule/price_list/
    // price_list_item (ADR-003 §"Price list"), same pattern as
    // core-metamodel's EntityDefinition et al (S1-T7/ADR-005): core-pricing
    // only holds jakarta.persistence annotations, no Hibernate/Spring Data
    // dependency — app owns the repository/service wiring.
    api("jakarta.persistence:jakarta.persistence-api")

    // JSON DSL rule parsing (rule_type=DSL, ADR-002) — jackson-databind is
    // not transitively pulled in by the bare `spring-boot-starter`
    // (cpq.java-conventions), unlike spring-boot-starter-web, so it is
    // declared explicitly here; version is still managed by the Spring Boot
    // BOM imported by the convention plugin, no separate pin needed.
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Groovy script rule execution (rule_type=SCRIPT, ADR-002/D-014),
    // sandboxed via SecureASTCustomizer + a custom AST policy check (see
    // com.cpq.core.pricing.script). Version managed by the Spring Boot BOM
    // (io.spring.dependency-management via cpq.java-conventions) — see
    // docs/decisions.md for the resolved version.
    implementation("org.apache.groovy:groovy")

    testImplementation("org.assertj:assertj-core")
}
