// core-catalog: generic CSV/Excel import framework (E1-04), SPI connector
// (E6-03).
//
// S2-T5 adds the first real content: a pure, Spring-free CSV parser
// (CatalogCsvParser) plus the ImportJob/ImportJobRowError JPA entities
// (ADR-003's "Import" section) — same pattern as core-metamodel holding
// EntityDefinition/AttributeDefinition as plain JPA-annotated classes
// while the persistence runtime/repositories live in `app` (ADR-005).
plugins {
    id("cpq.java-conventions")
}

dependencies {
    // ImportJob/ImportJobRowError are plain JPA-annotated entities only
    // (no Spring/Hibernate dependency here) — same reasoning as
    // core-metamodel's AttributeDefinition/EntityDefinition.
    api("jakarta.persistence:jakarta.persistence-api")
}
