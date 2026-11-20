package com.cpq.app.pack;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S3-T1 Definition of Done (sprint-3.md, E8-01): migration {@code
 * V9__entity_taxonomy_v2.sql} must not lose or corrupt any {@code
 * entity_def}/{@code entity_instance} data a deployment already seeded
 * under Sprint 2 (S2-T6/S2-T7's automotive demo: {@code Product}/{@code
 * Customer}/{@code Vehicle} entity defs + a seeded {@code Product}
 * instance) and must backfill {@code role} correctly on it.
 *
 * <p>Deliberately bypasses {@code @SpringBootTest} (and therefore Spring
 * Boot's own Flyway auto-migration, which always migrates straight to the
 * latest version): this test needs to construct the exact "pre-V9" state
 * itself, so it drives Flyway directly (migrate to {@code V8}, insert data
 * via raw JDBC mirroring Sprint 2's real shape, *then* migrate to latest)
 * against a disposable Testcontainers Postgres.
 */
@Testcontainers
class V9MigrationBackfillIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratingToLatestPreservesExistingDataAndBackfillsRole() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("8")
                .load()
                .migrate();

        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            // Mirrors Sprint 2's real shape (S2-T3/S2-T4/S2-T6): Product/
            // Customer/Vehicle already loaded by the automotive pack, plus
            // one seeded Product instance with a business key/display
            // label, exactly like real demo data looked like before V9
            // ever existed.
            statement.execute(
                    "INSERT INTO entity_def (code, label, version, pack_id, pack_version) VALUES "
                            + "('Product', 'Product', 1, 'automotive', 1), "
                            + "('Customer', 'Customer', 1, 'automotive', 1), "
                            + "('Vehicle', 'Vehicle', 1, 'automotive', 1)");
            statement.execute(
                    "INSERT INTO attribute_def (entity_def_id, code, label, data_type, required, pack_id, pack_version) "
                            + "SELECT id, 'sku', 'SKU', 'STRING', true, 'automotive', 1 "
                            + "FROM entity_def WHERE code = 'Product'");
            statement.execute(
                    "UPDATE entity_def SET key_attribute_def_id = "
                            + "(SELECT id FROM attribute_def WHERE code = 'sku') WHERE code = 'Product'");
            statement.execute(
                    "INSERT INTO entity_instance (entity_def_id, pack_id, pack_version, business_key, display_label) "
                            + "SELECT id, 'automotive', 1, 'BRK-PAD-001', 'Brake Pad 001' "
                            + "FROM entity_def WHERE code = 'Product'");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            Map<String, String> roleByCode = new HashMap<>();
            try (ResultSet roles = statement.executeQuery("SELECT code, role FROM entity_def ORDER BY code")) {
                while (roles.next()) {
                    roleByCode.put(roles.getString("code"), roles.getString("role"));
                }
            }
            assertThat(roleByCode).containsEntry("Product", "ANCHOR");
            assertThat(roleByCode).containsEntry("Customer", "ANCHOR");
            assertThat(roleByCode).containsEntry("Vehicle", "PARAMETER");

            try (ResultSet instance =
                    statement.executeQuery(
                            "SELECT business_key, display_label FROM entity_instance "
                                    + "WHERE business_key = 'BRK-PAD-001'")) {
                assertThat(instance.next()).isTrue();
                assertThat(instance.getString("display_label")).isEqualTo("Brake Pad 001");
                assertThat(instance.next()).isFalse(); // exactly 1 row — nothing duplicated
            }
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
