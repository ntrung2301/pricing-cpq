package com.cpq.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the Flyway + PostgreSQL + Spring Boot wiring works end-to-end
 * (S1-T3 Definition of Done) against a real Postgres, not a running local
 * instance: Testcontainers starts a disposable Postgres container, Flyway
 * runs {@code V1__init.sql} against it on context startup.
 */
@Testcontainers
@SpringBootTest
class ApplicationContextLoadsIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void contextLoadsAndFlywayMigratesAgainstRealPostgres() {
        // Intentionally empty: @SpringBootTest already proves the context
        // starts, DataSource connects, and Flyway migrates successfully
        // (test fails fast at context startup otherwise).
    }
}
