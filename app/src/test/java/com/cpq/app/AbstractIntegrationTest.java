package com.cpq.app;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for every {@code @SpringBootTest} integration test in {@code
 * app}. Perf/test-infra fix (NOT a sprint-3 task — PO-requested directly,
 * see D-041/D-044 in {@code docs/decisions.md}): before this class existed,
 * each of the ~25 {@code @SpringBootTest} IT classes declared its own
 * {@code @Container PostgreSQLContainer} + its own {@code
 * @DynamicPropertySource} method. Even though every one of those blocks was
 * byte-for-byte identical, Spring's test context caching keys on the
 * declaring {@link java.lang.reflect.Method} of the {@code
 * @DynamicPropertySource} callback — a different method per class, even
 * with identical bodies, is a different cache key — so Spring Test could
 * never recognize two classes as "the same context configuration" and
 * started a brand new {@code ApplicationContext} (and therefore re-ran
 * every {@code ApplicationRunner} bean in {@code app}: pack loading, CSV
 * demo seed, fitment/pricing/taxonomy seed) AND a brand new disposable
 * Postgres container, once per class.
 *
 * <p>Fix: a single {@code static} Postgres container, started once, and a
 * single {@code @DynamicPropertySource} method, both declared here and
 * inherited. Subclasses that are otherwise identical (same {@code
 * @SpringBootTest}, same {@code @AutoConfigureMockMvc} or lack thereof, no
 * other context-affecting annotation) now share exactly one {@code
 * ApplicationContext} and one container for the whole {@code :app:test}
 * run, cutting ~25 full Spring Boot startups (with all seeding) down to a
 * small number of groups by actual, real configuration difference.
 *
 * <p>Deliberately does NOT use {@code @Testcontainers}/{@code @Container}
 * (the JUnit Jupiter Testcontainers extension) — that extension manages
 * start/stop around each test class itself, which is exactly the
 * per-class lifecycle we are eliminating. Instead the container is started
 * once in a {@code static} initializer and intentionally never stopped
 * here; the Ryuk reaper container (started automatically by Testcontainers
 * the first time any container starts in this JVM) tears it down when the
 * test JVM exits.
 *
 * <p>Not used by {@code V9MigrationBackfillIT}: that test deliberately
 * drives Flyway itself to an intermediate version ({@code target("8")})
 * before asserting a backfill migration, so it needs a container with
 * control over its own schema version/lifecycle, not one already migrated
 * to head and fully seeded by every {@code ApplicationRunner}.
 */
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
