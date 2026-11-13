import org.springframework.boot.gradle.tasks.bundling.BootJar

// app: Spring Boot application module (composition root). The ONLY module
// allowed to depend on every core-* module (ADR-005) — enforced by this
// dependency block being the one place that lists all of them, and backed
// by com.cpq.app.arch.ModuleLayeringArchTest.
plugins {
    id("cpq.java-conventions")
}

// Unlike the core-* library modules, app is the bootable artifact.
tasks.named<BootJar>("bootJar") {
    enabled = true
}

tasks.named<Jar>("jar") {
    enabled = false
}

dependencies {
    implementation(project(":core-metamodel"))
    implementation(project(":core-constraint-engine"))
    implementation(project(":core-pricing"))
    implementation(project(":core-catalog"))
    implementation(project(":core-quote"))
    implementation(project(":core-security"))
    implementation(project(":core-api"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Persistence runtime (Hibernate) + Spring Data JPA repositories for the
    // core-metamodel entities (S1-T7) — app is the only module that owns
    // DB-facing wiring (ADR-005), core-metamodel only has the plain JPA
    // annotations (jakarta.persistence-api).
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
