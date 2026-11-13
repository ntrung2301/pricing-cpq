// core-constraint-engine: constraint type registry (compatibility, range,
// required-if, ...) + SPI for new constraint types (E6-02).
//
// S2-T2 adds ConstraintEngine, a generic evaluator for the JSON condition
// grammar shared with rule conditions (ADR-002: ==, !=, >, >=, <, <=, in,
// and, or, not, var). It depends only on Jackson's databind module to
// parse the condition JSON into a JsonNode tree — no dependency on
// core-metamodel or any JPA/DB type, so the engine stays a pure function
// of (condition JSON, generic attribute values) -> pass/fail, callable
// from app without the engine ever seeing an EntityInstance. Version
// managed by the Spring Boot BOM (cpq.java-conventions), same pattern as
// app's jackson-dataformat-yaml dependency (D-020).
plugins {
    id("cpq.java-conventions")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
