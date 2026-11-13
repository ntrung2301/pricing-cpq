import org.springframework.boot.gradle.tasks.bundling.BootJar

// Shared convention for every module in the modular monolith (ADR-005):
// Java 21 toolchain (auto-provisioned via the foojay resolver if no local
// JDK 21 is present, see docs/decisions.md), Spring Boot 3 dependency
// management (BOM), and common test wiring (JUnit 5).
//
// Applying the full `org.springframework.boot` plugin to every module
// (including core-* libraries) is intentional: it is the standard way to
// get Spring Boot's managed dependency versions (via the
// `io.spring.dependency-management` plugin it applies transitively)
// without each module having to declare its own BOM import. Library
// modules don't produce an executable jar, so `bootJar` is disabled and
// the plain `jar` task is re-enabled; `app` (the only module with a
// `main` method) flips this back in its own build.gradle.kts.
plugins {
    `java-library`
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter")

    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
