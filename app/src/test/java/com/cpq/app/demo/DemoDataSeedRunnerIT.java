package com.cpq.app.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cpq.app.metamodel.EntityInstanceAttributeValueRepository;
import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.metamodel.EntityInstanceAttributeValue;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T6 (sprint-2.md) Definition of Done: real automotive demo data (Product
 * + Customer) is importable at real app startup (not test-only fixtures),
 * replacing {@code SampleWidget} (D-018 mục 3) for demos. This exercises the
 * *real* app context with Testcontainers Postgres — {@link DemoDataSeedRunner}
 * is a genuine {@link org.springframework.boot.ApplicationRunner} bean, so
 * Spring Boot's test context lifecycle already invokes it once automatically
 * during context startup (same mechanism {@code AutomotivePackIT} relies on
 * for {@code PackLoaderService}) before any test method runs — exactly the
 * "seed at real startup" scenario the task requires, not a mocked call.
 *
 * <p>The second test simulates an app restart (re-invoking {@code run(...)}
 * in the same JVM/DB, since actually restarting the Spring context per test
 * is prohibitively slow with Testcontainers) and asserts no duplicate rows
 * and no exception — the idempotency requirement.
 */
@Testcontainers
@SpringBootTest
class DemoDataSeedRunnerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private DemoDataSeedRunner demoDataSeedRunner;
    @Autowired private EntityInstanceRepository entityInstanceRepository;
    @Autowired private EntityInstanceAttributeValueRepository entityInstanceAttributeValueRepository;

    @Test
    @Transactional
    void seedsProductsAndCustomersAtRealAppStartup() {
        List<EntityInstance> products = entityInstanceRepository.findByEntityDefinition_Code("Product");
        assertThat(products).hasSizeGreaterThanOrEqualTo(20);
        assertThat(products.stream().map(EntityInstance::getBusinessKey).toList())
                .contains("BRK-PAD-OLD-001", "BRK-PAD-NEW-001", "ALT-OLD-500", "ALT-NEW-500");

        List<EntityInstance> customers = entityInstanceRepository.findByEntityDefinition_Code("Customer");
        assertThat(customers).hasSizeGreaterThanOrEqualTo(4);
        assertThat(tiersOf(customers)).containsExactlyInAnyOrder("OEM", "DISTRIBUTOR", "DEALER", "WORKSHOP");
    }

    @Test
    void runningSeedAgainSimulatingRestartIsIdempotentAndDoesNotCrash() throws Exception {
        int productsBefore = entityInstanceRepository.findByEntityDefinition_Code("Product").size();
        int customersBefore = entityInstanceRepository.findByEntityDefinition_Code("Customer").size();
        assertThat(productsBefore).isGreaterThan(0);
        assertThat(customersBefore).isGreaterThan(0);

        assertThatCode(() -> demoDataSeedRunner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        assertThat(entityInstanceRepository.findByEntityDefinition_Code("Product")).hasSize(productsBefore);
        assertThat(entityInstanceRepository.findByEntityDefinition_Code("Customer")).hasSize(customersBefore);
    }

    private Set<String> tiersOf(List<EntityInstance> customers) {
        return customers.stream()
                .flatMap(
                        customer ->
                                entityInstanceAttributeValueRepository.findByEntityInstance(customer).stream())
                .filter(value -> "tier".equals(value.getAttributeDefinition().getCode()))
                .map(EntityInstanceAttributeValue::getValueString)
                .collect(Collectors.toSet());
    }
}
