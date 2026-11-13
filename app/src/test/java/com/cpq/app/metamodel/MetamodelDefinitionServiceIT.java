package com.cpq.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S1-T7 Definition of Done: "migration chạy pass trên Testcontainers
 * Postgres, test tạo 1 entity definition generic qua API/service". Uses a
 * made-up "TestWidget" entity (CLAUDE.md genericity principle / E1-01
 * acceptance pattern) — deliberately not automotive — to prove the
 * metamodel definition tables + entities + service are domain-agnostic.
 *
 * <p>Follows the same Testcontainers pattern as
 * {@code com.cpq.app.ApplicationContextLoadsIT} (S1-T3): a real, disposable
 * Postgres container, with Flyway migrating {@code V1__init.sql} and
 * {@code V2__metamodel_definitions.sql} against it on context startup.
 */
@Testcontainers
@SpringBootTest
class MetamodelDefinitionServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MetamodelDefinitionService metamodelDefinitionService;

    @Autowired
    private EntityDefinitionRepository entityDefinitionRepository;

    @Test
    void createsEntityDefinitionWithAttributesAndRoundTripsThemFromPostgres() {
        AttributeDefinition skuAttribute =
                new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        AttributeDefinition weightAttribute =
                new AttributeDefinition(
                        "weight_kg",
                        "Weight (kg)",
                        AttributeDataType.DECIMAL,
                        false,
                        "EXT:legacy-weight-field");

        EntityDefinition saved =
                metamodelDefinitionService.defineEntity(
                        "TEST_WIDGET", "Test Widget", 1, List.of(skuAttribute, weightAttribute));

        EntityDefinition reloaded =
                entityDefinitionRepository
                        .findByCodeWithAttributes("TEST_WIDGET")
                        .orElseThrow();

        assertThat(reloaded.getId()).isEqualTo(saved.getId());
        assertThat(reloaded.getCode()).isEqualTo("TEST_WIDGET");
        assertThat(reloaded.getLabel()).isEqualTo("Test Widget");
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.getAttributes()).hasSize(2);

        AttributeDefinition reloadedSku = findByCode(reloaded, "sku");
        assertThat(reloadedSku.getLabel()).isEqualTo("SKU");
        assertThat(reloadedSku.getDataType()).isEqualTo(AttributeDataType.STRING);
        assertThat(reloadedSku.isRequired()).isTrue();
        assertThat(reloadedSku.getExternalCodeHint()).isNull();

        AttributeDefinition reloadedWeight = findByCode(reloaded, "weight_kg");
        assertThat(reloadedWeight.getLabel()).isEqualTo("Weight (kg)");
        assertThat(reloadedWeight.getDataType()).isEqualTo(AttributeDataType.DECIMAL);
        assertThat(reloadedWeight.isRequired()).isFalse();
        assertThat(reloadedWeight.getExternalCodeHint()).isEqualTo("EXT:legacy-weight-field");
    }

    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
