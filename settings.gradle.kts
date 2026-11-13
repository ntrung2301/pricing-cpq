pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions JDK toolchains (e.g. Java 21) when not installed
    // locally, as long as the build has network access. See
    // docs/decisions.md for the Java 21 toolchain decision (S1-T3).
    // Note: version 0.8.0/0.10.0 clashed with Gradle 9.8.2's own bundled
    // toolchain classes (NoSuchFieldError: IBM_SEMERU) — 1.0.0 is the
    // version actually compatible with this Gradle version.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "pricing-cpq"

include(
    "core-metamodel",
    "core-constraint-engine",
    "core-pricing",
    "core-catalog",
    "core-quote",
    "core-security",
    "core-api",
    "app",
)
