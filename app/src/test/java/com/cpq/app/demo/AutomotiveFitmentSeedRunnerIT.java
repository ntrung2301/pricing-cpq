package com.cpq.app.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cpq.app.catalog.RelationshipInstanceQueryServiceImpl;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.core.api.catalog.SupersedingInstanceSummary;
import com.cpq.core.api.configurator.CompatibleInstanceSummary;
import com.cpq.core.api.configurator.ConfiguratorService;
import com.cpq.core.metamodel.EntityInstance;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T7 (sprint-2.md) Definition of Done: "Query 'part tương thích với xe X'
 * trả đúng danh sách; part bị supersede → API gợi ý part thay thế", exercised
 * against the *real* seeded automotive demo data (deliberately — unlike the
 * generic-mechanism tests of S2-T2/S2-T8, this task's own brief says using
 * real automotive data here does not violate the core/domain boundary, since
 * this is all seed data/config, not engine code).
 *
 * <p>{@link AutomotiveFitmentSeedRunner} is a genuine {@code ApplicationRunner}
 * bean, so Spring Boot's real context startup invokes it automatically (after
 * {@link DemoDataSeedRunner} and {@code PackLoaderService}, which it calls
 * itself for the same ordering reason D-026 already documented) — this test
 * never calls any seeding method directly except to simulate a restart.
 */
@Testcontainers
@SpringBootTest
class AutomotiveFitmentSeedRunnerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AutomotiveFitmentSeedRunner automotiveFitmentSeedRunner;
    @Autowired private EntityInstanceRepository entityInstanceRepository;
    @Autowired private ConfiguratorService configuratorService;
    @Autowired private RelationshipInstanceQueryServiceImpl relationshipInstanceQueryService;

    // from (replacement SKU) -> to (superseded SKU) — same 5 pairs
    // D-026/D-029 named.
    private static final Map<String, String> SUPERSESSION_PAIRS =
            Map.of(
                    "BRK-PAD-NEW-001", "BRK-PAD-OLD-001",
                    "BRK-PAD-NEW-002", "BRK-PAD-OLD-002",
                    "BRK-ROTOR-NEW-001", "BRK-ROTOR-OLD-001",
                    "OIL-FLT-NEW-010", "OIL-FLT-OLD-010",
                    "ALT-NEW-500", "ALT-OLD-500");

    @BeforeEach
    void authenticateAsSales() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("test-sales-rep", null, "ROLE_SALES"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void seedsFiveVehicleInstancesAtRealAppStartup() {
        List<EntityInstance> vehicles =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("Vehicle");
        assertThat(vehicles).hasSize(5);
    }

    @Test
    void firstVehicleHasExpectedCompatibleProductsViaConfiguratorApi() {
        List<EntityInstance> vehicles =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("Vehicle");
        EntityInstance camry = vehicles.get(0);

        List<CompatibleInstanceSummary> compatible =
                configuratorService.listCompatibleInstances(camry.getId(), "Product");

        Set<String> skus = compatible.stream().map(CompatibleInstanceSummary::businessKey).collect(
                java.util.stream.Collectors.toSet());
        assertThat(skus)
                .containsExactlyInAnyOrder(
                        "BRK-PAD-NEW-001", "BRK-ROTOR-NEW-001", "OIL-FLT-NEW-010", "AIR-FLT-001");
    }

    @Test
    void everySupersededPartResolvesToItsReplacementViaRelationshipApi() {
        SUPERSESSION_PAIRS.forEach(
                (replacementSku, supersededSku) -> {
                    EntityInstance superseded =
                            entityInstanceRepository
                                    .findByEntityDefinition_CodeAndBusinessKey("Product", supersededSku)
                                    .orElseThrow();

                    List<SupersedingInstanceSummary> result =
                            relationshipInstanceQueryService.findSupersededBy(superseded.getId());

                    assertThat(result).hasSize(1);
                    assertThat(result.get(0).businessKey()).isEqualTo(replacementSku);
                });
    }

    @Test
    void runningSeedAgainSimulatingRestartIsIdempotentAndDoesNotCrash() throws Exception {
        int vehiclesBefore =
                entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("Vehicle").size();
        EntityInstance oldBrakePad =
                entityInstanceRepository
                        .findByEntityDefinition_CodeAndBusinessKey("Product", "BRK-PAD-OLD-001")
                        .orElseThrow();
        int supersessionsBefore =
                relationshipInstanceQueryService.findSupersededBy(oldBrakePad.getId()).size();

        assertThatCode(() -> automotiveFitmentSeedRunner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        assertThat(entityInstanceRepository.findByEntityDefinition_CodeOrderByIdAsc("Vehicle"))
                .hasSize(vehiclesBefore);
        assertThat(relationshipInstanceQueryService.findSupersededBy(oldBrakePad.getId()))
                .hasSize(supersessionsBefore);
    }
}
