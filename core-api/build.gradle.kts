// core-api: REST controllers + OpenAPI, aggregating the core-* modules
// into one API surface; must not contain business logic (ADR-005 §2).
//
// S1-T6 adds the first real inter-core dependency: core-api -> core-security,
// needed for @PreAuthorize on the (skeleton-only) PingService and for
// @RestController/@GetMapping (spring-boot-starter-web). This is an
// intentional, one-directional edge allowed by ADR-005 rule 1 ("each
// core-* module only depends on the core-* module(s) it actually needs") —
// core-api legitimately needs core-security's RBAC types (ADR-006).
// com.cpq.app.arch.ModuleLayeringArchTest was updated accordingly (strict
// "no core depends on another" -> "core slices free of cycles").
plugins {
    id("cpq.java-conventions")
}

dependencies {
    implementation(project(":core-security"))
    implementation("org.springframework.boot:spring-boot-starter-web")
}
