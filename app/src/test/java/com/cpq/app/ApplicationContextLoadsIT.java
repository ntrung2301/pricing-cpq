package com.cpq.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the Flyway + PostgreSQL + Spring Boot wiring works end-to-end
 * (S1-T3 Definition of Done) against a real Postgres, not a running local
 * instance: Testcontainers starts a disposable Postgres container, Flyway
 * runs {@code V1__init.sql} against it on context startup.
 */
@Testcontainers
@SpringBootTest
class ApplicationContextLoadsIT extends AbstractIntegrationTest {

    @Test
    void contextLoadsAndFlywayMigratesAgainstRealPostgres() {
        // Intentionally empty: @SpringBootTest already proves the context
        // starts, DataSource connects, and Flyway migrates successfully
        // (test fails fast at context startup otherwise).
    }
}
