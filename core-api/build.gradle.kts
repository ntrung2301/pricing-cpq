// core-api: REST controllers + OpenAPI, aggregating the core-* modules
// into one API surface; must not contain business logic (ADR-005 §2).
// Standalone at skeleton stage (no inter-core dependency wired up yet —
// that happens as real endpoints are added in later sprints).
plugins {
    id("cpq.java-conventions")
}
