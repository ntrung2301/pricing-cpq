plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Makes the `org.springframework.boot` and
    // `io.spring.dependency-management` plugin ids resolvable, unversioned,
    // from the precompiled `cpq.java-conventions` script plugin in
    // src/main/kotlin. Spring Boot 3's gradle plugin no longer
    // auto-applies dependency-management, so it is applied explicitly.
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.3.5")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.6")
}
