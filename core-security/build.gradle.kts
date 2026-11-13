// core-security: RBAC/OIDC wiring shared across the app (ADR-006).
//
// spring-boot-starter-oauth2-resource-server + spring-boot-starter-security
// are declared as `api` (not `implementation`): CpqRole/KeycloakRealmRoleConverter
// expose Spring Security types (Jwt, GrantedAuthority, Converter) in their
// own public signatures, and any module that depends on core-security
// (app, core-api) needs those same types on its own compile classpath to
// use them (e.g. app's SecurityConfig builds a JwtAuthenticationConverter;
// core-api's PingService uses @PreAuthorize). The actual Spring Security
// @Configuration/SecurityFilterChain wiring stays in app (S1-T6) —
// core-security only holds the generic, reusable pieces (ADR-006).
plugins {
    id("cpq.java-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
}
