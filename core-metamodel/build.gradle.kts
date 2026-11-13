// core-metamodel: EntityDefinition/AttributeDefinition/RelationshipDefinition/
// ConstraintDefinition (ADR-001). Standalone at skeleton stage (no
// inter-core dependency needed yet).
plugins {
    id("cpq.java-conventions")
}

dependencies {
    // Entities here are plain JPA-annotated classes only (jakarta.persistence
    // annotations, no Spring/Hibernate dependency) — the persistence runtime
    // (Hibernate via spring-boot-starter-data-jpa), repositories, and
    // services live in `app` (S1-T7), consistent with ADR-005: `app` is the
    // composition root that owns DB-facing wiring, core-* modules stay
    // framework-light. Version is managed by the Spring Boot BOM imported
    // via `cpq.java-conventions`.
    api("jakarta.persistence:jakarta.persistence-api")
}
