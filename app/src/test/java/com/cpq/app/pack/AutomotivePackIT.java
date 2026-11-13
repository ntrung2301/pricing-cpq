package com.cpq.app.pack;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.app.metamodel.RelationshipDefinitionRepository;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.RelationshipCardinality;
import com.cpq.core.metamodel.RelationshipDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T3 (Product/Vehicle) + S2-T4 (Customer) Definition of Done
 * (sprint-2.md): {@code PackLoaderService} loads the real {@code
 * config/packs/automotive/pack.yaml} shipped in {@code app}'s main
 * resources — not a test fixture — and the three entities land in the
 * metamodel with correct key/label attributes and the {@code supersedes}/
 * {@code contains} relationships. This exercises
 * {@link PackLoaderService#loadAllPacksFromClasspath()} the same way
 * {@code PackLoaderServiceIT} does for the synthetic fixture pack, except
 * here the classpath scan also picks up the real automotive pack because
 * it now exists under {@code app/src/main/resources/config/packs/
 * automotive/pack.yaml}.
 */
@Testcontainers
@SpringBootTest
class AutomotivePackIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private PackLoaderService packLoaderService;
    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private RelationshipDefinitionRepository relationshipDefinitionRepository;

    @Test
    @Transactional
    void loadsAutomotivePackWithProductVehicleCustomerEntities() throws Exception {
        packLoaderService.loadAllPacksFromClasspath();

        EntityDefinition product = entityDefinitionRepository.findByCodeWithAttributes("Product").orElseThrow();
        assertThat(product.getPackId()).isEqualTo("automotive");
        assertThat(product.getKeyAttributeDefinition().getCode()).isEqualTo("sku");
        assertThat(product.getLabelAttributeDefinition().getCode()).isEqualTo("name");
        assertThat(product.getAttributes()).hasSizeLessThanOrEqualTo(20);
        assertThat(codesOf(product))
                .contains("sku", "name", "oem_or_aftermarket", "core_charge", "moq", "packaging_unit");

        EntityDefinition vehicle = entityDefinitionRepository.findByCodeWithAttributes("Vehicle").orElseThrow();
        assertThat(vehicle.getPackId()).isEqualTo("automotive");
        assertThat(vehicle.getAttributes()).hasSizeLessThanOrEqualTo(20);
        assertThat(codesOf(vehicle)).contains("make", "model", "year", "engine");

        EntityDefinition customer = entityDefinitionRepository.findByCodeWithAttributes("Customer").orElseThrow();
        assertThat(customer.getPackId()).isEqualTo("automotive");
        assertThat(customer.getKeyAttributeDefinition().getCode()).isEqualTo("customer_id");
        assertThat(customer.getLabelAttributeDefinition().getCode()).isEqualTo("name");
        assertThat(customer.getAttributes()).hasSizeLessThanOrEqualTo(20);
        assertThat(codesOf(customer)).contains("customer_id", "name", "tier", "contract_code", "contact_email");

        RelationshipDefinition supersedes = relationshipDefinitionRepository.findByCode("supersedes").orElseThrow();
        assertThat(supersedes.getFromEntityDefinition().getCode()).isEqualTo("Product");
        assertThat(supersedes.getToEntityDefinition().getCode()).isEqualTo("Product");
        assertThat(supersedes.getCardinality()).isEqualTo(RelationshipCardinality.ONE_TO_MANY);

        RelationshipDefinition contains = relationshipDefinitionRepository.findByCode("contains").orElseThrow();
        assertThat(contains.getFromEntityDefinition().getCode()).isEqualTo("Product");
        assertThat(contains.getToEntityDefinition().getCode()).isEqualTo("Product");
    }

    private static List<String> codesOf(EntityDefinition entityDefinition) {
        return entityDefinition.getAttributes().stream().map(AttributeDefinition::getCode).toList();
    }
}
